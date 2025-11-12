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
    private var name: String? = null
    private var surname: String? = null
    private var dateOfBirth: String? = null
    private var sex: String? = null
    private var mnemonicUuid: String? = null
    private var email: String? = null
    private var phone: String? = null
    private var streetAddress: String? = null
    private var city: String? = null
    private var province: String? = null
    private var postalCode: String? = null

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
        mnemonicUuid: String,
        onResponse: (Boolean) -> Unit
    ) {
    val url = getNeuvoteServerUrl() + Constants.ENDPOINT_REGISTRATION_MFA_INITIATE_EMAIL
        val jsonBody = """{"email":"$email","mnemonicUuid":"$mnemonicUuid"}"""
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

    fun completeRegistration(context: Context, verificationCode: String, iProovManager: IProovManager?, onFinalResult: ((Boolean) -> Unit)? = null) {
        showDialog("Registering...")
        
        val unitNumberPOBox = "" // TODO
        val electionOptIns = "confirmEligibleVoteInPSB,confirmEligibleVoteInCSLF"

        val postalCodeValue = postalCode ?: ""
        val streetAddressValue = streetAddress ?: ""
        val provinceValue = province ?: ""
        val cityValue = city ?: ""
        val phoneValue = phone ?: ""
        val emailValue = email ?: ""
        val mnemonicUuidValue = mnemonicUuid ?: ""
        val firstName = name ?: ""
        val lastName = surname ?: ""
        val dateOfBirthValue = dateOfBirth ?: ""
        val sexValue = sex ?: ""
        val middleName = "" // TODO

        Log.d(TAG, "validateVerification data: verificationCode=$verificationCode, mnemonicUuid=$mnemonicUuidValue, firstName=$firstName, lastName=$lastName, dateOfBirth=$dateOfBirthValue, sex=$sexValue")
        Log.d(TAG, "validateVerification data: email=$emailValue, phone=$phoneValue, city=$cityValue, province=$provinceValue, streetAddress=$streetAddressValue, postalCode=$postalCode")
        
        val url = getNeuvoteServerUrl() + Constants.ENDPOINT_REGISTRATION_MFA_VERIFY_EMAIL
        val jsonBody = """
            {
                "votingChannel": "online",
                "firstName": "$firstName",
                "middleName": "$middleName",
                "lastName": "$lastName",
                "dateOfBirth": "$dateOfBirthValue",
                "email": "$emailValue",
                "phone": "$phoneValue",
                "mnemonicUuid": "$mnemonicUuid",
                "address": {
                    "streetAddress": "$streetAddressValue",
                    "city": "$cityValue",
                    "province": "$provinceValue",
                    "postalCode": "$postalCodeValue",
                    "unitNumberPOBox": "$unitNumberPOBox"
                },
                "electionOptIns": "$electionOptIns",
                "verificationCode": "$verificationCode"
            }
        """.trimIndent()

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
                var responseBody: String? = null
                if (response.isSuccessful) {
                    // Parse voterIdentifier from response (off main thread)
                    responseBody = response.body?.string()
                    Log.d(TAG, "Server response from /mfa/verify/email: $responseBody")
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
                        if (iProovManager != null) {
                            val verifyToken = iProovManager.getLastIProovToken() ?: ""
                            Log.d(TAG, "Sending verifyToken $verifyToken to validate-verification")
                            iProovManager.validateVerification(
                                verifyToken,
                                mnemonicUuidValue,
                                firstName,
                                lastName,
                                dateOfBirthValue,
                                sexValue,
                                onResult = { _ ->
                                    Log.d(TAG, "Backend /iproov/validate-verification completed")
                                    updateAbisID(context, voterIdentifier, mnemonicUuidValue, onFinalResult)
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
                    } else {
                        dismissDialog();
                        Toast.makeText(context, "Registration failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    fun updateAbisID(context: Context, voterIdentifier: String, mnemonicUuid: String, onFinalResult: ((Boolean) -> Unit)? = null) {
        val url = getNeuvoteServerUrl() + "/voters/" + voterIdentifier + "/abis-id"
        val jsonBody = """{"abisID":"$mnemonicUuid"}"""
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
                        updateAbisFacialScanFlag(context, mnemonicUuid) { success ->
                            onFinalResult?.invoke(success)
                            dismissDialog();
                            if (!success) {
                                Toast.makeText(context, context.getString(R.string.face_scan_flag_failed), Toast.LENGTH_LONG).show()
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
        val jsonBody = """{"biometricsFacialScanCollected":true}"""
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
    
    fun setName(value: String?) {
        name = value
    }
    fun setSurname(value: String?) {
        surname = value
    }
    fun setDateOfBirth(value: String?) {
        dateOfBirth = value
    }
    fun setSex(value: String?) {
        sex = value
    }

    fun setMnemonicUuid(value: String?) {
        mnemonicUuid = value
    }
    fun getMnemonicUuid(): String? {
        return mnemonicUuid
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

    fun setProvince(value: String?) {
        province = value
    }
    fun getProvince(): String? {
        return province
    }

    fun setStreetAddress(value: String?) {
        streetAddress = value
    }
    fun getStreetAddress(): String? {
        return streetAddress
    }

    fun setPostalCode(value: String?) {
        postalCode = value
    }
    fun getPostalCode(): String? {
        return postalCode
    }
}
