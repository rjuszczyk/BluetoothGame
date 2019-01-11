package pl.rjuszczyk.bluetoothgame

import android.annotation.TargetApi
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.*
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.widget.Toast

class BluetoothServerConnectionService : Service() {

    val IDLE = 0
    val WAITING_FOR_BLUETOOTH = 1
    val BLUETOOTH_STARTING = 6
    val WAITING_FOR_DEVICE_CONNECTION = 3
    val WAITING_FOR_ENABLING_DISCOVERABILITY = 4
    val CONNECTED = 5
    val TURN_OFF = 7

    lateinit var acceptConnectionThread: AcceptConnectionThread
    lateinit var sharedPreferences : SharedPreferences
    var bluetoothSocketThread: BluetoothSocketThread? = null

    lateinit var connectTo:String
    var activityId:Long = 0

    var tryingToConnectToBondedDevice = false
    var skipBonded = false

    var state = IDLE

    val discoverableStateReceiver = object : BroadcastReceiver(){
        override fun onReceive(p0: Context, p1: Intent) {
            updateScanMode()
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
                    waitForBeingDiscovered()
                }
            } else {
                if(state != IDLE) {
                    updateState(WAITING_FOR_BLUETOOTH)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        sharedPreferences = getSharedPreferences("broadcastHelper", Context.MODE_PRIVATE);
        acceptConnectionThread = AcceptConnectionThread(packageName, BluetoothAdapter.getDefaultAdapter(), Handler())

        state = sharedPreferences.getInt("services_state", 0)

        Log.d("RadekServer", "onCreate services_state = " + nameThatState(state))

        var lastConnectTo = sharedPreferences.getString("services_connectTo", null)
        lastConnectTo?.let {
            connectTo = it
        }

        registerReceiver(discoverableStateReceiver, IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        //restoring state
        updateScanMode()
        if (BluetoothAdapter.getDefaultAdapter().isEnabled) {
            if(state == WAITING_FOR_BLUETOOTH || state == BLUETOOTH_STARTING) {
                waitForBeingDiscovered()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d("RadekServer", "onDestroy")
        acceptConnectionThread.cancel()
        bluetoothSocketThread?.cancel()
        bluetoothSocketThread = null

        unregisterReceiver(discoverableStateReceiver)
        unregisterReceiver(bluetoothStateReceiver)
    }

    companion object {
        fun waitForConnectionIntent(acitivity: Activity) : Intent {
            Log.d("RadekServer", "Intent sent = waitForConnectionIntent")
            var intent = Intent(acitivity, BluetoothServerConnectionService::class.java)

            intent.setAction("waitForConnection")
            return intent
        }

        fun sendMessageIntent(acitivity: Activity, message:String) : Intent {
            Log.d("RadekServer", "Intent sent = sendMessageIntent")
            var intent = Intent(acitivity, BluetoothServerConnectionService::class.java)
            intent.setAction("sendMessage")
            intent.putExtra("message", message)
            return intent
        }

        fun initConnectionFlowIntent(acitivity: Activity, activityId:Long, connectTo:String) : Intent  {
            Log.d("RadekServer", "Intent sent = initConnectionFlowIntent")
            var intent = Intent(acitivity, BluetoothServerConnectionService::class.java)
            intent.setAction("initConnectionFlowIntent")
            intent.putExtra("connectTo", connectTo)
            intent.putExtra("activityId", activityId)
            return intent
        }
        fun stop(acitivity: Activity) : Intent  {
            Log.d("RadekServer", "Intent sent = stop")
            var intent = Intent(acitivity, BluetoothServerConnectionService::class.java)
            intent.setAction("stop")
            return intent
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if(intent.action.equals("initConnectionFlowIntent")) {
            Log.d("RadekServer", "startCommand = initConnectionFlowIntent")
            connectTo = intent.getStringExtra("connectTo")
            activityId = intent.getLongExtra("activityId", -1)

            val lastActivityId = sharedPreferences.getLong("services_activityId", -1)

            if(lastActivityId != activityId) {
                sharedPreferences.edit()
                        .remove("services_state")
                        .putString("services_connectTo", connectTo)
                        .putLong("services_activityId", activityId)
                        .commit()

                updateState(IDLE)
            }

        }
        if(intent.action.equals("stop")) {
            Log.d("RadekServer", "startCommand = stop")
            updateState(TURN_OFF)
            sharedPreferences.edit().remove("services_activityId").commit()
            stopSelf()
        }
        if(intent.action.equals("waitForConnection")) {
            Log.d("RadekServer", "startCommand = waitForConnection")
            waitForBeingDiscovered()
        }

        if(intent.action.equals("sendMessage")) {
            Log.d("RadekServer", "startCommand = sendMessage")
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

    fun waitForConnection() {
        Log.d("RadekServer", "waitForConnection")
        skipBonded = false
        Log.d("RadekServer", "acceptConnectionThread start")
        acceptConnectionThread.start(object : AcceptConnectionThread.Callback {
            override fun onConnected(bluetoothSocket: BluetoothSocket) {
                Log.d("RadekServer", "acceptConnectionThread connected")
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
                        //waitForBeingDiscovered()
                    }
                })
            }

            override fun onFailed(exception: Exception) {
                Log.d("RadekServer", "acceptConnectionThread failed")
                if(tryingToConnectToBondedDevice) {
                    skipBonded = true

                    waitForBeingDiscovered()
                    return
                }
                updateState(IDLE)
            }
        })
    }

    fun updateScanMode() {
        Log.d("RadekServer", "updateScanMode")
        if(BluetoothAdapter.getDefaultAdapter().scanMode  == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            if(state == WAITING_FOR_ENABLING_DISCOVERABILITY) {
                waitForBeingDiscovered()
            }
        }
    }

    fun waitForBeingDiscovered() {
        Log.d("RadekServer", "waitForBeingDiscovered")
        if (BluetoothAdapter.getDefaultAdapter().isEnabled == false) {
            askForEnablingBluetooth()
            updateState(WAITING_FOR_BLUETOOTH)
        } else {
            for (bondedDevice in BluetoothAdapter.getDefaultAdapter().bondedDevices) {
                if(connectTo.equals(bondedDevice.name)) {
                    if(!skipBonded) {
                        tryingToConnectToBondedDevice = true
                        updateState(WAITING_FOR_DEVICE_CONNECTION)
                        waitForConnection()
                        return
                    }
                }
            }

            if(BluetoothAdapter.getDefaultAdapter().scanMode !=  BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                updateState(WAITING_FOR_ENABLING_DISCOVERABILITY)
                askForDiscoverability()
            } else {
                tryingToConnectToBondedDevice = false
                updateState(WAITING_FOR_DEVICE_CONNECTION)
                waitForConnection()
            }
        }
    }

    private fun askForDiscoverability() {
        val startBluetoothAsk = Intent("activityTurnOnDiscoverabilityPlz")
        LocalBroadcastManager.getInstance(this).sendBroadcast(startBluetoothAsk)
    }

    private fun askForEnablingBluetooth() {
        val startBluetoothAsk = Intent("activityTurnOnBluetoothPlz")
        LocalBroadcastManager.getInstance(this).sendBroadcast(startBluetoothAsk)
    }

    private fun updateState(newState:Int) {
        Log.d("RadekServer", "updateState ( " + nameThatState(state) + " )")
        showNotificationForAState(newState)
        state = newState

        sharedPreferences.edit()
                .putInt(""+activityId+"_newState", newState)
                .putInt("services_state", newState)
                .commit()

        val broadcastIntent = Intent("updateState")
        broadcastIntent.putExtra("newState", newState)
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
    }

    val ONGOING_NOTIFICATION_ID = 1

    private fun showNotificationForAState(state:Int) {
        if(state == TURN_OFF) {
            stopForeground(true)
            return
        }

        val pendingIntent: PendingIntent =
                Intent(this, BluetoothServerActivity::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(this, 0, notificationIntent, 0)
                }

        val contentText = nameThatState(state)


        val notification: Notification = getNotificationBuilder("pl.rjuszczyk.bluetoothgame.CHANNEL_ID_FOREGROUND",
                NotificationManagerCompat.IMPORTANCE_LOW)
                .setContentTitle("Bluetooth connection")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setTicker(contentText)
                .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }



    private fun getNotificationBuilder(channelId: String, importance: Int): NotificationCompat.Builder {
        val builder: NotificationCompat.Builder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            prepareChannel(this, channelId, importance)
            builder = NotificationCompat.Builder(this, channelId)
        } else {
            builder = NotificationCompat.Builder(this)
        }
        return builder
    }

    @TargetApi(26)
    private fun prepareChannel(context: Context, id: String, importance: Int) {
        val appName = context.getString(R.string.app_name)
        val description = context.getString(R.string.channel_descirption)
        val nm = context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager

        if (nm != null) {
            var nChannel: NotificationChannel? = nm.getNotificationChannel(id)

            if (nChannel == null) {
                nChannel = NotificationChannel(id, appName, importance)
                nChannel.description = description
                nm.createNotificationChannel(nChannel)
            }
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    fun nameThatState(state: Int) : String{
        if(state == IDLE ) {
            return "IDLE"
        }
        if(state == WAITING_FOR_BLUETOOTH ) {
            return "WAITING_FOR_BLUETOOTH"
        }
        if(state == BLUETOOTH_STARTING ) {
            return "BLUETOOTH_STARTING"
        }
        if(state == WAITING_FOR_DEVICE_CONNECTION ) {
            return "WAITING_FOR_DEVICE_CONNECTION"
        }
        if(state == WAITING_FOR_ENABLING_DISCOVERABILITY ) {
            return "WAITING_FOR_ENABLING_DISCOVERABILITY"
        }
        if(state == CONNECTED ) {
            return "CONNECTED"
        }

        return "UNKNOWN"
    }
}
