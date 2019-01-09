package pl.rjuszczyk.bluetoothgame

import android.bluetooth.BluetoothAdapter
import android.content.*
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.TextView


class BluetoothServerActivity : AppCompatActivity() {
    val IDLE = 0
    val WAITING_FOR_BLUETOOTH = 1
    val BLUETOOTH_STARTING = 6
    val WAITING_FOR_DEVICE_CONNECTION = 3
    val WAITING_FOR_ENABLING_DISCOVERABILITY = 4
    val CONNECTED = 5

    lateinit var connectTo:String
    var activityId:Long = 0

    lateinit var sendMessage: Button
    lateinit var connect: Button
    lateinit var deviceName: TextView
    lateinit var status: TextView

    var state = IDLE


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(savedInstanceState == null) {
            activityId = System.currentTimeMillis()
        } else {
            activityId = savedInstanceState.getLong("activityId")
        }

        setContentView(R.layout.activity_server)

        connectTo = intent.getStringExtra("connectTo")

        status = findViewById(R.id.status)

        sendMessage = findViewById(R.id.sendMessage)
        deviceName = findViewById(R.id.deviceName)
        connect = findViewById(R.id.connect)
        deviceName.text = BluetoothAdapter.getDefaultAdapter().name


        sendMessage.setOnClickListener {
            startService(BluetoothServerConnectionService.sendMessageIntent(this, "test message"))

        }

        connect.setOnClickListener {
            startService(BluetoothServerConnectionService.waitForConnectionIntent(this))
        }

        startService(BluetoothServerConnectionService.initConnectionFlowIntent(this, activityId, connectTo))
    }

    val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context, p1: Intent) {
            if(p1.action == "activityTurnOnBluetoothPlz") {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
            if(p1.action == "updateState") {
                var newState = p1.getIntExtra("newState", -1)
                updateState(newState)
            }
            if(p1.action == "activityTurnOnDiscoverabilityPlz") {
                val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
                startActivityForResult(discoverableIntent, 999)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val intentFilter = IntentFilter()
        intentFilter.addAction("activityTurnOnBluetoothPlz")
        intentFilter.addAction("updateState")
        intentFilter.addAction("activityTurnOnDiscoverabilityPlz")
        LocalBroadcastManager.getInstance(this).registerReceiver(bluetoothStateReceiver, intentFilter)

        val sharedPreferences = getSharedPreferences("broadcastHelper", Context.MODE_PRIVATE)
        if(sharedPreferences.contains(""+activityId+"_newState")) {
            val newState = sharedPreferences.getInt(""+activityId+"_newState",-1)
            updateState(newState)
        }
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(bluetoothStateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        if(isFinishing) {
            startService(BluetoothServerConnectionService.stop(this))
            val sharedPreferences = getSharedPreferences("broadcastHelper", Context.MODE_PRIVATE)
            sharedPreferences.edit().remove(""+activityId+"_newState").commit()
        }
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
        outState.putLong("activityId", activityId)
        super.onSaveInstanceState(outState)
    }
}