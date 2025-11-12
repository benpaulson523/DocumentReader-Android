package com.regula.backend.processing

import android.util.Log
import android.os.Bundle
import android.widget.Toast
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.regula.backend.processing.databinding.ActivityRegistrationScanDocBinding
import androidx.appcompat.app.AlertDialog
import android.graphics.Bitmap

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

class RegistrationScanDocActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationScanDocActivity"
    }

    private lateinit var binding: ActivityRegistrationScanDocBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var regulaScanner: RegulaScanner
    private lateinit var iProovManager: IProovManager
    private lateinit var neuvoteManager: NeuvoteManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationScanDocActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationScanDocBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        
        regulaScanner = RegulaScanner(
            context = this,
            onResults = { results ->
                docScanned(results)
            },
            onFinalize = { results ->
                docScanned(results)
            },
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        regulaScanner.initializeReader {
            regulaScanner.showScanner()
        }
        
        val mnemonicUuid = generateMnemonicUUID()
        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        neuvoteManager.setMnemonicUuid(mnemonicUuid)

        iProovManager = IProovManager.getInstance(
            context = this,
            mnemonicUuid = neuvoteManager.getMnemonicUuid()
        )
        
        binding.continueBtn.setOnClickListener {
            NavigationHelper.navigateToRegistrationLiveness(this)
        }
    }

    private fun docScanned(results: DocumentReaderResults?) {
        binding.idUploadIcon.visibility = View.VISIBLE
        binding.title.visibility = View.VISIBLE

        if (results?.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT) != null) {
            var documentImage = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT)
            if (documentImage != null) {
                val aspectRatio = documentImage.width.toDouble() / documentImage.height.toDouble()
                documentImage = Bitmap.createScaledBitmap(
                    documentImage,
                    (480 * aspectRatio).toInt(), 480, false
                )

                // Enroll the photo with iProov, pass callback for UI update
                iProovManager.enrollDocumentPhotoWithIProov(documentImage) {
                    Toast.makeText(this, "Upload complete", Toast.LENGTH_LONG).show()
                    binding.continueBtn.isEnabled = true
                }
            }
            else {
                Toast.makeText(this, "Failed to retrieve document image", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Failed to capture document image", Toast.LENGTH_LONG).show()
        }

        /*if (results?.getTextFieldByType(eVisualFieldType.FT_SURNAME) != null) {
            val surname = getString(R.string.surname_label) + ": " + results.getTextFieldValueByType(eVisualFieldType.FT_SURNAME)
            binding.surnameTv.text = surname
            binding.surnameTv.visibility = View.VISIBLE
            binding.scanDocumentBtn.visibility = View.GONE
            neuvoteManager.setSurname(results.getTextFieldValueByType(eVisualFieldType.FT_SURNAME))
        } else {
            binding.surnameTv.text = getString(R.string.surname_label)
            binding.surnameTv.visibility = View.GONE
            neuvoteManager.setSurname(null)
        }

        if (results?.getTextFieldByType(eVisualFieldType.FT_GIVEN_NAMES) != null) {
            val name = getString(R.string.name_label) + ": " + results.getTextFieldValueByType(eVisualFieldType.FT_GIVEN_NAMES)
            binding.nameTv.text = name
            binding.nameTv.visibility = View.VISIBLE
            neuvoteManager.setName(results.getTextFieldValueByType(eVisualFieldType.FT_GIVEN_NAMES))
        } else {
            binding.nameTv.text = getString(R.string.name_label)
            binding.nameTv.visibility = View.GONE
            neuvoteManager.setName(null)
        }

        // Display date of birth between last name and photo
        if (results?.getTextFieldByType(eVisualFieldType.FT_DATE_OF_BIRTH) != null) {
            val rawDob = results.getTextFieldValueByType(eVisualFieldType.FT_DATE_OF_BIRTH)
            val formattedDob = formatDateOfBirth(rawDob.toString())
            val dob = getString(R.string.dob_label) + ": " + formattedDob
            binding.dobTv.text = dob
            binding.dobTv.visibility = View.VISIBLE
            neuvoteManager.setDateOfBirth(formattedDob)
        } else {
            binding.dobTv.text = getString(R.string.dob_label)
            binding.dobTv.visibility = View.GONE
            neuvoteManager.setDateOfBirth(null)
        }
        
        // Display sex between date of birth and photo
        if (results?.getTextFieldByType(eVisualFieldType.FT_SEX) != null) {
            val sex = results.getTextFieldValueByType(eVisualFieldType.FT_SEX)
            val sexDisplay = getString(R.string.sex_label) + ": " + sex
            binding.sexTv.text = sexDisplay
            binding.sexTv.visibility = View.VISIBLE
            neuvoteManager.setSex(sex)
        } else {
            binding.sexTv.text = getString(R.string.sex_label)
            binding.sexTv.visibility = View.GONE
            neuvoteManager.setSex(null)
        }*/
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
