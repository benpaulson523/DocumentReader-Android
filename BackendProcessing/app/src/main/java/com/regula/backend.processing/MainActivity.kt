package com.regula.backend.processing

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.regula.backend.processing.databinding.ActivityMainBinding

import android.util.Log
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.json.jsonDeserializer
import com.regula.backend.processing.IProovManager
import com.regula.backend.processing.RegulaScanner
import com.regula.backend.processing.formatDateOfBirth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import okhttp3.MediaType.Companion.toMediaType
import android.app.Activity

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val job = SupervisorJob()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private var loadingDialog: AlertDialog? = null
    private lateinit var binding: ActivityMainBinding
    private lateinit var regulaScanner: RegulaScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened MainActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Initialize settings manager
        SettingsManager.init(this)

        binding.settingsButton.setOnClickListener {
            // Prompt for password before navigating to settings
            val passwordDialog = AlertDialog.Builder(this)
            passwordDialog.setTitle("Enter Password")
            val input = EditText(this)
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordDialog.setView(input)
            passwordDialog.setCancelable(false)
            passwordDialog.setPositiveButton("OK") { dialog, _ ->
                val entered = input.text.toString()
                if (entered == Constants.SETTINGS_PASSWORD) {
                    NavigationHelper.navigateToSettings(this)
                } else {
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            passwordDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            passwordDialog.show()
        }

        binding.registerBtn.setOnClickListener {
            NavigationHelper.navigateToRegistrationStart(this)
        }

        regulaScanner = RegulaScanner.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        regulaScanner.initializeReader(false)
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

    private fun dismissDialog() {
        if (loadingDialog != null) {
            loadingDialog!!.dismiss()
        }
    }

    private fun showDialog(msg: String?) {
        dismissDialog()
        val builderDialog = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.simple_dialog, null)
        builderDialog.setTitle(msg)
        builderDialog.setView(dialogView)
        builderDialog.setCancelable(false)
        loadingDialog = builderDialog.show()
    }
}

