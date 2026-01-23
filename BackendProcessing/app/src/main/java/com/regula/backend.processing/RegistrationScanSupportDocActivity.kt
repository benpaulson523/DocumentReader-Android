package com.regula.backend.processing

import android.util.Log
import android.os.Bundle
import android.widget.Toast
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.regula.backend.processing.databinding.ActivityRegistrationScanSupportDocBinding
import androidx.appcompat.app.AlertDialog
import android.graphics.Bitmap
import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.app.Activity

import com.regula.documentreader.api.DocumentReader
import com.regula.documentreader.api.completions.IDocumentReaderCompletion
import com.regula.documentreader.api.completions.IDocumentReaderInitCompletion
import com.regula.documentreader.api.completions.rfid.IRfidReaderCompletion
import com.regula.documentreader.api.config.ScannerConfig
import com.regula.documentreader.api.enums.DocReaderAction
import com.regula.documentreader.api.enums.Scenario
import com.regula.documentreader.api.enums.eGraphicFieldType
import com.regula.documentreader.api.enums.eVisualFieldType
import com.regula.documentreader.api.errors.DocumentReaderException
import com.regula.documentreader.api.params.BackendProcessingConfig
import com.regula.documentreader.api.params.DocReaderConfig
import com.regula.documentreader.api.results.DocumentReaderResults
import com.regula.documentreader.api.results.TransactionInfo
import com.regula.documentreader.api.enums.eRPRM_ResultType

class RegistrationScanSupportDocActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationScanSupportDocActivity"
    }

    private lateinit var binding: ActivityRegistrationScanSupportDocBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var regulaScanner: RegulaScanner
    private lateinit var neuvoteManager: NeuvoteManager
    private lateinit var downstreamLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationScanSupportDocActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationScanSupportDocBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        
        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        
        regulaScanner = RegulaScanner.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        
        if (neuvoteManager.getSupportDocumentScan() == null) {
            startScanner()
        } else {
            binding.fullDocumentImageView.setImageBitmap(neuvoteManager.getOfficialDocumentScan())
            binding.fullDocumentImageView.visibility = View.VISIBLE
            binding.fullDocumentBorderView.visibility = View.VISIBLE
            binding.continueBtn.setText(getString(R.string.continueString))
            binding.continueBtn.isEnabled = true
            binding.retakeBtn.visibility = View.GONE
            binding.instructionMessage.visibility = View.GONE
        }

        binding.retakeBtn.setOnClickListener {
            startScanner()
        }

        binding.continueBtn.setOnClickListener {
            val intent = Intent(this, RegistrationDataActivity::class.java)
            downstreamLauncher.launch(intent)
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

    private fun startScanner() {
        binding.retakeBtn.isEnabled = true
        binding.fullDocumentImageView.visibility = View.GONE
        binding.fullDocumentBorderView.visibility = View.GONE
        binding.continueBtn.isEnabled = false
        regulaScanner.showScanner(
            false,
            true,
            onFinalize = { results -> docScanned(results) },
            onFailure = { docScanningFailed() }
        )
    }

    private fun docScanningFailed() {
        Log.d(TAG, "docScanningFailed")

        binding.continueBtn.isEnabled = false
        handleFailure(getString(R.string.document_scanning_failed), null)
    }
    
    private fun handleFailure(errorMessage: String, toastMessage: String?) {
        if (toastMessage != null) {
            showToast(this, toastMessage, 460)
        } else {
            showToast(this, errorMessage, 460)
        }
        binding.errorMessage.text = errorMessage
        binding.errorMessage.visibility = View.VISIBLE
    }

    private fun docScanned(results: DocumentReaderResults?) {
        Log.d(TAG, "docScanned")

        binding.errorMessage.visibility = View.GONE
        binding.continueBtn.isEnabled = true

        val fullDocumentImage: Bitmap? = results?.getGraphicFieldImageByType(
            eGraphicFieldType.GF_DOCUMENT_IMAGE,
            eRPRM_ResultType.RPRM_RESULT_TYPE_RAW_IMAGE,
            0, // page index
        )

        neuvoteManager.setSupportDocumentScan(fullDocumentImage)

        // Display the image in the UI
        if (fullDocumentImage != null) {
            binding.fullDocumentImageView.setImageBitmap(fullDocumentImage)
            binding.fullDocumentImageView.visibility = View.VISIBLE
            binding.fullDocumentBorderView.visibility = View.VISIBLE
        } else {
            binding.fullDocumentImageView.visibility = View.GONE
            binding.fullDocumentBorderView.visibility = View.GONE
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
