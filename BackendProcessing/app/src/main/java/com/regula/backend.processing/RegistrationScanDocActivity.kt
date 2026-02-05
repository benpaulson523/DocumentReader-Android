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
import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.app.Activity
import android.util.TypedValue
import android.view.ViewGroup

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
import com.regula.documentreader.api.results.DocumentReaderDocumentType

class RegistrationScanDocActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationScanDocActivity"
    }

    private lateinit var binding: ActivityRegistrationScanDocBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var regulaScanner: RegulaScanner
    private lateinit var iProovManager: IProovManager
    private lateinit var neuvoteManager: NeuvoteManager
    private lateinit var downstreamLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationScanDocActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationScanDocBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        //modify scan preview based on mobile phone vs. tablet
        if (!Constants.MOBILE_CONFIG) {
            val frameLayoutParams = binding.documentViewFrame.layoutParams
            val heightInDp = 300
            val heightInPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, heightInDp.toFloat(), view.resources.displayMetrics
            ).toInt()
            frameLayoutParams.height = heightInPx
            binding.documentViewFrame.layoutParams = frameLayoutParams

            val borderViewParams = binding.fullDocumentBorderView.layoutParams as ViewGroup.MarginLayoutParams
            val marginInDp = 32
            val marginInPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, marginInDp.toFloat(), resources.displayMetrics
            ).toInt()
            borderViewParams.marginStart = marginInPx
            borderViewParams.marginEnd = marginInPx
            binding.fullDocumentBorderView.layoutParams = borderViewParams
        }
        
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
            Log.i(TAG, "not yet enrolled with iProov");
            val registrationCode = generateRegistrationCode()
            Log.i(TAG, "registrationCode = $registrationCode");
            neuvoteManager.setRegistrationCode(registrationCode)
            iProovManager.setRegistrationCode(registrationCode)
            startScanner()
        } else {
            Log.i(TAG, "already enrolled with iProov");
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
            if (isInternetAvailable(this)) {
                if (iProovManager.getEnrolled()) {
                    val intent = Intent(this, RegistrationLivenessActivity::class.java)
                    downstreamLauncher.launch(intent)
                } else {
                    enrollPhoto()
                }
            } else {
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
        
        // Use OnBackPressedDispatcher for reliable back button handling
        // Always navigate to RegistrationSelectDocActivity on OS back button
        onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Log.i(TAG, "onBackPressed (OnBackPressedDispatcher)")
                    val intent = Intent(this@RegistrationScanDocActivity, RegistrationSelectDocActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }
            }
        )
    }

    private fun startScanner() {
        binding.retakeBtn.isEnabled = true
        binding.fullDocumentImageView.visibility = View.GONE
        binding.fullDocumentBorderView.visibility = View.GONE
        binding.continueBtn.isEnabled = false
        binding.uploadingMsg.visibility = View.GONE
        regulaScanner.showScanner(
            neuvoteManager.getReadChip(),
            false,
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
        } else if (firstNameRaw.contains(" ")) {
            // No comma but contains space(s): treat first token as first name, rest as middle name
            val idx = firstNameRaw.indexOf(' ')
            val first = firstNameRaw.substring(0, idx).trim()
            val middle = firstNameRaw.substring(idx + 1).trim()
            Pair(first, middle)
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

        val countryName = results?.documentType?.firstOrNull()?.dCountryName?.uppercase()
        neuvoteManager.setCountry(countryName)
        Log.d(TAG, "Country: $countryName")

        if (neuvoteManager.getReadChip()) {
            handlePhoto(true, results, false)
        } else {
            handlePhoto(false, results, false)
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

    fun handlePhoto(chip: Boolean, results: DocumentReaderResults?, fallback: Boolean) {
        if (chip) {
            if (results?.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.RFID_RESULT_TYPE_RFID_IMAGE_DATA) != null) {
                var documentImage = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.RFID_RESULT_TYPE_RFID_IMAGE_DATA)
                if (documentImage != null) {
                    neuvoteManager.setPhoto(documentImage)
                    binding.uploadingMsg.setText("Document successfully scanned")
                    binding.uploadingMsg.visibility = View.VISIBLE

                    if (isInternetAvailable(this)) {
                        binding.continueBtn.setText("Upload")
                    } else {
                        binding.continueBtn.setText("Continue")
                    }
                    binding.continueBtn.isEnabled = true
                }
                else {
                    handlePhoto(false, results, true)
                }
            } else {
                handleFailure(getString(R.string.failed_capture_document_image_chip), getString(R.string.failed_capture_document_image_short))
                handlePhoto(false, results, true)
            }
        }
        else {
            if (results?.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.NONE) != null) {
                var documentImage = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.NONE)
                if (documentImage != null) {
                    neuvoteManager.setPhoto(documentImage)

                    if (isInternetAvailable(this)) {
                        binding.continueBtn.setText("Upload")
                    } else {
                        binding.continueBtn.setText("Continue")
                    }
                    binding.continueBtn.isEnabled = true

                    if (!fallback) {
                        binding.uploadingMsg.setText("Document successfully scanned")
                        binding.uploadingMsg.visibility = View.VISIBLE
                    }
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

        binding.errorMessage.visibility = View.GONE
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
            binding.continueBtn.isEnabled = false
            // Enroll the photo with iProov, pass callback for UI update
            iProovManager.enrollDocumentPhotoWithIProov(scaledDocumentImage) { errorMsg ->
                if (errorMsg == null) {
                    binding.uploadingMsg.setText(R.string.document_uploaded)
                    binding.continueBtn.setText(getString(R.string.continueString))
                    binding.retakeBtn.visibility = View.GONE
                    binding.instructionMessage.visibility = View.GONE
                    binding.continueBtn.isEnabled = true
                    iProovManager.setEnrolled(true)
                    showToast(this, getString(R.string.upload_complete))
                } else {
                    handleFailure(errorMsg, null)
                    binding.continueBtn.isEnabled = true
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
