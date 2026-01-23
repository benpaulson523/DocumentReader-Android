package com.regula.backend.processing

import android.app.Activity
import android.widget.TextView
import android.view.View
import android.util.Log
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.graphics.Bitmap

class NeuvoteManager private constructor(
        // ...existing code...
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
    private var registrationQRCode: Bitmap? = null
    private var email: String? = null
    private var phone: String? = null
    private var streetAddress: String? = null
    private var unitNumber: String? = null
    private var city: String? = null
    private var jurisdiction: String? = null
    private var postalCode: String? = null
    private var country: String? = null
    private var readChip: Boolean = false
    private var verifyMethod: String? = null
    private var officialDocumentScan: Bitmap? = null
    private var photo: Bitmap? = null
    private var supportDocumentScan: Bitmap? = null

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

            if (port != "") {
                return "$address:$port"
            }
            return "$address"
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
        }
        val jsonBody = jsonObj.toString()
        val client = okhttp3.OkHttpClient()
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                (context as? Activity)?.runOnUiThread {
                    showToast(context, context.getString(R.string.verification_email_failed))
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
        }
        val jsonBody = jsonObj.toString()
        val client = okhttp3.OkHttpClient()
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                (context as? Activity)?.runOnUiThread {
                    showToast(context, context.getString(R.string.verification_text_failed))
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
        showDialog(context.getString(R.string.registering))

        var url = getNeuvoteServerUrl()
        if (verifyMethod == "email") {
            url += Constants.ENDPOINT_REGISTRATION_MFA_VERIFY_EMAIL
        } else {
            url += Constants.ENDPOINT_REGISTRATION_MFA_VERIFY_SMS
        }

        val client = okhttp3.OkHttpClient()
        val multipartBuilder = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)

        // Add all fields as separate form fields
        multipartBuilder.addFormDataPart("votingChannel", "online")
        multipartBuilder.addFormDataPart("firstName", firstName ?: "")
        multipartBuilder.addFormDataPart("middleName", middleName ?: "")
        multipartBuilder.addFormDataPart("lastName", surname ?: "")
        multipartBuilder.addFormDataPart("dateOfBirth", dateOfBirth ?: "")
        multipartBuilder.addFormDataPart("sex", sex ?: "")
        multipartBuilder.addFormDataPart("email", email ?: "")
        multipartBuilder.addFormDataPart("phone", phone ?: "")
        multipartBuilder.addFormDataPart("registrationCode", registrationCode ?: "")
        multipartBuilder.addFormDataPart("verificationCode", verificationCode)
        // Address fields
        multipartBuilder.addFormDataPart("address[streetAddress]", streetAddress ?: "")
        multipartBuilder.addFormDataPart("address[city]", city ?: "")
        multipartBuilder.addFormDataPart("address[province]", jurisdiction ?: "")
        multipartBuilder.addFormDataPart("address[postalCode]", postalCode ?: "")
        multipartBuilder.addFormDataPart("address[unitNumberPOBox]", unitNumber ?: "")
        multipartBuilder.addFormDataPart("address[country]", country ?: "")

        // Add officialDocumentScan as photoIDs if available
        officialDocumentScan?.let { bitmap ->
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            val photoBytes = stream.toByteArray()
            multipartBuilder.addFormDataPart(
                "photoIDs",
                "officialDocument.jpg",
                photoBytes.toRequestBody("image/jpeg".toMediaType())
            )
        }
        // Add supportDocumentScan as photoIDs if available
        supportDocumentScan?.let { bitmap ->
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            val photoBytes = stream.toByteArray()
            multipartBuilder.addFormDataPart(
                "photoIDs",
                "supportDocument.jpg",
                photoBytes.toRequestBody("image/jpeg".toMediaType())
            )
        }
        val requestBody = multipartBuilder.build()
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                (context as? Activity)?.runOnUiThread {
                    dismissDialog();
                    showToast(context, context.getString(R.string.registration_failed))
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                var voterIdentifier = ""
                val responseBody = response.body?.string()
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
                        val parseErrMsg = "Failed to parse voterIdentifier: ${e.message}"
                        errorMsg = parseErrMsg
                        Log.e(TAG, parseErrMsg)
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
                        showToast(context, context.getString(R.string.error) + ": " + context.getString(R.string.verification_failed_msg))
                        onFinalResult?.invoke(false, context.getString(R.string.error) + ": " + context.getString(R.string.verification_failed_msg))
                    } else if (response.isSuccessful && errorMsg == null) {
                        validateIProovVerification(context, voterIdentifier, iProovManager) { success ->
                            onFinalResult?.invoke(success, null)
                        }
                    } else {
                        dismissDialog();
                        val failMsg = context.getString(R.string.registration_failed) + ": " + errorMsg!!
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
                        val json = org.json.JSONObject(verificationResponse)
                        val passed = json.optBoolean("passed", false)
                        val frameAvailable = json.optBoolean("frame_available", false)
                        faceImage = json.optString("frame")
                        val assuranceType = json.optString("assurance_type")
                        val signals = json.optJSONObject("signals")
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
                        Log.e(TAG, errorMsg)
                    }
                    if (errorMsg != null) {
                        (context as? Activity)?.runOnUiThread {
                            dismissDialog();
                            showToast(context, context.getString(R.string.verification_failed_msg) + ": " + errorMsg)
                        }
                        return@validateVerification
                    }
                    registerWithAbis(
                        context = context,
                        registrationCode = registrationCode ?: "",
                        firstName = firstName,
                        middleName = middleName,
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
                    showToast(context, context.getString(R.string.verification_failed_msg) + ": " + errorMsg)
                }
            )
        } else {
            dismissDialog();
            showToast(context, context.getString(R.string.registration_failed) + ": " + context.getString(R.string.unknown_error))
        }
    }
    
    fun registerWithAbis(
        context: Context,
        registrationCode: String,
        firstName: String?,
        middleName: String?,
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
            put("middleName", middleName ?: "")
            put("lastName", lastName ?: "")
            put("dateOfBirth", dateOfBirth ?: "")
            put("sex", sex ?: "")
            put("faceImage", faceImage ?: "")
        }
        val client = okhttp3.OkHttpClient()
        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = okhttp3.Request.Builder()
            .url(NeuvoteManager.getNeuvoteServerUrl() + Constants.ENDPOINT_ABIS_ENROLL_VOTER)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                (context as? Activity)?.runOnUiThread {
                    dismissDialog();
                    showToast(context, context.getString(R.string.registration_failed))
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
                        showToast(context, context.getString(R.string.registration_failed) + ": " + errorMsg!!)
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
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = okhttp3.Request.Builder()
            .url(url)
            .put(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                (context as? Activity)?.runOnUiThread {
                    dismissDialog();
                    showToast(context, context.getString(R.string.registration_failed))
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
                        showToast(context, context.getString(R.string.registration_failed))
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
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
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

    fun setRegistrationQRCode(value: Bitmap?) {
        registrationQRCode = value
        Log.i(TAG, "setRegistrationQRCode: " + registrationQRCode)
    }
    fun getRegistrationQRCode(): Bitmap? {
        return registrationQRCode
    }

    fun getRegistrationQRCodeString(): String? {
        // If registrationQRCode is null, return null
        val bmp = registrationQRCode ?: return null
        try {
            val width = bmp.width
            val height = bmp.height
            val intArray = IntArray(width * height)
            bmp.getPixels(intArray, 0, width, 0, 0, width, height)
            val source = com.google.zxing.RGBLuminanceSource(width, height, intArray)
            val bitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
            val result = com.google.zxing.qrcode.QRCodeReader().decode(bitmap)
            return result.text
        } catch (e: Exception) {
            // Could not decode QR code
            return null
        }
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

    fun setCountry(value: String?) {
        country = value
    }
    fun getCountry(): String? {
        return country
    }

    fun getFullAddress(): String {
        if ((unitNumber != null) && (unitNumber != "")) {
            return streetAddress + " #" + unitNumber + "\n" + city + ", " + jurisdiction + " " + postalCode + "\n" + country
        }
        return streetAddress + "\n" + city + ", " + jurisdiction + " " + postalCode + "\n" + country
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

    fun setOfficialDocumentScan(value: Bitmap?) {
        officialDocumentScan = value
    }
    fun getOfficialDocumentScan(): Bitmap? {
        return officialDocumentScan
    }

    fun setPhoto(value: Bitmap?) {
        photo = value
    }
    fun getPhoto(): Bitmap? {
        return photo
    }

    fun setSupportDocumentScan(value: Bitmap?) {
        supportDocumentScan = value
    }
    fun getSupportDocumentScan(): Bitmap? {
        return supportDocumentScan
    }

    /**
     * Reset all stored registration/session data so a fresh session starts next launch.
     */
    fun reset() {
        firstName = null
        middleName = null
        surname = null
        dateOfBirth = null
        sex = null
        registrationCode = null
        email = null
        phone = null
        streetAddress = null
        unitNumber = null
        city = null
        jurisdiction = null
        postalCode = null
        readChip = false
        verifyMethod = null
        officialDocumentScan = null
        photo = null
        supportDocumentScan = null
        country = null
        instance = null
    }
}
