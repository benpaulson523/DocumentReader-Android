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

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationLivenessActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationLivenessBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        
        iProovManager = IProovManager.getInstanceOrNull() ?: return
        iProovManager.setShowResultHandler(this::onResult)

        binding.beginBtn.setOnClickListener {
            iProovManager.launchFacialScanSession()
            binding.beginBtn.isEnabled = false
        }

        binding.continueBtn.setOnClickListener {
            Toast.makeText(this, "Navigate to next page", Toast.LENGTH_LONG).show()
            //NavigationHelper.navigateToRegistrationSelectDoc(this)
        }
    }
    
    private fun onResult(title: String?, resultMessage: String?) {
        Log.d(TAG, "Verification scan result: title=$title, resultMessage=$resultMessage")

        binding.beginBtn.visibility = View.GONE

        val resultTv = binding.resultMessageTv

        if (title == "Success") {
            resultTv.visibility = View.GONE
            Toast.makeText(this, "Liveness check passed", Toast.LENGTH_LONG).show()
            binding.continueBtn.isEnabled = true
        } else {
            Toast.makeText(this, "Facial scan failed", Toast.LENGTH_LONG).show()
            resultTv.text = resultMessage ?: "Unknown error"
            resultTv.visibility = View.VISIBLE
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
