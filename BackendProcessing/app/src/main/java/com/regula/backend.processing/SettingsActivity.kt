package com.regula.backend.processing

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.EditText
import android.widget.TextView
import android.widget.Button
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import android.view.ViewGroup
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.Gravity
import android.content.Context
import android.content.SharedPreferences

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Connection Settings"

        val serverInput = findViewById<EditText>(R.id.serverInput)
        val portInput = findViewById<EditText>(R.id.portInput)
        val saveButton = findViewById<Button>(R.id.saveButton)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        serverInput.setText(prefs.getString("server_address", ""))
        portInput.setText(prefs.getString("server_port", ""))

        saveButton.setOnClickListener {
            val address = serverInput.text.toString()
            val port = portInput.text.toString()
            SettingsManager.setServerAddress(this, address)
            SettingsManager.setServerPort(this, port)
            finish()
        }
    }
}
