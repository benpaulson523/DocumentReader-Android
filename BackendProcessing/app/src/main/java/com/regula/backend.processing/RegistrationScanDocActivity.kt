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
import com.regula.documentreader.api.enums.eRPRM_ResultType

class RegistrationScanDocActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationScanDocActivity"
    }

    private lateinit var binding: ActivityRegistrationScanDocBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var regulaScanner: RegulaScanner
    private lateinit var iProovManager: IProovManager
    private lateinit var neuvoteManager: NeuvoteManager
    private var failed: Boolean = false
    private var passed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationScanDocActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationScanDocBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        
        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        
        val registrationCode = generateRegistrationCode()
        neuvoteManager.setRegistrationCode(registrationCode)
        
        regulaScanner = RegulaScanner.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        
        if (!passed) {
            regulaScanner.showScanner(
                neuvoteManager.getReadChip(),
                onFinalize = { results -> docScanned(results) },
                onFailure = { docScanningFailed() }
            )
        }

        iProovManager = IProovManager.getInstance(
            context = this,
            registrationCode = neuvoteManager.getRegistrationCode()
        )

        binding.continueBtn.setOnClickListener {
            if (failed) {
                regulaScanner.showScanner(
                    neuvoteManager.getReadChip(),
                    onFinalize = { results -> docScanned(results) },
                    onFailure = { docScanningFailed() }
                )
            } else if (passed) {
                NavigationHelper.navigateToRegistrationLiveness(this)
            } else {
                Log.d(TAG, "continueBtn should not be enabled if neither failed nor passed")
            }
        }
    }

    private fun docScanningFailed() {
        Log.d(TAG, "docScanningFailed")
        failed = true

        binding.errorMessage.visibility = View.VISIBLE
        binding.continueBtn.setText("RETRY SCAN")
        binding.continueBtn.isEnabled = true
    }

    private fun docScanned(results: DocumentReaderResults?) {
        Log.d(TAG, "docScanned")
        failed = false
        binding.continueBtn.setText("CONTINUE")
        binding.continueBtn.isEnabled = false

        binding.errorMessage.visibility = View.GONE

        binding.idUploadIcon.visibility = View.VISIBLE
        binding.title.visibility = View.VISIBLE

        val surnameField = results?.getTextFieldByType(eVisualFieldType.FT_SURNAME)
        val surname = surnameField?.value ?: ""
        neuvoteManager.setSurname(surname)
        Log.d(TAG, "Surname: $surname")
        
        val firstNameField = results?.getTextFieldByType(eVisualFieldType.FT_GIVEN_NAMES)
        val firstNameRaw = firstNameField?.value ?: ""
        val (firstName, middleName) = if (firstNameRaw.contains(",")) {
            val parts = firstNameRaw.split(",", limit = 2).map { it.trim() }
            Pair(parts[0], parts.getOrNull(1) ?: "")
        } else {
            Pair(firstNameRaw, "")
        }
        neuvoteManager.setFirstName(firstName)
        neuvoteManager.setMiddleName(middleName)
        Log.d(TAG, "First name: $firstName, Middle name: $middleName")
        
        val dobField = results?.getTextFieldByType(eVisualFieldType.FT_DATE_OF_BIRTH)
        val rawDob = dobField?.value ?: ""
        val formattedDob = formatDateOfBirth(rawDob)
        neuvoteManager.setDateOfBirth(formattedDob)
        Log.d(TAG, "Date of birth: $formattedDob")
        
        val sexField = results?.getTextFieldByType(eVisualFieldType.FT_SEX)
        val sex = sexField?.value ?: ""
        neuvoteManager.setSex(sex)
        Log.d(TAG, "Sex: $sex")
        
        val streetAddressField = results?.getTextFieldByType(eVisualFieldType.FT_ADDRESS_STREET)
        val streetAddress = streetAddressField?.value ?: ""
        neuvoteManager.setStreetAddress(streetAddress)
        Log.d(TAG, "Street address: $streetAddress")
        
        val cityField = results?.getTextFieldByType(eVisualFieldType.FT_ADDRESS_CITY)
        val city = cityField?.value ?: ""
        neuvoteManager.setCity(city)
        Log.d(TAG, "City: $city")
        
        val jurisdictionField = results?.getTextFieldByType(eVisualFieldType.FT_ADDRESS_JURISDICTION_CODE)
        val jurisdiction = jurisdictionField?.value ?: ""
        neuvoteManager.setJurisdiction(jurisdiction)
        Log.d(TAG, "Jurisdiction: $jurisdiction")
        
        val postalCodeField = results?.getTextFieldByType(eVisualFieldType.FT_ADDRESS_POSTAL_CODE)
        val postalCode = postalCodeField?.value ?: ""
        neuvoteManager.setPostalCode(postalCode)
        Log.d(TAG, "Postal code: $postalCode")

        if (neuvoteManager.getReadChip()) {
            if (results?.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.RFID_RESULT_TYPE_RFID_IMAGE_DATA) != null) {
                var documentImage = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.RFID_RESULT_TYPE_RFID_IMAGE_DATA)
                if (documentImage != null) {
                    processDocumentImage(documentImage)
                }
                else {
                    showToast(this, "Failed to retrieve document image")
                }
            } else {
                showToast(this, "Failed to capture document image")
            }
        } else {
            if (results?.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.NONE) != null) {
                var documentImage = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.NONE)
                if (documentImage != null) {
                    processDocumentImage(documentImage)
                }
                else {
                    showToast(this, "Failed to retrieve document image")
                }
            } else {
                showToast(this, "Failed to capture document image")
            }
        }
    }

    fun processDocumentImage(documentImage: Bitmap) {
        val aspectRatio = documentImage.width.toDouble() / documentImage.height.toDouble()
        var scaledDocumentImage = Bitmap.createScaledBitmap(
            documentImage,
            (480 * aspectRatio).toInt(), 480, false
        )

        Log.d(TAG, "Calling enrollDocumentPhotoWithIProov")
        // Enroll the photo with iProov, pass callback for UI update
        iProovManager.enrollDocumentPhotoWithIProov(scaledDocumentImage) { errorMsg ->
            if (errorMsg == null) {
                showToast(this, "Upload complete")
                binding.errorMessage.visibility = View.GONE
                binding.continueBtn.setText("CONTINUE")
                binding.title.setText("Your document has been uploaded.")
                passed = true
            } else {
                showToast(this, errorMsg)
                binding.errorMessage.text = errorMsg
                binding.errorMessage.visibility = View.VISIBLE
                binding.continueBtn.setText("RETRY SCAN")
                failed = true
            }
            binding.continueBtn.isEnabled = true
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
