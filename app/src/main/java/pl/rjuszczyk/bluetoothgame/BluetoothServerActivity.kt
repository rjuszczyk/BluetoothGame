package pl.rjuszczyk.bluetoothgame

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.PersistableBundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast


class BluetoothServerActivity : AppCompatActivity() {
    val IDLE = 0
    val WAITING_FOR_BLUETOOTH = 1
    val BLUETOOTH_STARTING = 6
    val WAITING_FOR_DEVICE_CONNECTION = 3
    val WAITING_FOR_ENABLING_DISCOVERABILITY = 4
    val CONNECTED = 5


    lateinit var connectTo:String

    lateinit var sendMessage: Button
    lateinit var connect: Button
    lateinit var deviceName: TextView
    lateinit var status: TextView
    lateinit var acceptConnectionThread: AcceptConnectionThread
    var bluetoothSocketThread: BluetoothSocketThread? = null

    var tryingToConnectToBondedDevice = false
    var skipBonded = false

    var state = IDLE

    fun waitForConnection() {
        skipBonded = false
        acceptConnectionThread.start(object : AcceptConnectionThread.Callback {
            override fun onConnected(bluetoothSocket: BluetoothSocket) {
                updateState(CONNECTED)

                sendMessage.visibility = View.VISIBLE
                bluetoothSocketThread = BluetoothSocketThread(bluetoothSocket, Handler())
                bluetoothSocketThread!!.start(object : BluetoothSocketThread.Callback {
                    override fun onMessage(message: ByteArray) {
                        Toast.makeText(this@BluetoothServerActivity, String(message), Toast.LENGTH_SHORT).show()
                    }

                    override fun onDisconnected() {
                        updateState(IDLE)
                        Toast.makeText(this@BluetoothServerActivity, "DISCONNECTED", Toast.LENGTH_SHORT).show()

                        bluetoothSocketThread = null
                        waitForConnection2()
                    }
                })
            }

            override fun onFailed(exception: Exception) {
                if(tryingToConnectToBondedDevice) {
                    skipBonded = true
                    waitForConnection2()
                    return
                }
                updateState(IDLE)
            }
        })
    }

    val discoverableStateReceiver = object : BroadcastReceiver(){
        override fun onReceive(p0: Context, p1: Intent) {
            updateScanMode()
        }
    }

    fun updateScanMode() {
        if(BluetoothAdapter.getDefaultAdapter().scanMode  == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            if(state == WAITING_FOR_ENABLING_DISCOVERABILITY) {
                waitForConnection2()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(discoverableStateReceiver, IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        updateScanMode()
        if (BluetoothAdapter.getDefaultAdapter().isEnabled) {
            if(state == WAITING_FOR_BLUETOOTH || state == BLUETOOTH_STARTING) {
                waitForConnection2()
            }
        }
    }

    override fun onStop() {
        super.onStop()

        unregisterReceiver(discoverableStateReceiver)
        unregisterReceiver(bluetoothStateReceiver)
//        if(state == WAITING_FOR_DEVICE_CONNECTION) {
            acceptConnectionThread.cancel()
//        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_server)


        connectTo = intent.getStringExtra("connectTo")

        status = findViewById(R.id.status)

        sendMessage = findViewById(R.id.sendMessage)
        deviceName = findViewById(R.id.deviceName)
        connect = findViewById(R.id.connect)
        deviceName.text = BluetoothAdapter.getDefaultAdapter().name

        acceptConnectionThread = AcceptConnectionThread(packageName, BluetoothAdapter.getDefaultAdapter(), Handler())

        sendMessage.setOnClickListener {
            bluetoothSocketThread?.send("TEST".toByteArray(),
                    object : BluetoothSocketThread.SendCallback {
                        override fun onSend(message: ByteArray) {
                            Toast.makeText(this@BluetoothServerActivity, "SEND =" + String(message), Toast.LENGTH_SHORT).show()
                        }

                        override fun onFailed() {
                            Toast.makeText(this@BluetoothServerActivity, "SENDING FAILED", Toast.LENGTH_SHORT).show()
                        }
                    }
            )
        }


        connect.setOnClickListener {
            waitForConnection2()
        }
        updateState(IDLE)

        val wasConnected =savedInstanceState?.getBoolean("wasConnected", false)?:false
        if(wasConnected) {
            waitForConnection2()
        }
    }

    val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context, p1: Intent) {
            val bluetoothState = p1.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
            if(bluetoothState == BluetoothAdapter.STATE_TURNING_ON) {
                if(state == WAITING_FOR_BLUETOOTH) {
                    updateState(BLUETOOTH_STARTING)
                }
            } else if(bluetoothState == BluetoothAdapter.STATE_ON) {
                if(state == BLUETOOTH_STARTING || state == WAITING_FOR_BLUETOOTH) {
                    waitForConnection2()
                }
            } else {
                if(state != IDLE) {
                    updateState(WAITING_FOR_BLUETOOTH)
                }
            }
        }
    }

    fun waitForConnection2() {
        if (BluetoothAdapter.getDefaultAdapter().isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            updateState(WAITING_FOR_BLUETOOTH)
        } else {
            for (bondedDevice in BluetoothAdapter.getDefaultAdapter().bondedDevices) {
                if(connectTo.equals(bondedDevice.name)) {
                    if(!skipBonded) {
                        tryingToConnectToBondedDevice = true
                        waitForConnection()
                        updateState(WAITING_FOR_DEVICE_CONNECTION)
                        return
                    }
                }
            }

            if(BluetoothAdapter.getDefaultAdapter().scanMode !=  BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                updateState(WAITING_FOR_ENABLING_DISCOVERABILITY)
                val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
                startActivityForResult(discoverableIntent, 999)
            } else {
                tryingToConnectToBondedDevice = false
                waitForConnection()
                updateState(WAITING_FOR_DEVICE_CONNECTION)
            }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        bluetoothSocketThread?.cancel()
        bluetoothSocketThread = null
    }

    private fun updateState(newState:Int) {
        state = newState

        if(state == IDLE) {
            status.text = "IDLE"
            connect.visibility = View.VISIBLE
            sendMessage.visibility = View.VISIBLE
            sendMessage.visibility = View.GONE
        }
        if(state == WAITING_FOR_BLUETOOTH) {
            status.text = "WAITING_FOR_BLUETOOTH"
            connect.visibility = View.GONE
            sendMessage.visibility = View.GONE
        }
        if(state == BLUETOOTH_STARTING) {
            status.text = "BLUETOOTH_STARTING"
            connect.visibility = View.GONE
            sendMessage.visibility = View.GONE
        }
        if(state == WAITING_FOR_DEVICE_CONNECTION) {
            status.text = "WAITING_FOR_DEVICE_CONNECTION"
            connect.visibility = View.GONE
            sendMessage.visibility = View.GONE
        }
        if(state == WAITING_FOR_ENABLING_DISCOVERABILITY) {
            status.text = "WAITING_FOR_ENABLING_DISCOVERABILITY"
            connect.visibility = View.GONE
            sendMessage.visibility = View.GONE
        }
        if(state == CONNECTED) {
            status.text = "CONNECTED"
            connect.visibility = View.GONE
            sendMessage.visibility = View.VISIBLE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("wasConnected", state == CONNECTED)
        super.onSaveInstanceState(outState)
    }
}