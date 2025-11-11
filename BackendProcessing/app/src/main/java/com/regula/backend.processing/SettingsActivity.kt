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
import com.regula.backend.processing.databinding.ActivitySettingsBinding
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

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

    override fun setContentView(view: View?) {
        super.setContentView(view)

        applyEdgeToEdgeInsets()
    }

    private fun applyEdgeToEdgeInsets() {
        val rootView = window.decorView.findViewWithTag<View>("content")
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
                val systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout()
                )
                view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
                )
                insets
            }
        }
    }
}
