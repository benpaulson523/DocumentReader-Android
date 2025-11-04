package com.regula.backend.processing

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
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
import com.iproov.androidapiclient.AssuranceType
import com.iproov.androidapiclient.ClaimType
import com.iproov.androidapiclient.kotlinfuel.ApiClientFuel
import com.iproov.sdk.api.IProov
import com.iproov.sdk.api.exception.SessionCannotBeStartedTwiceException
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
    private var sessionStateJob: Job? = null
    private var loadingDialog: AlertDialog? = null
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.refreshMnemonicButton.setOnClickListener {
            val newMnemonic = generateMnemonicUUID()
            binding.mnemonicInput.setText(newMnemonic)
        }

        initializeReader()

        // Generate mnemonic UUID and set to input field
        val mnemonicUuid = generateMnemonicUUID()
        binding.mnemonicInput.setText(mnemonicUuid)

        binding.showScannerBtn.setOnClickListener {
            binding.surnameTv.text = "Surname:"
            binding.nameTv.text = "Name:"
            binding.resultIv.setImageBitmap(null)
            showScanner()
        }

        // Add iProov button logic
        val buttons = listOf(binding.enrolGpaButton, binding.verifyLaButton, binding.verifyGpaButton)
        buttons.forEach { btn ->
            btn.setOnClickListener {
                val mnemonic = binding.mnemonicInput.text.toString()
                if (mnemonic.isEmpty()) {
                    Toast.makeText(this, "Mnemonic cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    val claimType = when (btn) {
                        binding.enrolGpaButton -> "ENROL"
                        binding.verifyGpaButton -> "VERIFY"
                        binding.verifyLaButton -> "VERIFY"
                        else -> throw NotImplementedError()
                    }
                    val assuranceType = when (btn) {
                        binding.enrolGpaButton -> "GENUINE_PRESENCE"
                        binding.verifyGpaButton -> "GENUINE_PRESENCE"
                        binding.verifyLaButton -> "LIVENESS"
                        else -> throw NotImplementedError()
                    }
                    launchIProov(claimType, mnemonic, assuranceType)
                }
            }
        }
    }

    private fun launchIProov(claimType: String, username: String, assuranceType: String) {
    Log.d(TAG, "launchIProov called with claimType=$claimType, mnemonic=$username, assuranceType=$assuranceType")
        // Show a progress dialog or progress bar if you have one
        // binding.progressBar.visibility = View.VISIBLE
        // binding.progressBar.isIndeterminate = true

        // Map string to enums
        val claimTypeEnum = when (claimType) {
            "ENROL" -> ClaimType.ENROL
            "VERIFY" -> ClaimType.VERIFY
            else -> throw NotImplementedError()
        }
        val assuranceTypeEnum = when (assuranceType) {
            "GENUINE_PRESENCE" -> AssuranceType.GENUINE_PRESENCE
            "LIVENESS" -> AssuranceType.LIVENESS
            else -> throw NotImplementedError()
        }

        val apiClientFuel = ApiClientFuel(
            this,
            Constants.FUEL_URL,
            Constants.API_KEY,
            Constants.SECRET,
        )

        uiScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Requesting token from API client")
                val token = apiClientFuel.getToken(
                    assuranceTypeEnum,
                    claimTypeEnum,
                    username, // now mnemonic
                )
                Log.d(TAG, "Received token: $token")
                if (!job.isActive) {
                    Log.w(TAG, "Job is not active after getting token")
                    return@launch
                }
                startScan(token)
            } catch (ex: Exception) {
                Log.e(TAG, "Exception in launchIProov", ex)
                withContext(Dispatchers.Main) {
                    ex.printStackTrace()
                    if (ex is FuelError) {
                        val json = jsonDeserializer().deserialize(ex.response)
                        val description = json.obj().getString("error_description")
                        onResult("Error", description)
                    } else {
                        onResult("Error", "Failed to get token")
                    }
                }
            }
        }
    }

    private fun startScan(token: String) {
        Log.d(TAG, "Starting scan with token: $token")
        IProov.createSession(applicationContext, Constants.IPROOV_BASE_URL, token).let { session ->
            Log.d(TAG, "Session created, observing state and starting session")
            observeSessionState(session) {
                Log.d(TAG, "Session start called")
                session.start()
            }
        }
    }

    private fun observeSessionState(session: IProov.Session, whenReady: (() -> Unit)? = null) {
        Log.d(TAG, "Observing session state")
        sessionStateJob?.cancel()
        sessionStateJob = uiScope.launch(Dispatchers.IO) {
            session.state
                .onSubscription { whenReady?.invoke() }
                .collect { state ->
                    if (sessionStateJob?.isActive == true) {
                        withContext(Dispatchers.Main) {
                            Log.d(TAG, "Session state: ${state::class.java.simpleName}")
                            when (state) {
                                is IProov.State.Starting -> {
                                    // Optionally show starting UI
                                }
                                is IProov.State.Connecting -> {
                                    // Optionally show connecting UI
                                }
                                is IProov.State.Connected -> {
                                    // Optionally show connected UI
                                }
                                is IProov.State.Processing -> {
                                    // Optionally update progress UI
                                }
                                is IProov.State.Success -> onResult("Success", "")
                                is IProov.State.Failure -> onResult(state.failureResult.reason.feedbackCode.toString(), getString(state.failureResult.reason.description))
                                is IProov.State.Error -> onResult("Error", state.exception.localizedMessage)
                                is IProov.State.Canceled -> onResult("Canceled", null)
                            }
                        }
                    }
                }
        }
    }

    private fun onResult(title: String?, resultMessage: String?) {
        Log.d(TAG, "Verification scan result: title=$title, resultMessage=$resultMessage")
        Toast.makeText(this@MainActivity, "Verification scan result: $title - $resultMessage", Toast.LENGTH_LONG).show()
        AlertDialog.Builder(this@MainActivity)
            .setTitle(title)
            .setMessage(resultMessage)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.cancel() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    // Generate a mnemonic UUID (adjective-noun-uuid)
    private fun generateMnemonicUUID(): String {
        val adjectives = listOf("brave", "calm", "eager", "fancy", "gentle", "jolly", "kind", "lucky", "proud", "witty")
        val nouns = listOf("lion", "tiger", "eagle", "panda", "shark", "wolf", "falcon", "otter", "fox", "bear")
        val adj = adjectives.random()
        val noun = nouns.random()
        val uuid = java.util.UUID.randomUUID().toString().substring(0, 8)
        return "$adj-$noun-$uuid"
    }

    private fun initializeReader() {
        showDialog("initializing")
        Executors.newSingleThreadExecutor().execute {
            try {
                val licInput = resources.openRawResource(R.raw.regula)
                val available = licInput.available()
                val license = ByteArray(available)
                licInput.read(license)
                licInput.close()
                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    val docReaderConfig = DocReaderConfig(license)
                    DocumentReader.Instance()
                        .initializeReader(this@MainActivity, docReaderConfig, initCompletion)

                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                Toast.makeText(
                    this,
                    "init error: " + ex.localizedMessage,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val initCompletion =
        IDocumentReaderInitCompletion { result: Boolean, error: DocumentReaderException? ->
            dismissDialog()

            if (result) {
                if (DocumentReader.Instance().availableScenarios.size == 0) {
                    Toast.makeText(
                        this@MainActivity,
                        "Available scenarios list is empty",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.showScannerBtn.isEnabled = false
                }
            } else {
                Toast.makeText(this@MainActivity, "Init failed: ${error?.message}", Toast.LENGTH_LONG).show()
                return@IDocumentReaderInitCompletion
            }
        }

    private val completion =
        IDocumentReaderCompletion { action, results, error ->
            //processing is finished, all results are ready

            if (action == DocReaderAction.COMPLETE) {
                if (binding.doRfidCb.isChecked && results != null && results.chipPage != 0){
                    DocumentReader.Instance().startRFIDReader(this, object : IRfidReaderCompletion() {
                        override fun onCompleted(
                            rfidAction: Int,
                            documentReaderResults: DocumentReaderResults?,
                            e: DocumentReaderException?
                        ) {
                            finalize(documentReaderResults)
                        }
                    })
                } else finalize(results)
            } else {
                //something happened before all results were ready
                if (action == DocReaderAction.CANCEL) {
                    Toast.makeText(this@MainActivity, "Scanning was cancelled", Toast.LENGTH_LONG)
                        .show()
                } else if (action == DocReaderAction.ERROR) {
                    Toast.makeText(this@MainActivity, "Error:${error?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

    private fun finalize(results: DocumentReaderResults?) {
        showDialog("Finalizing process...")
        DocumentReader.Instance()
            .finalizePackage { action: Int, transactionInfo: TransactionInfo?, documentReaderException: DocumentReaderException? ->
                dismissDialog()
                if (action == DocReaderAction.COMPLETE) {
                    Toast.makeText(
                        this@MainActivity,
                        "Finalize Done. TransactionId " + transactionInfo?.transactionId,
                        Toast.LENGTH_LONG
                    )
                        .show()
                    displayImage(results)
                    displayTextFields(results)
                } else if (documentReaderException != null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to Finalize. Error " + documentReaderException.message,
                        Toast.LENGTH_LONG
                    )
                        .show()
                }
            }
    }

    private fun showScanner() {
        val backendProcessingConfig = BackendProcessingConfig(Constants.REGULA_BASE_URL)
        DocumentReader.Instance().functionality().edit().setDoRecordProcessingVideo(true).apply()

        DocumentReader.Instance().processParams().backendProcessingConfig = backendProcessingConfig

        val scannerConfig = ScannerConfig.Builder(Scenario.SCENARIO_FULL_PROCESS).build()

        DocumentReader.Instance().startScanner(this, scannerConfig, completion)
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
                enrollDocumentPhotoWithIProov(documentImage)
            }
        }
    }

    private fun displayTextFields(results: DocumentReaderResults?) {
        if (results?.getTextFieldByType(eVisualFieldType.FT_SURNAME) != null) {
            val surname = "Surname:" + results.getTextFieldValueByType(eVisualFieldType.FT_SURNAME)
            binding.surnameTv.text = surname
        } else {
            binding.surnameTv.text = "Surname:"
        }

        if (results?.getTextFieldByType(eVisualFieldType.FT_GIVEN_NAMES) != null) {
            val name = "Name: " + results.getTextFieldValueByType(eVisualFieldType.FT_GIVEN_NAMES)
            binding.nameTv.text = name
        } else {
            binding.nameTv.text = "Name:"
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
    
    // Enroll document photo with iProov using mnemonic UUID as user_id
    private fun enrollDocumentPhotoWithIProov(documentPhoto: Bitmap) {
        val userId = binding.mnemonicInput.text.toString()
        val photoBytes = bitmapToJpegBytes(documentPhoto)
        val client = okhttp3.OkHttpClient()

        uiScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Get token from iProov API
                val apiKey = Constants.API_KEY
                val secret = Constants.SECRET
                val resource = Constants.IPROOV_SERVICE_PROVIDER
                val apiBase = Constants.IPROOV_REST_API_BASE
                val assuranceType = "genuine_presence"
                val tokenPayload = org.json.JSONObject().apply {
                    put("api_key", apiKey)
                    put("secret", secret)
                    put("resource", resource)
                    put("assurance_type", assuranceType)
                    put("user_id", userId)
                }
                val tokenRequest = okhttp3.Request.Builder()
                    .url("$apiBase/v2/claim/enrol/token")
                    .post(okhttp3.RequestBody.create("application/json".toMediaType(), tokenPayload.toString()))
                    .build()
                client.newCall(tokenRequest).execute().use { tokenResponse ->
                    val tokenResponseBody = tokenResponse.body!!.string()
                    Log.d(TAG, "iProov API /v2/claim/enrol/token response: $tokenResponseBody")
                    val token = org.json.JSONObject(tokenResponseBody).getString("token")
                    // Step 2: Upload photo to iProov API
                    val enrollRequestBody = okhttp3.MultipartBody.Builder()
                        .setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("api_key", apiKey)
                        .addFormDataPart("secret", secret)
                        .addFormDataPart("rotation", "0")
                        .addFormDataPart("image", "photo.jpg",
                            okhttp3.RequestBody.create("image/jpeg".toMediaType(), photoBytes))
                        .addFormDataPart("token", token)
                        .build()
                    val enrollRequest = okhttp3.Request.Builder()
                        .url("$apiBase/v2/claim/enrol/image")
                        .post(enrollRequestBody)
                        .build()
                    client.newCall(enrollRequest).execute().use { enrollResponse ->
                        val result = enrollResponse.body!!.string()
                        Log.d(TAG, "iProov API /v2/claim/enrol/image response: $result")
                        // Check for success in result (assume JSON with success field or status)
                        val enrollJson = org.json.JSONObject(result)
                        val enrollSuccess = enrollJson.optBoolean("success", true) // fallback: treat as success if no field
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Photo enroll result: $result", Toast.LENGTH_LONG).show()
                        }
                        if (enrollSuccess) {
                            // Step 3: Get verification token
                            val verifyPayload = org.json.JSONObject().apply {
                                put("api_key", apiKey)
                                put("secret", secret)
                                put("resource", resource)
                                put("assurance_type", assuranceType)
                                put("user_id", userId)
                            }
                            val verifyTokenRequest = okhttp3.Request.Builder()
                                .url("$apiBase/v2/claim/verify/token")
                                .post(okhttp3.RequestBody.create("application/json".toMediaType(), verifyPayload.toString()))
                                .build()
                            client.newCall(verifyTokenRequest).execute().use { verifyTokenResponse ->
                                val verifyTokenBody = verifyTokenResponse.body!!.string()
                                Log.d(TAG, "iProov API /v2/claim/verify/token response: $verifyTokenBody")
                                val verifyToken = org.json.JSONObject(verifyTokenBody).getString("token")
                                // Step 4: Launch verification scan
                                withContext(Dispatchers.Main) {
                                    Log.d(TAG, "Launching iProov verification scan with token: $verifyToken")
                                    IProov.createSession(applicationContext, Constants.IPROOV_BASE_URL, verifyToken).let { session ->
                                        observeSessionState(session) {
                                            session.start()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "iProov API error: ${ex.localizedMessage}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Photo enroll error: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Utility: Convert Bitmap to JPEG ByteArray
    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }
}

