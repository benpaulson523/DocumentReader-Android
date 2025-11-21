package com.regula.backend.processing

import android.app.Activity
import android.app.ProgressDialog
import android.widget.TextView
import android.view.View
import android.util.Log
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType

class NeuvoteManager private constructor(
    var context: Context,
    var showDialog: (String?) -> Unit,
    var dismissDialog: () -> Unit
) {
    private var firstName: String? = null
    private var middleName: String? = null
    private var surname: String? = null
    private var dateOfBirth: String? = null
    private var sex: String? = null
    private var registrationCode: String? = null
    private var email: String? = null
    private var phone: String? = null
    private var streetAddress: String? = null
    private var unitNumber: String? = null
    private var city: String? = null
    private var jurisdiction: String? = null
    private var postalCode: String? = null
    private var readChip: Boolean = false
    private var verifyMethod: String? = null

    companion object {
        private const val TAG = "NeuvoteManager"
        @Volatile
        private var instance: NeuvoteManager? = null

        @JvmStatic
        fun getInstance(
            context: Context,
            showDialog: (String?) -> Unit,
            dismissDialog: () -> Unit
        ): NeuvoteManager {
            return if (instance == null) {
                synchronized(this) {
                    instance ?: NeuvoteManager(context, showDialog, dismissDialog).also { instance = it }
                }
            } else {
                instance!!.context = context
                instance!!.showDialog = showDialog
                instance!!.dismissDialog = dismissDialog
                instance!!
            }
        }

        fun getNeuvoteServerUrl(): String {
            val address = SettingsManager.getServerAddress()
            val port = SettingsManager.getServerPort()
            return "http://$address:$port"
        }
    }

    fun sendVerificationEmail(
        context: Context,
        email: String,
        onResponse: (Boolean) -> Unit
    ) {
        val url = getNeuvoteServerUrl() + Constants.ENDPOINT_REGISTRATION_MFA_INITIATE_EMAIL
        val jsonObj = org.json.JSONObject().apply {
            put("email", email)
            put("mnemonicUuid", registrationCode)
        }
        val jsonBody = jsonObj.toString()
        val client = okhttp3.OkHttpClient()
        val requestBody = okhttp3.RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            jsonBody
        )
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                (context as? Activity)?.runOnUiThread {
                    showToast(context, "Failed to send email")
                    onResponse(false)
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                (context as? Activity)?.runOnUiThread {
                    onResponse(response.isSuccessful)
                }
            }
        })
    }

    fun sendVerificationText(
        context: Context,
        phone: String,
        onResponse: (Boolean) -> Unit
    ) {
        val url = getNeuvoteServerUrl() + Constants.ENDPOINT_REGISTRATION_MFA_INITIATE_SMS
        val jsonObj = org.json.JSONObject().apply {
            put("phone", phone)
            put("mnemonicUuid", registrationCode)
        }
        val jsonBody = jsonObj.toString()
        val client = okhttp3.OkHttpClient()
        val requestBody = okhttp3.RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            jsonBody
        )
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                (context as? Activity)?.runOnUiThread {
                    showToast(context, "Failed to send text")
                    onResponse(false)
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                (context as? Activity)?.runOnUiThread {
                    onResponse(response.isSuccessful)
                }
            }
        })
    }

    fun completeRegistration(context: Context, verificationCode: String, iProovManager: IProovManager?, onFinalResult: ((Boolean, String?) -> Unit)? = null) {
        showDialog("Registering...")
        val electionOptIns = "confirmEligibleVoteInPSB,confirmEligibleVoteInCSLF"
        val addressJson = org.json.JSONObject().apply {
            put("streetAddress", streetAddress ?: "")
            put("city", city ?: "")
            put("province", jurisdiction ?: "")
            put("postalCode", postalCode ?: "")
            put("unitNumberPOBox", unitNumber)
        }
        val jsonObj = org.json.JSONObject().apply {
            put("votingChannel", "online")
            put("firstName", firstName ?: "")
            put("middleName", middleName ?: "")
            put("lastName", surname ?: "")
            put("dateOfBirth", dateOfBirth ?: "")
            put("email", email ?: "")
            put("phone", phone ?: "")
            put("mnemonicUuid", registrationCode ?: "")
            put("address", addressJson)
            put("electionOptIns", electionOptIns)
            put("verificationCode", verificationCode)
        }
        Log.d(TAG, "validateVerification data: $jsonObj")

        var url = getNeuvoteServerUrl()
        if (verifyMethod == "email") {
            url += Constants.ENDPOINT_REGISTRATION_MFA_VERIFY_EMAIL
        } else {
            url += Constants.ENDPOINT_REGISTRATION_MFA_VERIFY_SMS
        }

        val jsonBody = jsonObj.toString()

        val client = okhttp3.OkHttpClient()
        val requestBody = okhttp3.RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            jsonBody
        )
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                (context as? Activity)?.runOnUiThread {
                    dismissDialog();
                    showToast(context, "Network error: Registration failed")
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                var voterIdentifier = ""
                var parseError: String? = null
                var responseBody: String? = null
                responseBody = response.body?.string()
                Log.d(TAG, "Server response from /mfa/verify/email_or_sms: $responseBody")
                var errorMsg: String? = null
                var invalidCode = false
                if (response.isSuccessful) {
                    try {
                        val json = org.json.JSONObject(responseBody ?: "")
                        val data = json.optJSONObject("data")
                        voterIdentifier = data?.optString("voterIdentifier", "") ?: ""
                        Log.d(TAG, "voterIdentifier: $voterIdentifier")
                        if (voterIdentifier.isEmpty()) {
                            errorMsg = "No voter identifier returned."
                        }
                    } catch (e: Exception) {
                        parseError = e.message
                        errorMsg = "Failed to parse voterIdentifier: ${e.message}"
                        Log.e(TAG, errorMsg ?: "Parse error")
                    }
                } else {
                    // Try to parse error message from server
                    try {
                        val json = org.json.JSONObject(responseBody ?: "")
                        // Check for error object with code/message
                        if (json.has("error")) {
                            val errorObj = json.optJSONObject("error")
                            val code = errorObj?.optInt("code", -1) ?: -1
                            val message = errorObj?.optString("message", "") ?: ""
                            if (code == 400 && message == "Invalid code") {
                                invalidCode = true
                            } else {
                                errorMsg = message
                            }
                        } else {
                            errorMsg = json.optString("error")
                            if (errorMsg.isNullOrEmpty()) {
                                errorMsg = json.optString("message")
                            }
                        }
                    } catch (e: Exception) {
                        errorMsg = "Unknown server error"
                    }
                }
                (context as? Activity)?.runOnUiThread {
                    if (invalidCode) {
                        dismissDialog();
                        showToast(context, "Error: invalid code")
                        onFinalResult?.invoke(false, "Error: invalid code")
                    } else if (response.isSuccessful && errorMsg == null) {
                        validateIProovVerification(context, voterIdentifier, iProovManager) { success ->
                            onFinalResult?.invoke(success, null)
                        }
                    } else {
                        dismissDialog();
                        val failMsg = "Registration failed: ${errorMsg ?: "Unknown error"}"
                        showToast(context, failMsg)
                        onFinalResult?.invoke(false, failMsg)
                    }
                }
            }
        })
    }

    fun validateIProovVerification(context: Context, voterIdentifier: String, iProovManager: IProovManager?, onFinalResult: ((Boolean) -> Unit)? = null) {
        if (iProovManager != null) {
            val verifyToken = iProovManager.getLastIProovToken() ?: ""
            Log.d(TAG, "Sending verifyToken $verifyToken to validate-verification")
            iProovManager.validateVerification(
                verifyToken,
                registrationCode ?: "",
                onResult = { verificationResponse ->
                    Log.d(TAG, "Backend /iproov/validate-verification completed")
                    var faceImage: String? = null
                    var errorMsg: String? = null
                    try {
                        val json = org.json.JSONObject(verificationResponse ?: "")
                        val passed = json.optBoolean("passed", false)
                        val frameAvailable = json.optBoolean("frame_available", false)
                        faceImage = json.optString("frame")
                        val assuranceType = json.optString("assurance_type")
                        val signals = json.optJSONObject("signals")
                        val token = json.optString("token")
                        val type = json.optString("type")
                        // Optionally log signals for diagnostics
                        Log.d(TAG, "iProov signals: $signals, assuranceType: $assuranceType, type: $type")
                        if (!passed) {
                            errorMsg = "Verification failed: passed=false"
                        } else if (!frameAvailable) {
                            errorMsg = "Verification failed: frame not available"
                        } else if (faceImage.isNullOrEmpty()) {
                            errorMsg = "No face image returned from iProov."
                        }
                        Log.d(TAG, "Successfully parsed frame image")
                    } catch (e: Exception) {
                        errorMsg = "Failed to parse iProov response: ${e.message}"
                        Log.e(TAG, errorMsg ?: "Parse error")
                    }
                    if (errorMsg != null) {
                        (context as? Activity)?.runOnUiThread {
                            dismissDialog();
                            showToast(context, "iProov verification failed: $errorMsg")
                        }
                        return@validateVerification
                    }
                    registerWithAbis(
                        context = context,
                        registrationCode = registrationCode ?: "",
                        firstName = firstName,
                        lastName = surname,
                        dateOfBirth = dateOfBirth,
                        sex = sex,
                        faceImage = faceImage,
                        voterIdentifier = voterIdentifier,
                        onFinalResult = onFinalResult
                    )
                },
                onError = { errorMsg ->
                    dismissDialog();
                    Log.e(TAG, "Backend validate-verification error: $errorMsg")
                    showToast(context, "iProov verification failed: $errorMsg")
                }
            )
        } else {
            dismissDialog();
            showToast(context, "Registration failed: iProovManager not available")
        }
    }
    
    fun registerWithAbis(
        context: Context,
        registrationCode: String,
        firstName: String?,
        lastName: String?,
        dateOfBirth: String?,
        sex: String?,
        faceImage: String?,
        voterIdentifier: String,
        onFinalResult: ((Boolean) -> Unit)? = null
    ) {
        val payload = org.json.JSONObject().apply {
            put("userId", registrationCode)
            put("firstName", firstName ?: "")
            put("lastName", lastName ?: "")
            put("dateOfBirth", dateOfBirth ?: "")
            put("sex", sex ?: "")
            put("faceImage", faceImage ?: "")
        }
        val client = okhttp3.OkHttpClient()
        val requestBody = okhttp3.RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            payload.toString()
        )
        val request = okhttp3.Request.Builder()
            .url(NeuvoteManager.getNeuvoteServerUrl() + Constants.ENDPOINT_ABIS_ENROLL_VOTER)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                (context as? Activity)?.runOnUiThread {
                    dismissDialog();
                    showToast(context, "Network error: ABIS registration failed")
                    onFinalResult?.invoke(false)
                }
                Log.e(TAG, "Failed to register with ABIS: " + e.message)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Server response from ABIS enroll: $responseBody")
                var errorMsg: String? = null
                var serverResult: String? = null
                try {
                    val json = org.json.JSONObject(responseBody ?: "")
                    serverResult = json.optString("serverResult")
                    if (serverResult != "Success") {
                        errorMsg = serverResult.ifEmpty { json.optString("error") }
                        if (errorMsg.isNullOrEmpty()) {
                            errorMsg = json.optString("message")
                        }
                    }
                } catch (e: Exception) {
                    errorMsg = "Failed to parse ABIS response: ${e.message}"
                }
                (context as? Activity)?.runOnUiThread {
                    if (response.isSuccessful && (errorMsg == null || serverResult == "Success")) {
                        updateAbisID(context, voterIdentifier, registrationCode, onFinalResult)
                    } else {
                        dismissDialog();
                        showToast(context, "ABIS registration failed: ${errorMsg ?: "Unknown error"}")
                        onFinalResult?.invoke(false)
                    }
                }
            }
        })
    }

    fun updateAbisID(context: Context, voterIdentifier: String, registrationCode: String, onFinalResult: ((Boolean) -> Unit)? = null) {
        val url = getNeuvoteServerUrl() + "/voters/" + voterIdentifier + "/abis-id"
        val jsonObj = org.json.JSONObject().apply {
            put("abisID", registrationCode)
        }
        val jsonBody = jsonObj.toString()
        val client = okhttp3.OkHttpClient()
        val requestBody = okhttp3.RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            jsonBody
        )
        val request = okhttp3.Request.Builder()
            .url(url)
            .put(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                (context as? Activity)?.runOnUiThread {
                    dismissDialog();
                    showToast(context, "Setting ABIS ID failed")
                }
                Log.e(TAG, "Failed to update ABIS ID: " + e.message)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Server response from /$voterIdentifier/abis-id: $responseBody")
                (context as? Activity)?.runOnUiThread {
                    if (response.isSuccessful) {
                        updateAbisFacialScanFlag(context, registrationCode) { success ->
                            onFinalResult?.invoke(success)
                            dismissDialog();
                            if (!success) {
                                showToast(context, context.getString(R.string.registration_failed))
                            }
                        }
                    } else {
                        dismissDialog();
                        showToast(context, "Setting ABIS ID failed")
                    }
                }
            }
        })
    }

    fun updateAbisFacialScanFlag(context: Context, abisID: String, onUiUpdate: (Boolean) -> Unit) {
        val url = getNeuvoteServerUrl() + "/voters/abis/" + abisID + "/face-scan-flag"
        val jsonObj = org.json.JSONObject().apply {
            put("biometricsFacialScanCollected", true)
        }
        val jsonBody = jsonObj.toString()
        val client = okhttp3.OkHttpClient()
        val requestBody = okhttp3.RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            jsonBody
        )
        val request = okhttp3.Request.Builder()
            .url(url)
            .put(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                (context as? Activity)?.runOnUiThread {
                    onUiUpdate(false)
                }
                Log.e(TAG, "Failed to update facial scan flag: " + e.message)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Server response from /voters/abis/$abisID/face-scan-flag: $responseBody")
                (context as? Activity)?.runOnUiThread {
                    onUiUpdate(response.isSuccessful)
                }
            }
        })
    }
    
    fun setFirstName(value: String?) {
        firstName = value
    }
    fun getFirstName(): String? {
        return firstName
    }
    
    fun setMiddleName(value: String?) {
        middleName = value
    }
    fun getMiddleName(): String? {
        return middleName
    }

    fun setSurname(value: String?) {
        surname = value
    }
    fun getSurname(): String? {
        return surname
    }

    fun getFullName(): String {
        if (middleName != "") {
            return firstName + " " + middleName + " " + surname
        }
        return firstName + " " + surname
    }

    fun setDateOfBirth(value: String?) {
        dateOfBirth = value
    }
    fun getDateOfBirth(): String? {
        return dateOfBirth
    }

    fun setSex(value: String?) {
        sex = value
    }
    fun getSex(): String? {
        return sex
    }

    fun setRegistrationCode(value: String?) {
        registrationCode = value
    }
    fun getRegistrationCode(): String? {
        return registrationCode
    }

    fun setEmail(value: String?) {
        email = value
    }
    fun getEmail(): String? {
        return email
    }

    fun setPhone(value: String?) {
        phone = value
    }
    fun getPhone(): String? {
        return phone
    }

    fun setCity(value: String?) {
        city = value
    }
    fun getCity(): String? {
        return city
    }

    fun setJurisdiction(value: String?) {
        jurisdiction = value
    }
    fun getJurisdiction(): String? {
        return jurisdiction
    }

    fun setStreetAddress(value: String?) {
        streetAddress = value
    }
    fun getStreetAddress(): String? {
        return streetAddress
    }

    fun setUnitNumber(value: String?) {
        unitNumber = value
    }
    fun getUnitNumber(): String? {
        return unitNumber
    }

    fun setPostalCode(value: String?) {
        postalCode = value
    }
    fun getPostalCode(): String? {
        return postalCode
    }

    fun getFullAddress(): String {
        if ((unitNumber != null) && (unitNumber != "")) {
            return streetAddress + " #" + unitNumber + "\n" + city + ", " + jurisdiction + " " + postalCode
        }
        return streetAddress + "\n" + city + ", " + jurisdiction + " " + postalCode
    }

    fun hasAddress(): Boolean {
        return (streetAddress != null) && (streetAddress != "") &&
                (jurisdiction != null) && (jurisdiction != "") &&
                (postalCode != null) && (postalCode != "") &&
                (city != null) && (city != "")
    }

    fun setReadChip(value: Boolean) {
        readChip = value
    }
    fun getReadChip(): Boolean {
        return readChip
    }

    fun setVerifyMethod(value: String) {
        verifyMethod = value
    }
    fun getVerifyMethod(): String? {
        return verifyMethod
    }
}
