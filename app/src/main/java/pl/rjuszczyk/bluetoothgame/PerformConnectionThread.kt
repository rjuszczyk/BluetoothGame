package pl.rjuszczyk.bluetoothgame

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler

class PerformConnectionThread(
        private val callbackHandler: Handler
        ) {

    var bluetoothSocket: BluetoothSocket? = null


    fun start(bluetoothDevice: BluetoothDevice, callback: Callback) {
        if(this.bluetoothSocket!= null)
            throw IllegalStateException("It is already staretes")

        var thread = Thread(Runnable {
            try {
                val bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(CONNECTION_SERVICE_UUID)
                this.bluetoothSocket = bluetoothSocket
                bluetoothSocket.connect()
                this.bluetoothSocket = null
                callbackHandler.post { callback.onConnected(bluetoothSocket) }
            } catch (e: Exception) {
                bluetoothSocket = null
                callbackHandler.post { callback.onFailed(e) }
            }
        })
        thread.start()
    }

    fun cancel() {
        bluetoothSocket?.close()
        bluetoothSocket = null
    }

    interface Callback {
        fun onConnected(bluetoothSocket: BluetoothSocket)
        fun onFailed(exception: Exception)
    }
}
