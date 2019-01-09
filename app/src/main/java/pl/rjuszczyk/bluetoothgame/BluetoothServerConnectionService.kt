package pl.rjuszczyk.bluetoothgame

import android.app.Activity
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.support.v4.content.LocalBroadcastManager
import android.widget.Toast

class BluetoothServerConnectionService : Service() {


    lateinit var connectTo:String
    var activityId:Long = 0

    override fun onBind(p0: Intent?): IBinder {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCreate() {
        super.onCreate()

        acceptConnectionThread = AcceptConnectionThread(packageName, BluetoothAdapter.getDefaultAdapter(), Handler())

        updateState(IDLE)

        registerReceiver(discoverableStateReceiver, IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        //this will not happen as state is not restored in service
//        updateScanMode()
//        if (BluetoothAdapter.getDefaultAdapter().isEnabled) {
//            if(state == WAITING_FOR_BLUETOOTH || state == BLUETOOTH_STARTING) {
//                waitForConnection2()
//            }
//        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(discoverableStateReceiver)
        unregisterReceiver(bluetoothStateReceiver)
//        if(state == WAITING_FOR_DEVICE_CONNECTION) {
        acceptConnectionThread.cancel()

        bluetoothSocketThread?.cancel()
        bluetoothSocketThread = null
    }

    companion object {
        fun waitForConnectionIntent(acitivity: Activity) : Intent {
            var intent = Intent(acitivity, BluetoothServerConnectionService::class.java)
            intent.setAction("waitForConnection")
            return intent
        }

        fun sendMessageIntent(acitivity: Activity, message:String) : Intent {
            var intent = Intent(acitivity, BluetoothServerConnectionService::class.java)
            intent.setAction("sendMessage")
            intent.putExtra("message", message)
            return intent
        }

        fun initConnectionFlowIntent(acitivity: Activity, activityId:Long, connectTo:String) : Intent  {
            var intent = Intent(acitivity, BluetoothServerConnectionService::class.java)
            intent.setAction("initConnectionFlowIntent")
            intent.putExtra("connectTo", connectTo)
            intent.putExtra("activityId", activityId)
            return intent
        }
        fun stop(acitivity: Activity) : Intent  {
            var intent = Intent(acitivity, BluetoothServerConnectionService::class.java)
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
        if(intent.action.equals("waitForConnection")) {
            waitForConnection2()
        }

        if(intent.action.equals("sendMessage")) {
            val message = intent.getStringExtra("message")
            bluetoothSocketThread?.send(message.toByteArray(),
                    object : BluetoothSocketThread.SendCallback {
                        override fun onSend(message: ByteArray) {
                            Toast.makeText(this@BluetoothServerConnectionService, "SEND =" + String(message), Toast.LENGTH_SHORT).show()
                        }

                        override fun onFailed() {
                            Toast.makeText(this@BluetoothServerConnectionService, "SENDING FAILED", Toast.LENGTH_SHORT).show()
                        }
                    }
            )
        }

        return START_STICKY
    }

    val IDLE = 0
    val WAITING_FOR_BLUETOOTH = 1
    val BLUETOOTH_STARTING = 6
    val WAITING_FOR_DEVICE_CONNECTION = 3
    val WAITING_FOR_ENABLING_DISCOVERABILITY = 4
    val CONNECTED = 5


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
                tryingToConnectToBondedDevice = false
                bluetoothSocketThread = BluetoothSocketThread(bluetoothSocket, Handler())
                bluetoothSocketThread!!.start(object : BluetoothSocketThread.Callback {
                    override fun onMessage(message: ByteArray) {
                        Toast.makeText(this@BluetoothServerConnectionService, String(message), Toast.LENGTH_SHORT).show()
                    }

                    override fun onDisconnected() {
                        updateState(IDLE)
                        Toast.makeText(this@BluetoothServerConnectionService, "DISCONNECTED", Toast.LENGTH_SHORT).show()

                        bluetoothSocketThread = null
                        //waitForConnection2()
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
            askForEnablingBluetooth()
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
                askForDiscoverability()
            } else {
                tryingToConnectToBondedDevice = false
                waitForConnection()
                updateState(WAITING_FOR_DEVICE_CONNECTION)
            }
        }
    }

    private fun askForDiscoverability() {
        //show notification that we need to turn on discoverability

        val startBluetoothAsk = Intent("activityTurnOnDiscoverabilityPlz")
        LocalBroadcastManager.getInstance(this).sendBroadcast(startBluetoothAsk)


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
        val sharedPreferences = getSharedPreferences("broadcastHelper", Context.MODE_PRIVATE);
        sharedPreferences.edit().putInt(""+activityId+"_newState", newState).commit()
        state = newState

        val broadcastIntent = Intent("updateState")
        broadcastIntent.putExtra("newState", newState)
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
    }
}
