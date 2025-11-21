package com.regula.backend.processing

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.onSubscription
import okhttp3.MediaType.Companion.toMediaType
import com.iproov.sdk.api.IProov
import com.iproov.sdk.api.exception.SessionCannotBeStartedTwiceException
import android.app.Activity
import android.view.View

class IProovManager private constructor(
    private val context: Context,
    private val registrationCode: String?
) {
    companion object {
        private const val TAG = "IProovManager"

        @Volatile
        private var instance: IProovManager? = null

        @JvmStatic
        fun getInstance(
            context: Context,
            registrationCode: String?
        ): IProovManager {
            return instance ?: synchronized(this) {
                instance ?: IProovManager(
                    context,
                    registrationCode
                ).also { instance = it }
            }
        }

        @JvmStatic
        fun getInstanceOrNull(): IProovManager? {
            return instance
        }
    }

    private var lastIProovToken: String? = null
    private val job = SupervisorJob()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private var sessionStateJob: Job? = null

    private var showResult: ((title: String?, message: String?) -> Unit)? = null

    fun setShowResultHandler(handler: (title: String?, message: String?) -> Unit) {
        this.showResult = handler
    }

    fun enrollDocumentPhotoWithIProov(documentPhoto: Bitmap, onUiUpdate: ((String?) -> Unit)? = null) {
        val photoBytes = bitmapToJpegBytes(documentPhoto)
        val client = okhttp3.OkHttpClient()

        uiScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Get enrollment token from backend
                val tokenPayload = org.json.JSONObject().apply {
                    put("userId", registrationCode)
                }
                val tokenRequest = okhttp3.Request.Builder()
                    .url(NeuvoteManager.getNeuvoteServerUrl() + Constants.ENDPOINT_IPROOV_CREATE_ENROLLMENT_TOKEN)
                    .post(okhttp3.RequestBody.create("application/json".toMediaType(), tokenPayload.toString()))
                    .build()
                client.newCall(tokenRequest).execute().use { tokenResponse ->
                    val tokenResponseBody = tokenResponse.body!!.string()
                    Log.d(TAG, "Backend $Constants.ENDPOINT_IPROOV_CREATE_ENROLLMENT_TOKEN response: $tokenResponseBody")
                    if (!tokenResponse.isSuccessful) {
                        val errorMsg = try {
                            val json = org.json.JSONObject(tokenResponseBody)
                            json.optString("error", json.optString("message", "Failed to get enrollment token"))
                        } catch (e: Exception) {
                            "Failed to get enrollment token"
                        }
                        withContext(Dispatchers.Main) {
                            showToast(context, errorMsg)
                            onUiUpdate?.invoke(errorMsg)
                        }
                        return@use
                    }
                    val token = org.json.JSONObject(tokenResponseBody).getString("token")
                    // Step 2: Upload photo to backend
                    val enrollRequestBody = okhttp3.MultipartBody.Builder()
                        .setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("image", "photo.jpg",
                            okhttp3.RequestBody.create("image/jpeg".toMediaType(), photoBytes))
                        .addFormDataPart("token", token)
                        .build()
                    val enrollRequest = okhttp3.Request.Builder()
                        .url(NeuvoteManager.getNeuvoteServerUrl() + Constants.ENDPOINT_IPROOV_ENROLL_PHOTO)
                        .post(enrollRequestBody)
                        .build()
                    client.newCall(enrollRequest).execute().use { enrollResponse ->
                        val result = enrollResponse.body!!.string()
                        Log.d(TAG, "Backend /iproov/enroll-photo response: $result")
                        if (!enrollResponse.isSuccessful) {
                            val errorMsg = try {
                                val json = org.json.JSONObject(result)
                                json.optString("error", json.optString("message", "Photo enrollment failed"))
                            } catch (e: Exception) {
                                "Photo enrollment failed"
                            }
                            withContext(Dispatchers.Main) {
                                showToast(context, errorMsg)
                                onUiUpdate?.invoke(errorMsg)
                            }
                            return@use
                        }
                        val enrollJson = org.json.JSONObject(result)
                        val enrollSuccess = enrollJson.optBoolean("success", true)
                        if (enrollSuccess) {
                            // Step 3: Get verification token from backend
                            getVerificationToken()
                            withContext(Dispatchers.Main) {
                                onUiUpdate?.invoke(null)
                            }
                        } else {
                            val errorMsg = enrollJson.optString("error", "Photo enrollment failed")
                            withContext(Dispatchers.Main) {
                                showToast(context, errorMsg)
                                onUiUpdate?.invoke(errorMsg)
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Backend API error: ${ex.localizedMessage}", ex)
                withContext(Dispatchers.Main) {
                    val errorMsg = if (ex.localizedMessage.contains("failed to connect")) {
                        "Error: could not connect to Neuvote"
                    } else {
                        "Photo enroll error: ${ex.localizedMessage}"
                    }
                    showToast(context, errorMsg)
                    onUiUpdate?.invoke(errorMsg)
                }
            }
        }
    }
    
    fun getVerificationToken() {
        val client = okhttp3.OkHttpClient()

        uiScope.launch(Dispatchers.IO) {
            try {
                val verifyPayload = org.json.JSONObject().apply {
                    put("userId", registrationCode)
                }
                val verifyTokenRequest = okhttp3.Request.Builder()
                    .url(NeuvoteManager.getNeuvoteServerUrl() + Constants.ENDPOINT_IPROOV_CREATE_VERIFY_TOKEN)
                    .post(okhttp3.RequestBody.create("application/json".toMediaType(), verifyPayload.toString()))
                    .build()
                client.newCall(verifyTokenRequest).execute().use { verifyTokenResponse ->
                    val verifyTokenBody = verifyTokenResponse.body!!.string()
                    Log.d(TAG, "Backend /iproov/create-verify-token response: $verifyTokenBody")
                    if (!verifyTokenResponse.isSuccessful) {
                        val errorMsg = try {
                            val json = org.json.JSONObject(verifyTokenBody)
                            json.optString("error", json.optString("message", "Failed to get verification token"))
                        } catch (e: Exception) {
                            "Failed to get verification token"
                        }
                        withContext(Dispatchers.Main) {
                            showToast(context, errorMsg)
                        }
                        return@use
                    }
                    lastIProovToken = org.json.JSONObject(verifyTokenBody).getString("token")
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Backend API error: ${ex.localizedMessage}", ex)
                withContext(Dispatchers.Main) {
                    showToast(context, "Verify token error: ${ex.localizedMessage}")
                }
            }
        }
    }

    fun launchFacialScanSession() {
        val verifyToken = getLastIProovToken() ?: return
        Log.d(TAG, "Launching iProov verification scan with token: $verifyToken")
        IProov.createSession(context.applicationContext, Constants.IPROOV_BASE_URL, verifyToken).let { session ->
            observeSessionState(session) {
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
                                    // Optionally show processing UI
                                }
                                is IProov.State.Success -> {
                                    showResult?.invoke("Success", "")
                                }
                                is IProov.State.Failure -> {
                                    showResult?.invoke(state.failureResult.reason.feedbackCode.toString(), context.getString(state.failureResult.reason.description))
                                }
                                is IProov.State.Error -> {
                                    showResult?.invoke("Error", state.exception.localizedMessage)
                                }
                                is IProov.State.Canceled -> {
                                    showResult?.invoke("Canceled", null)
                                }
                            }
                        }
                    }
                }
        }
    }

    fun getLastIProovToken(): String? = lastIProovToken

    fun destroy() {
        job.cancel()
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }
    
    fun validateVerification(
        verifyToken: String,
        userId: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val client = okhttp3.OkHttpClient()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = org.json.JSONObject().apply {
                    put("token", verifyToken)
                    put("userId", userId)
                }
                val request = okhttp3.Request.Builder()
                    .url(NeuvoteManager.getNeuvoteServerUrl() + Constants.ENDPOINT_IPROOV_VALIDATE_VERIFICATION)
                    .post(okhttp3.RequestBody.create("application/json".toMediaType(), payload.toString()))
                    .build()
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body!!.string()
                    if (!response.isSuccessful) {
                        val errorMsg = try {
                            val json = org.json.JSONObject(responseBody)
                            json.optString("error", json.optString("message", "Verification validation failed"))
                        } catch (e: Exception) {
                            "Verification validation failed"
                        }
                        withContext(Dispatchers.Main) {
                            onError(errorMsg)
                        }
                        return@use
                    }
                    withContext(Dispatchers.Main) {
                        onResult(responseBody)
                    }
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    onError(ex.localizedMessage ?: "Unknown error")
                }
            }
        }
    }
}
