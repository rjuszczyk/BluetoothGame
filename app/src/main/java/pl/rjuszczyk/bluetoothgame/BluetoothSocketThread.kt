package pl.rjuszczyk.bluetoothgame

import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class BluetoothSocketThread(
        private val bluetoothSocket: BluetoothSocket,
        private val callbackHandler: Handler) {

    val TAG = "BluetoothSocketThread"


    val mmInStream: InputStream = bluetoothSocket.inputStream
    val mmOutStream: OutputStream = bluetoothSocket.outputStream

    fun start(callback:Callback) {
        val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        object : Thread() {

            override fun run() {
                var numBytes: Int // bytes returned from read()

                // Keep listening to the InputStream until an exception occurs.
                while (true) {
                    // Read from the InputStream.
                    numBytes = try {
                        mmInStream.read(mmBuffer)
                    } catch (e: IOException) {
                        Log.d(TAG, "Input stream was disconnected", e)
                        callbackHandler.post{
                            callback.onDisconnected()
                        }
                        break
                    }


                    var msg = mmBuffer.copyOfRange(0, numBytes)
                    callbackHandler.post{
                        callback.onMessage(msg)
                    }
                }
            }
        }.start()
    }

    fun cancel() {
        try {
            bluetoothSocket.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the connect socket", e)
        }
    }

    fun send(msg:ByteArray, sendCallback: SendCallback) {
        try {
            mmOutStream.write(msg)
            callbackHandler.post{
                sendCallback.onSend(msg)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error occurred when sending data", e)
            callbackHandler.post{
                sendCallback.onFailed()
            }
            return
        }
    }

    interface Callback {
        fun onMessage(message: ByteArray)
        fun onDisconnected()
    }

    interface SendCallback {
        fun onSend(msg: ByteArray)
        fun onFailed()
    }
}
