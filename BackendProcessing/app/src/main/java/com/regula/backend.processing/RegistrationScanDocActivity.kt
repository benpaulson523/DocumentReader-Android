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
        
        
        regulaScanner = RegulaScanner.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )

        iProovManager = IProovManager.getInstance(
            context = this
        )
        
        if (!iProovManager.getEnrolled()) {
            val registrationCode = generateRegistrationCode()
            neuvoteManager.setRegistrationCode(registrationCode)
            iProovManager.setRegistrationCode(registrationCode)
            startScanner()
        } else {
            binding.uploadingMsg.setText(R.string.document_uploaded)
            binding.uploadingMsg.visibility = View.VISIBLE
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
            if (iProovManager.getEnrolled()) {
                NavigationHelper.navigateToRegistrationLiveness(this)
            } else {
                enrollPhoto()
            }
        }
    }

    private fun startScanner() {
        binding.retakeBtn.isEnabled = true
        binding.fullDocumentImageView.visibility = View.GONE
        binding.fullDocumentBorderView.visibility = View.GONE
        binding.continueBtn.isEnabled = false
        binding.uploadingMsg.visibility = View.GONE
        regulaScanner.showScanner(
            neuvoteManager.getReadChip(),
            onFinalize = { results -> docScanned(results) },
            onFailure = { docScanningFailed() }
        )
    }

    private fun docScanningFailed() {
        Log.d(TAG, "docScanningFailed")

        handleFailure(getString(R.string.document_scanning_failed), null)
    }

    private fun docScanned(results: DocumentReaderResults?) {
        Log.d(TAG, "docScanned")

        binding.errorMessage.visibility = View.GONE

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
        
        val unitNumberField = results?.getTextFieldByType(eVisualFieldType.FT_ADDRESS_FLAT)
        val unitNumber = unitNumberField?.value ?: ""
        neuvoteManager.setUnitNumber(unitNumber)
        Log.d(TAG, "Unit number: $unitNumber")
        
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
            handlePhoto(true, results)
        } else {
            handlePhoto(false, results)
        }

        saveFullScan(results)
    }

    fun saveFullScan(results: DocumentReaderResults?) {
        val fullDocumentImage: Bitmap? = results?.getGraphicFieldImageByType(
            eGraphicFieldType.GF_DOCUMENT_IMAGE,
            eRPRM_ResultType.RPRM_RESULT_TYPE_RAW_IMAGE,
            0, // page index
        )

        neuvoteManager.setOfficialDocumentScan(fullDocumentImage)

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

    fun handlePhoto(chip: Boolean, results: DocumentReaderResults?) {
        if (chip) {
            if (results?.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.RFID_RESULT_TYPE_RFID_IMAGE_DATA) != null) {
                var documentImage = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.RFID_RESULT_TYPE_RFID_IMAGE_DATA)
                if (documentImage != null) {
                    neuvoteManager.setPhoto(documentImage)
                    binding.continueBtn.isEnabled = true
                    binding.continueBtn.setText("Upload")
                    binding.uploadingMsg.setText("Document successfully scanned")
                    binding.uploadingMsg.visibility = View.VISIBLE
                }
                else {
                    handlePhoto(false, results)
                }
            } else {
                handleFailure(getString(R.string.failed_capture_document_image_chip), getString(R.string.failed_capture_document_image_short))
            }
        }
        else {
            if (results?.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.NONE) != null) {
                var documentImage = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.NONE)
                if (documentImage != null) {
                    neuvoteManager.setPhoto(documentImage)
                    binding.continueBtn.isEnabled = true
                    binding.continueBtn.setText("Upload")
                    binding.uploadingMsg.setText("Document successfully scanned")
                    binding.uploadingMsg.visibility = View.VISIBLE
                }
                else {
                    handleFailure(getString(R.string.failed_retrieve_document_image), null)
                }
            } else {
                handleFailure(getString(R.string.failed_capture_document_image_short), null)
            }
        }
    }

    fun enrollPhoto() {

        binding.uploadingMsg.visibility = View.VISIBLE
        binding.uploadingMsg.setText(getString(R.string.uploading_document_msg))

        val documentImage: Bitmap? = neuvoteManager.getPhoto()

        if (documentImage != null) {
            val aspectRatio = documentImage.width.toDouble() / documentImage.height.toDouble()

            var scaledDocumentImage = Bitmap.createScaledBitmap(
                documentImage,
                (480 * aspectRatio).toInt(), 480, false
            )

            Log.d(TAG, "Calling enrollDocumentPhotoWithIProov")
            // Enroll the photo with iProov, pass callback for UI update
            iProovManager.enrollDocumentPhotoWithIProov(scaledDocumentImage) { errorMsg ->
                if (errorMsg == null) {
                    binding.errorMessage.visibility = View.GONE
                    binding.uploadingMsg.setText(R.string.document_uploaded)
                    binding.continueBtn.setText(getString(R.string.continueString))
                    binding.continueBtn.isEnabled = true
                    binding.retakeBtn.visibility = View.GONE
                    binding.instructionMessage.visibility = View.GONE
                    iProovManager.setEnrolled(true)
                    showToast(this, getString(R.string.upload_complete))
                } else {
                    handleFailure(errorMsg, null)
                }
            }
        } else {
            handleFailure(getString(R.string.unknown_error), null)
        }
    }

    private fun handleFailure(errorMessage: String, toastMessage: String?) {
        binding.uploadingMsg.visibility = View.GONE
        if (toastMessage != null) {
            showToast(this, toastMessage, 460)
        } else {
            showToast(this, errorMessage, 460)
        }
        binding.errorMessage.text = errorMessage
        binding.errorMessage.visibility = View.VISIBLE
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
