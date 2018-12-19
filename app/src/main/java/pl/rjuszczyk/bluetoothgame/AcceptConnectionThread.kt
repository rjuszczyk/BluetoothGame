package pl.rjuszczyk.bluetoothgame

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import java.util.*

class AcceptConnectionThread(
        private val packageName: String,
        private val adapter: BluetoothAdapter,
        private val callbackHandler: Handler
        ) {

    var bluetoothServerSocket: BluetoothServerSocket? = null


    fun start(callback: Callback) {
        if(this.bluetoothServerSocket!= null)
            throw IllegalStateException("It is already starete")

        bluetoothServerSocket = adapter.listenUsingRfcommWithServiceRecord(
                packageName + "bluetooth_game_server",
                CONNECTION_SERVICE_UUID
        )
        var thread = Thread(Runnable {
            try {
                val bluetoothSocket = bluetoothServerSocket!!.accept(300000)
                bluetoothServerSocket!!.close()
                bluetoothServerSocket = null
                callbackHandler.post { callback.onConnected(bluetoothSocket) }
            } catch (e: Exception) {
                if(bluetoothServerSocket != null) {
                    bluetoothServerSocket = null
                    callbackHandler.post { callback.onFailed(e) }
                }
            }
        })
        thread.start()
    }

    fun cancel() {
        val cpy = bluetoothServerSocket
        bluetoothServerSocket = null
        cpy?.close()
    }

    interface Callback {
        fun onConnected(bluetoothSocket: BluetoothSocket)
        fun onFailed(exception: Exception)
    }
}
