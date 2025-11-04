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
    // Store the last token used for iProov verification
    private var lastIProovToken: String? = null
    companion object {
        private const val TAG = "MainActivity"
    }

    private val job = SupervisorJob()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private var sessionStateJob: Job? = null
    private var loadingDialog: AlertDialog? = null
    private lateinit var binding: ActivityMainBinding
    // Store the last scanned document results
    private var lastDocumentResults: DocumentReaderResults? = null

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

        binding.scanDocumentBtn.setOnClickListener {
            binding.surnameTv.text = "Surname:"
            binding.nameTv.text = "Name:"
            binding.dobTv?.text = "Date of Birth:"
            binding.resultIv.setImageBitmap(null)
            showScanner()
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

        // If verification is successful, call backend /validate-verification
        if (title == "Success") {
            val userId = binding.mnemonicInput.text.toString()
            val firstName = binding.nameTv.text.toString().removePrefix("Name: ")
            val lastName = binding.surnameTv.text.toString().removePrefix("Surname:")

            // Extract and format date of birth from dobTv text field
            val dateOfBirth = binding.dobTv?.text.toString().removePrefix("Date of Birth: ")
            val sex = binding.sexTv?.text.toString().removePrefix("Sex: ")

            // Use the token passed to createSession instead of resultMessage
            val verifyToken = lastIProovToken ?: ""
            Log.d(TAG, "Sending verifyToken $verifyToken to validate-verification");
            val client = okhttp3.OkHttpClient()

            uiScope.launch(Dispatchers.IO) {
                try {
                    val payload = org.json.JSONObject().apply {
                        put("token", verifyToken)
                        put("userId", userId)
                        put("firstName", firstName)
                        put("lastName", lastName)
                        put("dateOfBirth", dateOfBirth)
                        put("sex", sex)
                    }
                    val request = okhttp3.Request.Builder()
                        .url(Constants.NEUVOTE_BACKEND_URL + "/iproov/validate-verification")
                        .post(okhttp3.RequestBody.create("application/json".toMediaType(), payload.toString()))
                        .build()
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body!!.string()
                        Log.d(TAG, "Backend /iproov/validate-verification response: $responseBody")
                        withContext(Dispatchers.Main) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Verification Result")
                                .setMessage(responseBody)
                                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.cancel() }
                                .show()
                        }
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Backend validate-verification error: ${ex.localizedMessage}", ex)
                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Error")
                            .setMessage("Verification validation failed: ${ex.localizedMessage}")
                            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.cancel() }
                            .show()
                    }
                }
            }
        } else {
            AlertDialog.Builder(this@MainActivity)
                .setTitle(title)
                .setMessage(resultMessage)
                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.cancel() }
                .show()
        }
    }

    // Utility: Format date of birth as YYYY-MM-DD
    private fun formatDateOfBirth(dob: String): String {
        // Try to match common formats and convert to YYYY-MM-DD
        val regexList = listOf(
            // DD.MM.YYYY or D.M.YYYY
            Regex("^(\\d{1,2})[.](\\d{1,2})[.](\\d{4})$"),
            // YYYY-MM-DD
            Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$"),
            // YYYY/MM/DD
            Regex("^(\\d{4})/(\\d{1,2})/(\\d{1,2})$"),
            // MM/DD/YYYY or M/D/YYYY
            Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$"),
            // MM/DD/YY or M/D/YY
            Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{2})$"),
            // DD.MM.YY or D.M.YY
            Regex("^(\\d{1,2})[.](\\d{1,2})[.](\\d{2})$")
        )
        for ((i, regex) in regexList.withIndex()) {
            val match = regex.find(dob)
            if (match != null) {
                val groups = match.groupValues
                return when (i) {
                    0 -> "${groups[3]}-${groups[2].padStart(2,'0')}-${groups[1].padStart(2,'0')}" // DD.MM.YYYY
                    1 -> "${groups[1]}-${groups[2].padStart(2,'0')}-${groups[3].padStart(2,'0')}" // YYYY-MM-DD
                    2 -> "${groups[1]}-${groups[2].padStart(2,'0')}-${groups[3].padStart(2,'0')}" // YYYY/MM/DD
                    3 -> "${groups[3]}-${groups[1].padStart(2,'0')}-${groups[2].padStart(2,'0')}" // MM/DD/YYYY
                    4 -> {
                        // MM/DD/YY, convert YY to YYYY
                        val year = groups[3].toInt()
                        val fullYear = if (year >= 26) 1900 + year else 2000 + year
                        "${fullYear}-${groups[1].padStart(2,'0')}-${groups[2].padStart(2,'0')}"
                    }
                    5 -> {
                        // DD.MM.YY, convert YY to YYYY
                        val year = groups[3].toInt()
                        val fullYear = if (year >= 26) 1900 + year else 2000 + year
                        "${fullYear}-${groups[2].padStart(2,'0')}-${groups[1].padStart(2,'0')}"
                    }
                    else -> dob
                }
            }
        }
        return dob // fallback: return as is
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
                    binding.scanDocumentBtn.isEnabled = false
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
                //if (binding.doRfidCb.isChecked && results != null && results.chipPage != 0){
                if (true) {
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
                    lastDocumentResults = results
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
                // Step 1: Get enrollment token from backend
                val tokenPayload = org.json.JSONObject().apply {
                    put("userId", userId)
                }
                val tokenRequest = okhttp3.Request.Builder()
                    .url(Constants.NEUVOTE_BACKEND_URL + "/iproov/create-enrollment-token")
                    .post(okhttp3.RequestBody.create("application/json".toMediaType(), tokenPayload.toString()))
                    .build()
                client.newCall(tokenRequest).execute().use { tokenResponse ->
                    val tokenResponseBody = tokenResponse.body!!.string()
                    Log.d(TAG, "Backend /iproov/create-enrollment-token response: $tokenResponseBody")
                    val token = org.json.JSONObject(tokenResponseBody).getString("token")
                    // Step 2: Upload photo to backend
                    val enrollRequestBody = okhttp3.MultipartBody.Builder()
                        .setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("image", "photo.jpg",
                            okhttp3.RequestBody.create("image/jpeg".toMediaType(), photoBytes))
                        .addFormDataPart("token", token)
                        .build()
                    val enrollRequest = okhttp3.Request.Builder()
                        .url(Constants.NEUVOTE_BACKEND_URL + "/iproov/enroll-photo")
                        .post(enrollRequestBody)
                        .build()
                    client.newCall(enrollRequest).execute().use { enrollResponse ->
                        val result = enrollResponse.body!!.string()
                        Log.d(TAG, "Backend /iproov/enroll-photo response: $result")
                        val enrollJson = org.json.JSONObject(result)
                        val enrollSuccess = enrollJson.optBoolean("success", true)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Photo enroll result: $result", Toast.LENGTH_LONG).show()
                        }
                        if (enrollSuccess) {
                            // Step 3: Get verification token from backend
                            val verifyPayload = org.json.JSONObject().apply {
                                put("userId", userId)
                            }
                            val verifyTokenRequest = okhttp3.Request.Builder()
                                .url(Constants.NEUVOTE_BACKEND_URL + "/iproov/create-verify-token")
                                .post(okhttp3.RequestBody.create("application/json".toMediaType(), verifyPayload.toString()))
                                .build()
                            client.newCall(verifyTokenRequest).execute().use { verifyTokenResponse ->
                                val verifyTokenBody = verifyTokenResponse.body!!.string()
                                Log.d(TAG, "Backend /iproov/create-verify-token response: $verifyTokenBody")
                                val verifyToken = org.json.JSONObject(verifyTokenBody).getString("token")
                                // Step 4: Launch verification scan
                                withContext(Dispatchers.Main) {
                                    Log.d(TAG, "Launching iProov verification scan with token: $verifyToken")
                                    lastIProovToken = verifyToken
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
                Log.e(TAG, "Backend API error: ${ex.localizedMessage}", ex)
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

