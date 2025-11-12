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
import android.app.Activity

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
    private lateinit var neuvoteManager: NeuvoteManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened MainActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Initialize settings manager
        SettingsManager.init(this)

        binding.settingsButton.setOnClickListener {
            NavigationHelper.navigateToSettings(this)
        }
        
        binding.nextBtn.isEnabled = false

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
            binding.surnameTv.text = getString(R.string.surname_label)
            binding.nameTv.text = getString(R.string.name_label)
            binding.dobTv?.text = getString(R.string.dob_label)
            binding.resultIv.setImageBitmap(null)
            regulaScanner.showScanner()
        }
        
        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        neuvoteManager.setMnemonicUuid(mnemonicUuid)

        iProovManager = IProovManager.getInstance(
            context = this,
            mnemonicUuid = mnemonicUuid,
            onVerificationSuccess = { token -> /* Optionally handle token if needed */ },
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )

        binding.nextBtn.setOnClickListener {
            NavigationHelper.navigateToFacialScan(this)
        }
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
                // Automatically enroll the photo with iProov, pass callback for UI update
                iProovManager.enrollDocumentPhotoWithIProov(documentImage) {
                    binding.nextBtn.isEnabled = true
                }
            }
        } else {
            binding.resultIv.setImageBitmap(null)
        }
    }

    private fun displayTextFields(results: DocumentReaderResults?) {

        val neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )

        if (results?.getTextFieldByType(eVisualFieldType.FT_SURNAME) != null) {
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
            binding.dobTv?.text = dob
            binding.dobTv?.visibility = View.VISIBLE
            neuvoteManager.setDateOfBirth(formattedDob)
        } else {
            binding.dobTv?.text = getString(R.string.dob_label)
            binding.dobTv?.visibility = View.GONE
            neuvoteManager.setDateOfBirth(null)
        }
        
        // Display sex between date of birth and photo
        if (results?.getTextFieldByType(eVisualFieldType.FT_SEX) != null) {
            val sex = results.getTextFieldValueByType(eVisualFieldType.FT_SEX)
            val sexDisplay = getString(R.string.sex_label) + ": " + sex
            binding.sexTv?.text = sexDisplay
            binding.sexTv?.visibility = View.VISIBLE
            neuvoteManager.setSex(sex)
        } else {
            binding.sexTv?.text = getString(R.string.sex_label)
            binding.sexTv?.visibility = View.GONE
            neuvoteManager.setSex(null)
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

