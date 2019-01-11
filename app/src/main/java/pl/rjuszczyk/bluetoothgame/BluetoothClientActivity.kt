package pl.rjuszczyk.bluetoothgame

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView


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
    lateinit var connectTo:String
    lateinit var qrCodeTxt:String
    var activityId :Long = 0

    var state = IDLE

    companion object {
        fun getStartIntent(activity: Activity, qrCodeTxt :String, connectTo:String ) : Intent {
            val intent = Intent(activity, BluetoothClientActivity::class.java)
            intent.putExtra("connectTo", connectTo )
            intent.putExtra("qrCodeTxt", qrCodeTxt )
            return intent
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(savedInstanceState == null) {
            activityId = System.currentTimeMillis()
        } else {
            activityId = savedInstanceState.getLong("activityId")
        }

        connectTo = intent.getStringExtra("connectTo")
        qrCodeTxt = intent.getStringExtra("qrCodeTxt")

        setContentView(R.layout.activity_client)
        status = findViewById(R.id.status)
        val qrcode = findViewById<ImageView>(R.id.qrcode)

        qrcode.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(p0: View, p1: Int, p2: Int, p3: Int, p4: Int, p5: Int, p6: Int, p7: Int, p8: Int) {
                qrcode.setImageBitmap(RetrieveNameFromQrCodesActivity.getQRCodeImage(qrCodeTxt, p0.width, p0.height))
            }
        })

        startDiscovery = findViewById(R.id.startDiscovery)
        sendMessage = findViewById(R.id.sendMessage)

        startDiscovery.setOnClickListener {
            startService(BluetoothClientConnectionService.discoverDevicesIntent(this))
        }
        sendMessage.setOnClickListener {
            startService(BluetoothClientConnectionService.sendMessageIntent(this, "message"))
        }

        val wasConnected =savedInstanceState?.getBoolean("wasConnected", false)?:false
        if(wasConnected) {
//            discoverDevices()
        }
        if(savedInstanceState == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(BluetoothClientConnectionService.initConnectionFlowIntent(this, activityId, connectTo))
            } else {
                startService(BluetoothClientConnectionService.initConnectionFlowIntent(this, activityId, connectTo))
            }
        }
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
        }
    }

    override fun onStart() {
        super.onStart()

        val intentFilter = IntentFilter()
        intentFilter.addAction("activityTurnOnBluetoothPlz")
        intentFilter.addAction("updateState")
        LocalBroadcastManager.getInstance(this).registerReceiver(bluetoothStateReceiver, intentFilter)

        val sharedPreferences = getSharedPreferences("broadcastHelper", Context.MODE_PRIVATE)
        if(sharedPreferences.contains(""+activityId+"_newState")) {
            val newState = sharedPreferences.getInt(""+activityId+"_newState",-1)
            updateState(newState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if(isFinishing) {
            startService(BluetoothClientConnectionService.stop(this))
            val sharedPreferences = getSharedPreferences("broadcastHelper", Context.MODE_PRIVATE)
            sharedPreferences.edit().remove(""+activityId+"_newState").commit()
        }
    }

    override fun onStop() {
        super.onStop()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(bluetoothStateReceiver)
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
        outState.putBoolean("wasConnected", state == CONNECTED)
        outState.putLong("activityId", activityId)
        super.onSaveInstanceState(outState)
    }
}