package com.regula.backend.processing

import android.util.Log
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.regula.backend.processing.databinding.ActivityRegistrationStartBinding
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.app.Activity

class RegistrationStartActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationStartActivity"
    }

    private lateinit var binding: ActivityRegistrationStartBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var regulaScanner: RegulaScanner
    private lateinit var downstreamLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationStartActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationStartBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        
        // Initialize settings manager
        SettingsManager.init(this)

        binding.startBtn.setOnClickListener {
            val intent = Intent(this, RegistrationSelectDocActivity::class.java)
            downstreamLauncher.launch(intent)
        }

        regulaScanner = RegulaScanner.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        regulaScanner.initializeReader()
        
        // Register the ActivityResultLauncher
        downstreamLauncher = registerForActivityResult(StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val resultString = data?.getStringExtra("resultString")
                Log.i(TAG, "Received result string from downstream activity: " + resultString)
                val resultIntent = Intent()
                resultIntent.putExtra("resultString", resultString)
                resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setResult(RESULT_OK, resultIntent)
                finishAndRemoveTask()
            }
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
