package com.regula.backend.processing

import android.graphics.Bitmap
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

import android.util.Log
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.json.jsonDeserializer
import com.regula.backend.processing.IProovManager
import com.regula.backend.processing.RegulaScanner
import com.regula.backend.processing.formatDateOfBirth
import com.regula.backend.processing.generateMnemonicUUID
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

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val job = SupervisorJob()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private var loadingDialog: AlertDialog? = null
    private lateinit var binding: ActivityMainBinding
    private lateinit var iProovManager: IProovManager
    private lateinit var regulaScanner: RegulaScanner

    override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityMainBinding.inflate(layoutInflater)
            val view = binding.root
            setContentView(view)

            // Initialize settings manager
            SettingsManager.init(this)

            binding.settingsButton.setOnClickListener {
                val intent = android.content.Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }

        binding.refreshMnemonicButton.setOnClickListener {
            val newMnemonic = generateMnemonicUUID()
            binding.mnemonicInput.setText(newMnemonic)
        }

        regulaScanner = RegulaScanner(
            context = this,
            binding = binding,
            onResults = { results ->
                displayImage(results)
                displayTextFields(results)
            },
            onFinalize = { results ->
                displayImage(results)
                displayTextFields(results)
            },
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        regulaScanner.initializeReader()

        // Generate mnemonic UUID and set to input field
        val mnemonicUuid = generateMnemonicUUID()
        binding.mnemonicInput.setText(mnemonicUuid)

        binding.scanDocumentBtn.setOnClickListener {
            binding.surnameTv.text = "Surname:"
            binding.nameTv.text = "Name:"
            binding.dobTv?.text = "Date of Birth:"
            binding.resultIv.setImageBitmap(null)
            regulaScanner.showScanner()
        }

        iProovManager = IProovManager(
            context = this,
            mnemonicInputProvider = { binding.mnemonicInput.text.toString() },
            showResult = { title, message -> onResult(title, message) },
            onVerificationSuccess = { token -> /* Optionally handle token if needed */ }
        )
    }

    private fun onResult(title: String?, resultMessage: String?) {
        Log.d(TAG, "Verification scan result: title=$title, resultMessage=$resultMessage")

        // If verification is successful, call backend /validate-verification
        if (title == "Success") {
            Toast.makeText(this@MainActivity, "Verification scan passed", Toast.LENGTH_LONG).show()
            showExtraFields()
        } else {
            Toast.makeText(this@MainActivity, "Verification scan failed", Toast.LENGTH_LONG).show()
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

    private fun displayImage(results: DocumentReaderResults?) {
        if (results?.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT) != null) {
            var documentImage = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT)
            if (documentImage != null) {
                val aspectRatio = documentImage.width.toDouble() / documentImage.height.toDouble()
                documentImage = Bitmap.createScaledBitmap(
                    documentImage,
                    (480 * aspectRatio).toInt(), 480, false
                )
                binding.resultIv.setImageBitmap(documentImage)
                // Automatically enroll the photo with iProov
                iProovManager.enrollDocumentPhotoWithIProov(documentImage)
            }
        }
    }

    private fun displayTextFields(results: DocumentReaderResults?) {

        if (results?.getTextFieldByType(eVisualFieldType.FT_SURNAME) != null) {
            val surname = "Surname:" + results.getTextFieldValueByType(eVisualFieldType.FT_SURNAME)
            binding.surnameTv.text = surname
            binding.surnameTv.visibility = View.VISIBLE
        } else {
            binding.surnameTv.text = "Surname:"
            binding.surnameTv.visibility = View.GONE
        }

        if (results?.getTextFieldByType(eVisualFieldType.FT_GIVEN_NAMES) != null) {
            val name = "Name: " + results.getTextFieldValueByType(eVisualFieldType.FT_GIVEN_NAMES)
            binding.nameTv.text = name
            binding.nameTv.visibility = View.VISIBLE
        } else {
            binding.nameTv.text = "Name:"
            binding.nameTv.visibility = View.GONE
        }

        // Display date of birth between last name and photo
        if (results?.getTextFieldByType(eVisualFieldType.FT_DATE_OF_BIRTH) != null) {
            val rawDob = results.getTextFieldValueByType(eVisualFieldType.FT_DATE_OF_BIRTH)
            val formattedDob = formatDateOfBirth(rawDob.toString())
            val dob = "Date of Birth: $formattedDob"
            binding.dobTv?.text = dob
            binding.dobTv?.visibility = View.VISIBLE
        } else {
            binding.dobTv?.text = "Date of Birth:"
            binding.dobTv?.visibility = View.GONE
        }
        
        // Display sex between date of birth and photo
        if (results?.getTextFieldByType(eVisualFieldType.FT_SEX) != null) {
            val sex = results.getTextFieldValueByType(eVisualFieldType.FT_SEX)
            val sexDisplay = "Sex: $sex"
            binding.sexTv?.text = sexDisplay
            binding.sexTv?.visibility = View.VISIBLE
        } else {
            binding.sexTv?.text = "Sex:"
            binding.sexTv?.visibility = View.GONE
        }
    }

    private fun showExtraFields() {
        val labelIds = listOf(
            R.id.emailLabel, R.id.phoneLabel, R.id.streetLabel, R.id.cityLabel, R.id.provinceLabel, R.id.postalLabel
        )
        val inputIds = listOf(
            R.id.emailInput, R.id.phoneInput, R.id.streetInput, R.id.cityInput, R.id.provinceInput, R.id.postalInput
        )
        for (i in labelIds.indices) {
            val labelView = findViewById<TextView>(labelIds[i])
            val inputView = findViewById<EditText>(inputIds[i])
            labelView?.visibility = View.VISIBLE
            inputView?.visibility = View.VISIBLE
        }

        populateDefaultsForExtraFields();

        val verifyEmailBtn = findViewById<View>(R.id.verifyEmailBtn)
        verifyEmailBtn?.setOnClickListener {
            val email = findViewById<EditText>(R.id.emailInput)?.text.toString()
            val mnemonicUuid = binding.mnemonicInput.text.toString()
            Neuvote.sendVerificationEmail(this, email, mnemonicUuid, iProovManager)
        }
        // Add listeners to all extra input fields to check if all are non-empty
        for (inputId in inputIds) {
            val inputView = findViewById<EditText>(inputId)
            inputView?.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    checkVerifyEmailButtonVisibility()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        // Initial check
        checkVerifyEmailButtonVisibility()
    }

    private fun populateDefaultsForExtraFields() {
        findViewById<EditText>(R.id.emailInput)?.setText("test@example.com")
        findViewById<EditText>(R.id.phoneInput)?.setText("555-123-4567")
        findViewById<EditText>(R.id.streetInput)?.setText("123 Main St")
        findViewById<EditText>(R.id.cityInput)?.setText("Toronto")
        findViewById<EditText>(R.id.provinceInput)?.setText("ON")
        findViewById<EditText>(R.id.postalInput)?.setText("A1A 1A1")
    }

    private fun checkVerifyEmailButtonVisibility() {
        val inputIds = listOf(
            R.id.emailInput, R.id.phoneInput, R.id.streetInput, R.id.cityInput, R.id.provinceInput, R.id.postalInput
        )
        val allFilled = inputIds.all { id ->
            val inputView = findViewById<EditText>(id)
            inputView?.visibility == View.VISIBLE && !inputView.text.isNullOrBlank()
        }
        val verifyEmailBtn = findViewById<View>(R.id.verifyEmailBtn)
        verifyEmailBtn?.visibility = if (allFilled) View.VISIBLE else View.GONE
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

}

