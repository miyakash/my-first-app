package com.example.bleperipheral

import android.annotation.SuppressLint
import android.content.*
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val log = intent?.getStringExtra("log") ?: return
            logTextView.append("$log\n")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logTextView = findViewById(R.id.textViewLog)

        val serviceIntent = Intent(this, BlePeripheralService::class.java)
        startService(serviceIntent)

        Toast.makeText(this, "BLEペリフェラル起動中", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        registerReceiver(logReceiver, IntentFilter("BLE_LOG"))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(logReceiver)
    }
}
