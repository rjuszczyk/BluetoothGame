package pl.rjuszczyk.bluetoothgame

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.*
import android.os.Handler
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.widget.Toast
import android.app.NotificationChannel
import android.support.v4.content.ContextCompat.getSystemService
import android.app.NotificationManager
import android.annotation.TargetApi
import android.os.Build
import android.support.v4.app.NotificationManagerCompat


class BluetoothClientConnectionService : Service() {

    val IDLE = 0
    val WAITING_FOR_BLUETOOTH = 4
    val BLUETOOTH_STARTING = 5
    val SEARCHING = 1
    val CONNECTING = 2
    val CONNECTED = 3
    val TURN_OFF = 6

    lateinit var performConnectionThread: PerformConnectionThread
    lateinit var sharedPreferences: SharedPreferences
    var bluetoothSocketThread: BluetoothSocketThread? = null

    lateinit var connectTo:String
    var activityId:Long = 0

    var tryingToConnectToBondedDevice = false
    var skipBonded = false

    var state = IDLE

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
            Log.d("BluetoothDevice", bluetoothDevice.toString() + "   name = " + bluetoothDevice.name)

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

    override fun onCreate() {
        super.onCreate()

        sharedPreferences = getSharedPreferences("broadcastHelper", Context.MODE_PRIVATE)
        performConnectionThread = PerformConnectionThread(Handler())

        state = sharedPreferences.getInt("services_state", 0)
        var lastConnectTo = sharedPreferences.getString("services_connectTo", null)
        lastConnectTo?.let {
            connectTo = it
        }

        registerReceiver(discoveryBroadcastReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(discoverySartedChangedBroadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED))
        registerReceiver(discoveryFinishedChangedBroadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        //restoring state
        if (BluetoothAdapter.getDefaultAdapter().isEnabled) {
            if(state == WAITING_FOR_BLUETOOTH || state == BLUETOOTH_STARTING) {
                discoverDevices()
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

            val lastActivityId = sharedPreferences.getLong("services_activityId", -1)

            if(lastActivityId != activityId) {
                sharedPreferences.edit()
                        .remove("services_state")
                        .putString("services_connectTo", connectTo)
                        .putLong("services_activityId", activityId)
                        .commit()
            }

            updateState(IDLE)
        }
        if(intent.action.equals("stop")) {
            sharedPreferences.edit().remove("services_activityId").commit()
            updateState(TURN_OFF)
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
                    updateState(SEARCHING)
                    discoverDevices()
                    return
                }
                updateState(IDLE)
            }
        })
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
        val startBluetoothAsk = Intent("activityTurnOnBluetoothPlz")
        LocalBroadcastManager.getInstance(this).sendBroadcast(startBluetoothAsk)
    }

    private fun updateState(newState:Int) {
        showNotificationForAState(newState)
        state = newState

        sharedPreferences.edit()
                .putInt(""+activityId+"_newState", newState)
                .putInt("services_state", newState)
                .commit()
        state = newState

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

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    fun nameThatState(state: Int) : String{
        if(state == IDLE) {
            return "IDLE"
        }
        if(state == WAITING_FOR_BLUETOOTH) {
            return "WAITING_FOR_BLUETOOTH"
        }
        if(state == BLUETOOTH_STARTING) {
            return "BLUETOOTH_STARTING"
        }
        if(state == SEARCHING) {
            return "SEARCHING"
        }
        if(state == CONNECTING) {
            return "CONNECTING"
        }
        if(state == CONNECTED) {
            return "CONNECTED"
        }

        return "UNKNOWN"
    }
}
