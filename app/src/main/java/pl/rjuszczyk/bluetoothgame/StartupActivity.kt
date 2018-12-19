package pl.rjuszczyk.bluetoothgame

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View

class StartupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_startup)

        findViewById<View>(R.id.server).setOnClickListener {
            val intent = Intent(this, ScanConnectToDeviceActivity::class.java)

            intent.putExtra("whereToGo", WHERE_TO_GO_SERVER)
            startActivity(intent)
        }
        findViewById<View>(R.id.client).setOnClickListener {
            val intent = Intent(this, ScanConnectToDeviceActivity::class.java)

            intent.putExtra("whereToGo", WHERE_TO_GO_CLIENT)
            startActivity(intent)
        }

    }
}
