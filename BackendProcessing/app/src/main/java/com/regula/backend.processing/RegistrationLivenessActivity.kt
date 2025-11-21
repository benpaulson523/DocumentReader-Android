package com.regula.backend.processing

import android.util.Log
import android.os.Bundle
import android.widget.Toast
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.regula.backend.processing.databinding.ActivityRegistrationLivenessBinding
import androidx.appcompat.app.AlertDialog

class RegistrationLivenessActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationLivenessActivity"
    }

    private lateinit var binding: ActivityRegistrationLivenessBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var iProovManager: IProovManager
    private lateinit var neuvoteManager: NeuvoteManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationLivenessActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationLivenessBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        
        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        
        iProovManager = IProovManager.getInstanceOrNull() ?: return
        iProovManager.setShowResultHandler(this::onResult)

        binding.beginBtn.setOnClickListener {
            val resultTv = binding.resultMessageTv
            resultTv.visibility = View.GONE
            iProovManager.launchFacialScanSession()
            binding.beginBtn.isEnabled = false
        }
    }
    
    private fun onResult(title: String?, resultMessage: String?) {
        Log.d(TAG, "Verification scan result: title=$title, resultMessage=$resultMessage")

        val resultTv = binding.resultMessageTv

        if (title == "Success") {
            binding.beginBtn.visibility = View.GONE
            resultTv.visibility = View.GONE
            Toast.makeText(this, "Liveness check passed", Toast.LENGTH_LONG).show()
            if (neuvoteManager.hasAddress()) {
                Log.d(TAG, "Address is populated")
                NavigationHelper.navigateToRegistrationData(this)
            } else {
                Log.d(TAG, "Need to obtain address")
                NavigationHelper.navigateToRegistrationEnterAddress(this)
            }
        } else {
            Toast.makeText(this, "Facial scan failed: $resultMessage", Toast.LENGTH_LONG).show()
            resultTv.text = "Facial scan failed: " + (resultMessage ?: "Unknown error")
            resultTv.visibility = View.VISIBLE
            iProovManager.getVerificationToken()
            binding.beginBtn.isEnabled = true
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
