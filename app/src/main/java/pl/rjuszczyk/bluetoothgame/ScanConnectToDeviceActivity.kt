package pl.rjuszczyk.bluetoothgame

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.EditText

const val WHERE_TO_GO_CLIENT = 0
const val WHERE_TO_GO_SERVER = 1

class ScanConnectToDeviceActivity : AppCompatActivity() {

    lateinit var connectTo: EditText
    lateinit var testData: Button
    lateinit var testData2: Button
    lateinit var testData3: Button
    lateinit var testData4: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_connect_to_device)

        connectTo = findViewById(R.id.connectTo)
        testData = findViewById(R.id.testData)
        testData2 = findViewById(R.id.testData2)
        testData3 = findViewById(R.id.testData3)
        testData4 = findViewById(R.id.testData4)

        val whereToGo = intent.getIntExtra("whereToGo", -1)

        findViewById<View>(R.id.client).setOnClickListener {
            val connvectToDeviceName = connectTo.text
            val intent =
            if(whereToGo == WHERE_TO_GO_SERVER) {
                Intent(this@ScanConnectToDeviceActivity, BluetoothServerActivity::class.java)
            } else {
                Intent(this@ScanConnectToDeviceActivity, BluetoothClientActivity::class.java)
            }

            intent.putExtra("connectTo", connvectToDeviceName.toString())
            startActivity(intent)
            finish()
        }

        testData.setOnClickListener{
            connectTo.setText(testData.text)

        }
        testData2.setOnClickListener{
            connectTo.setText(testData2.text)

        }
        testData3.setOnClickListener{
            connectTo.setText(testData3.text)

        }
        testData4.setOnClickListener{
            connectTo.setText(testData4.text)

        }
    }
}