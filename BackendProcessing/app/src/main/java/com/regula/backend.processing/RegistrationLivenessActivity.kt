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
import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.app.Activity

class RegistrationLivenessActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationLivenessActivity"
    }

    private lateinit var binding: ActivityRegistrationLivenessBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var iProovManager: IProovManager
    private lateinit var neuvoteManager: NeuvoteManager
    private lateinit var downstreamLauncher: ActivityResultLauncher<Intent>

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
        
        iProovManager = IProovManager.getInstance(
            context = this
        )
        iProovManager.setShowResultHandler(this::onResult)

        if (iProovManager.getLivenessPassed()) {
            binding.beginBtn.visibility = View.GONE
            binding.continueBtn.visibility = View.VISIBLE
            binding.title.setText("Live facial scan complete.")
        } else {
            binding.beginBtn.visibility = View.VISIBLE
            binding.continueBtn.visibility = View.GONE
        }

        binding.beginBtn.setOnClickListener {
            val resultTv = binding.resultMessageTv
            resultTv.visibility = View.GONE
            iProovManager.launchFacialScanSession()
            binding.beginBtn.isEnabled = false
        }
        
        binding.continueBtn.setOnClickListener {
            advanceToNextScreen()
        }
        
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
                finish()
            }
        }
    }
    
    private fun onResult(title: String?, resultMessage: String?) {
        Log.d(TAG, "Verification scan result: title=$title, resultMessage=$resultMessage")

        val resultTv = binding.resultMessageTv

        if (title == getString(R.string.success)) {
            iProovManager.setLivenessPassed(true)
            binding.title.setText("Live facial scan complete.")
            binding.beginBtn.visibility = View.GONE
            resultTv.visibility = View.GONE
            binding.continueBtn.visibility = View.VISIBLE
            showToast(this, getString(R.string.liveness_check_passed))
            advanceToNextScreen()
        } else {
            showToast(this, getString(R.string.facial_scan_failed, resultMessage ?: getString(R.string.unknown_error)))
            resultTv.text = getString(R.string.facial_scan_failed, resultMessage ?: getString(R.string.unknown_error))
            resultTv.visibility = View.VISIBLE
            iProovManager.getVerificationToken()
            binding.beginBtn.isEnabled = true
        }
    }

    private fun advanceToNextScreen() {
        if (neuvoteManager.hasAddress()) {
            Log.d(TAG, "Address is populated")
            val intent = Intent(this, RegistrationDataActivity::class.java)
            downstreamLauncher.launch(intent)
        } else {
            Log.d(TAG, "Need to obtain address")
            val intent = Intent(this, RegistrationEnterAddressActivity::class.java)
            downstreamLauncher.launch(intent)
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
