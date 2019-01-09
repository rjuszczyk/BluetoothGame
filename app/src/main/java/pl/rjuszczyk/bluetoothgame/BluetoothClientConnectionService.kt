package pl.rjuszczyk.bluetoothgame

import android.app.Activity
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.widget.Toast

class BluetoothClientConnectionService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    val IDLE = 0
    val WAITING_FOR_BLUETOOTH = 4
    val BLUETOOTH_STARTING = 5
    val SEARCHING = 1
    val CONNECTING = 2
    val CONNECTED = 3

    lateinit var performConnectionThread: PerformConnectionThread
    var bluetoothSocketThread: BluetoothSocketThread? = null
    lateinit var connectTo:String
    var activityId:Long = 0

    var tryingToConnectToBondedDevice = false
    var skipBonded = false

    var state = IDLE


    override fun onCreate() {
        super.onCreate()

        performConnectionThread = PerformConnectionThread(Handler())

        registerReceiver(discoveryBroadcastReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(discoverySartedChangedBroadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED))
        registerReceiver(discoveryFinishedChangedBroadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        if (BluetoothAdapter.getDefaultAdapter().isEnabled) {
            if(state == WAITING_FOR_BLUETOOTH || state == BLUETOOTH_STARTING) {
                discoverDevices()
            }
        }

        updateState(IDLE)
    }

    companion object {
        fun discoverDevicesIntent(acitivity: Activity) : Intent {
            var intent = Intent(acitivity, BluetoothClientConnectionService::class.java)
            intent.setAction("discoverDevices")
            return intent
        }

        fun sendMessageIntent(acitivity: Activity, message:String) : Intent {
            var intent = Intent(acitivity, BluetoothClientConnectionService::class.java)
            intent.setAction("sendMessage")
            intent.putExtra("message", message)
            return intent
        }

        fun initConnectionFlowIntent(acitivity: Activity, activityId:Long, connectTo:String) : Intent  {
            var intent = Intent(acitivity, BluetoothClientConnectionService::class.java)
            intent.setAction("initConnectionFlowIntent")
            intent.putExtra("connectTo", connectTo)
            intent.putExtra("activityId", activityId)
            return intent
        }

        fun stop(acitivity: Activity) : Intent  {
            var intent = Intent(acitivity, BluetoothClientConnectionService::class.java)
            intent.setAction("stop")
            return intent
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {


        if(intent.action.equals("initConnectionFlowIntent")) {
            connectTo = intent.getStringExtra("connectTo")
            activityId = intent.getLongExtra("activityId", -1)
        }
        if(intent.action.equals("stop")) {
            stopSelf()
        }
        if(intent.action.equals("discoverDevices")) {
            discoverDevices()
        }

        if(intent.action.equals("sendMessage")) {
            val message = intent.getStringExtra("message")
            bluetoothSocketThread?.send(message.toByteArray(),
                    object : BluetoothSocketThread.SendCallback {
                        override fun onSend(message: ByteArray) {
                            Toast.makeText(this@BluetoothClientConnectionService, "SEND =" + String(message), Toast.LENGTH_SHORT).show()
                        }

                        override fun onFailed() {
                            Toast.makeText(this@BluetoothClientConnectionService, "SENDING FAILED", Toast.LENGTH_SHORT).show()
                        }
                    }
            )
        }

        return START_STICKY
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
                tryingToConnectToBondedDevice = false
                bluetoothSocketThread = BluetoothSocketThread(bluetoothSocket, Handler())
                bluetoothSocketThread!!.start(object : BluetoothSocketThread.Callback {
                    override fun onMessage(message: ByteArray) {
                        Toast.makeText(this@BluetoothClientConnectionService, String(message), Toast.LENGTH_SHORT).show()
                    }

                    override fun onDisconnected() {
                        updateState(IDLE)
                        bluetoothSocketThread = null
                        Toast.makeText(this@BluetoothClientConnectionService, "DISCONNECTED", Toast.LENGTH_SHORT).show()
                        //discoverDevices()
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
        if (BluetoothAdapter.getDefaultAdapter().isEnabled == false) {
            askForEnablingBluetooth()
        }

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

    override fun onDestroy() {
        super.onDestroy()

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
            askForEnablingBluetooth()
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

    private fun askForEnablingBluetooth() {
        //show notification that we need to turn on bluetooth
        //send broadcast to activity to ask for bluetooth
        val startBluetoothAsk = Intent("activityTurnOnBluetoothPlz")
        LocalBroadcastManager.getInstance(this).sendBroadcast(startBluetoothAsk)
//        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
    }

    private fun updateState(newState:Int) {
        state = newState

        val sharedPreferences = getSharedPreferences("broadcastHelper", Context.MODE_PRIVATE);
        sharedPreferences.edit().putInt(""+activityId+"_newState", newState).commit()
        state = newState

        val broadcastIntent = Intent("updateState")
        broadcastIntent.putExtra("newState", newState)
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
    }
}
