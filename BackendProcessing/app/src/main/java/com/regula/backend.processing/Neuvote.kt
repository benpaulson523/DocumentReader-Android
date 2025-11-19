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
    private var biometricsId: String? = null
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
            put("mnemonicUuid", biometricsId)
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
                    Toast.makeText(context, "Failed to send email", Toast.LENGTH_LONG).show()
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
            put("mnemonicUuid", biometricsId)
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
                    Toast.makeText(context, "Failed to send text", Toast.LENGTH_LONG).show()
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

    fun completeRegistration(context: Context, verificationCode: String, iProovManager: IProovManager?, onFinalResult: ((Boolean) -> Unit)? = null) {
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
            put("mnemonicUuid", biometricsId ?: "")
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
                    Toast.makeText(context, "Registration failed", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                var voterIdentifier = ""
                var parseError: String? = null
                var responseBody: String?
                if (response.isSuccessful) {
                    // Parse voterIdentifier from response (off main thread)
                    responseBody = response.body?.string()
                    Log.d(TAG, "Server response from /mfa/verify/email_or_sms: $responseBody")
                    try {
                        val json = org.json.JSONObject(responseBody ?: "")
                        val data = json.optJSONObject("data")
                        voterIdentifier = data?.optString("voterIdentifier", "") ?: ""
                        Log.d(TAG, "voterIdentifier: $voterIdentifier")
                    } catch (e: Exception) {
                        parseError = e.message
                        Log.e(TAG, "Failed to parse voterIdentifier: " + e.message)
                    }
                }
                (context as? Activity)?.runOnUiThread {
                    if (response.isSuccessful) {
                        if (parseError != null) {
                            dismissDialog();
                            Toast.makeText(context, "Registration failed", Toast.LENGTH_LONG).show()
                            return@runOnUiThread
                        }
                        validateIProovVerification(context, voterIdentifier, iProovManager, onFinalResult)
                    } else {
                        dismissDialog();
                        Toast.makeText(context, "Registration failed", Toast.LENGTH_LONG).show()
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
                biometricsId ?: "",
                onResult = { verificationResponse ->
                    Log.d(TAG, "Backend /iproov/validate-verification completed")
                    var faceImage: String? = null
                    try {
                        val json = org.json.JSONObject(verificationResponse ?: "")
                        faceImage = json.optString("frame")
                        Log.d(TAG, "Successfully parsed frame image")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse frame image: " + e.message)
                    }
                    registerWithAbis(
                        context = context,
                        biometricsId = biometricsId ?: "",
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
                    Toast.makeText(context, "Registration failed", Toast.LENGTH_LONG).show()
                }
            )
        } else {
            dismissDialog();
            Toast.makeText(context, "Registration failed", Toast.LENGTH_LONG).show()
        }
    }
    
    fun registerWithAbis(
        context: Context,
        biometricsId: String,
        firstName: String?,
        lastName: String?,
        dateOfBirth: String?,
        sex: String?,
        faceImage: String?,
        voterIdentifier: String,
        onFinalResult: ((Boolean) -> Unit)? = null
    ) {
        val payload = org.json.JSONObject().apply {
            put("userId", biometricsId)
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
                    Toast.makeText(context, "ABIS registration failed", Toast.LENGTH_LONG).show()
                    onFinalResult?.invoke(false)
                }
                Log.e(TAG, "Failed to register with ABIS: " + e.message)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Server response from ABIS enroll: $responseBody")
                (context as? Activity)?.runOnUiThread {
                    if (response.isSuccessful) {
                        updateAbisID(context, voterIdentifier, biometricsId, onFinalResult)
                    } else {
                        dismissDialog();
                        Toast.makeText(context, "ABIS registration failed", Toast.LENGTH_LONG).show()
                        onFinalResult?.invoke(false)
                    }
                }
            }
        })
    }

    fun updateAbisID(context: Context, voterIdentifier: String, biometricsId: String, onFinalResult: ((Boolean) -> Unit)? = null) {
        val url = getNeuvoteServerUrl() + "/voters/" + voterIdentifier + "/abis-id"
        val jsonObj = org.json.JSONObject().apply {
            put("abisID", biometricsId)
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
                    Toast.makeText(context, "Setting ABIS ID failed", Toast.LENGTH_LONG).show()
                }
                Log.e(TAG, "Failed to update ABIS ID: " + e.message)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Server response from /$voterIdentifier/abis-id: $responseBody")
                (context as? Activity)?.runOnUiThread {
                    if (response.isSuccessful) {
                        updateAbisFacialScanFlag(context, biometricsId) { success ->
                            onFinalResult?.invoke(success)
                            dismissDialog();
                            if (!success) {
                                Toast.makeText(context, context.getString(R.string.registration_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        dismissDialog();
                        Toast.makeText(context, "Setting ABIS ID failed", Toast.LENGTH_LONG).show()
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

    fun setBiometricsId(value: String?) {
        biometricsId = value
    }
    fun getBiometricsId(): String? {
        return biometricsId
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
