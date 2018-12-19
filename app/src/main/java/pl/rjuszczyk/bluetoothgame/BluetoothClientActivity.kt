package pl.rjuszczyk.bluetoothgame

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast


const val REQUEST_ENABLE_BT = 101

class BluetoothClientActivity : AppCompatActivity() {


    val IDLE = 0
    val WAITING_FOR_BLUETOOTH = 4
    val BLUETOOTH_STARTING = 5
    val SEARCHING = 1
    val CONNECTING = 2
    val CONNECTED = 3

    lateinit var startDiscovery: Button
    lateinit var sendMessage: Button
    lateinit var status: TextView
    lateinit var performConnectionThread: PerformConnectionThread
    var bluetoothSocketThread: BluetoothSocketThread? = null
    lateinit var connectTo:String

    var tryingToConnectToBondedDevice = false
    var skipBonded = false

    var state = IDLE


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectTo = intent.getStringExtra("connectTo")

        setContentView(R.layout.activity_client)
        status = findViewById(R.id.status)
        startDiscovery = findViewById(R.id.startDiscovery)
        sendMessage = findViewById(R.id.sendMessage)

        performConnectionThread = PerformConnectionThread(Handler())

        startDiscovery.setOnClickListener { discoverDevices() }
        sendMessage.setOnClickListener {
            bluetoothSocketThread?.send("message".toByteArray(), object : BluetoothSocketThread.SendCallback{
                override fun onSend(message: ByteArray) {
                    Toast.makeText(this@BluetoothClientActivity, "SEND =" + String(message), Toast.LENGTH_SHORT ).show()
                }

                override fun onFailed() {
                    Toast.makeText(this@BluetoothClientActivity, "SENDING FAILED", Toast.LENGTH_SHORT ).show()
                }
            })
        }

        updateState(IDLE)
        val wasConnected =savedInstanceState?.getBoolean("wasConnected", false)?:false
        if(wasConnected) {
            discoverDevices()
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
                    discoverDevices()
                }
            } else {
                if(state != IDLE) {
                    updateState(WAITING_FOR_BLUETOOTH)
                }
            }
        }
    }
    val discoveryBroadcastReceiver = object : BroadcastReceiver(){
        override fun onReceive(p0: Context, p1: Intent) {
            val bluetoothDevice = p1.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            Log.d("BluetoothDevice", bluetoothDevice.toString())

            if(state != SEARCHING) {
                return
            }

            if(connectTo.equals(bluetoothDevice.name)) {
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                updateState(CONNECTING)
                connectToDevice(bluetoothDevice)
            }
        }
    }

    private fun connectToDevice(bluetoothDevice: BluetoothDevice) {
        skipBonded = false
        performConnectionThread.start(bluetoothDevice, object : PerformConnectionThread.Callback {
            override fun onConnected(bluetoothSocket: BluetoothSocket) {
                updateState(CONNECTED)
                bluetoothSocketThread = BluetoothSocketThread(bluetoothSocket, Handler())
                bluetoothSocketThread!!.start(object : BluetoothSocketThread.Callback {
                    override fun onMessage(message: ByteArray) {
                        Toast.makeText(this@BluetoothClientActivity, String(message), Toast.LENGTH_SHORT).show()
                    }

                    override fun onDisconnected() {
                        updateState(IDLE)
                        bluetoothSocketThread = null
                        Toast.makeText(this@BluetoothClientActivity, "DISCONNECTED", Toast.LENGTH_SHORT).show()
                        discoverDevices()
                    }
                })
            }

            override fun onFailed(exception: Exception) {
                if(tryingToConnectToBondedDevice) {
                    skipBonded = true
                    discoverDevices()
                    return
                }
                updateState(IDLE)
            }
        })
    }

    val discoverySartedChangedBroadcastReceiver = object : BroadcastReceiver(){
        override fun onReceive(p0: Context, p1: Intent) {
            updateSearching(BluetoothAdapter.getDefaultAdapter().isDiscovering)
        }
    }
    val discoveryFinishedChangedBroadcastReceiver = object : BroadcastReceiver(){
        override fun onReceive(p0: Context, p1: Intent) {
            updateSearching(BluetoothAdapter.getDefaultAdapter().isDiscovering)
        }
    }

    private fun updateSearching(discovering: Boolean) {
        if(discovering) {
            if (state == IDLE) {
                updateState(SEARCHING)
            }
        } else {
            if (state == SEARCHING) {
                updateState(IDLE)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updateSearching(BluetoothAdapter.getDefaultAdapter().isDiscovering)

        registerReceiver(discoveryBroadcastReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(discoverySartedChangedBroadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED))
        registerReceiver(discoveryFinishedChangedBroadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        if (BluetoothAdapter.getDefaultAdapter().isEnabled) {
            if(state == WAITING_FOR_BLUETOOTH || state == BLUETOOTH_STARTING) {
                discoverDevices()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
//        performConnectionThread.cancel()
//        bluetoothSocketThread?.cancel()
//        bluetoothSocketThread = null
    }

    override fun onStop() {
        super.onStop()

        performConnectionThread.cancel()
        bluetoothSocketThread?.cancel()
        bluetoothSocketThread = null

        BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
        unregisterReceiver(discoveryBroadcastReceiver)
        unregisterReceiver(discoverySartedChangedBroadcastReceiver)
        unregisterReceiver(discoveryFinishedChangedBroadcastReceiver)
    }

    private fun discoverDevices() {

        if (BluetoothAdapter.getDefaultAdapter().isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            updateState(WAITING_FOR_BLUETOOTH)
        } else {
            for (bondedDevice in BluetoothAdapter.getDefaultAdapter().bondedDevices) {
                if(connectTo.equals(bondedDevice.name)) {
                    if (!skipBonded) {
                        tryingToConnectToBondedDevice = true
                        updateState(CONNECTING)
                        connectToDevice(bondedDevice)
                        return
                    }


                }
            }


            BluetoothAdapter.getDefaultAdapter().startDiscovery()
        }
    }

    private fun updateState(newState:Int) {
        state = newState


        if(state == IDLE) {
            status.text = "IDLE"
            startDiscovery.visibility = View.VISIBLE
            sendMessage.visibility = View.GONE
        }
        if(state == SEARCHING) {
            status.text = "SEARCHING"
            startDiscovery.visibility = View.GONE
            sendMessage.visibility = View.GONE
        }
        if(state == CONNECTING) {
            status.text = "CONNECTING"
            startDiscovery.visibility = View.GONE
            sendMessage.visibility = View.GONE
        }
        if(state == WAITING_FOR_BLUETOOTH) {
            status.text = "WAITING_FOR_BLUETOOTH"
            startDiscovery.visibility = View.GONE
            sendMessage.visibility = View.GONE

        }
        if(state == BLUETOOTH_STARTING) {
            status.text = "BLUETOOTH_STARTING"
            startDiscovery.visibility = View.GONE
            sendMessage.visibility = View.GONE

        }
        if(state == CONNECTED) {
            status.text = "CONNECTED"
            startDiscovery.visibility = View.GONE
            sendMessage.visibility = View.VISIBLE
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("wasConnected", state == CONNECTED)
    }
}