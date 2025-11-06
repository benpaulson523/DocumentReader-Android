

package com.regula.backend.processing

import android.widget.TextView
import android.view.View
import android.util.Log
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType

object Neuvote {
    private const val TAG = "Neuvote"
    fun getNeuvoteServerUrl(): String {
        val address = SettingsManager.getServerAddress()
        val port = SettingsManager.getServerPort()
        return "http://$address:$port"
    }

    fun sendVerificationEmail(context: Context, email: String, mnemonicUuid: String, iProovManager: IProovManager?) {
        (context as? android.app.Activity)?.runOnUiThread {
            val registerBtn = (context as android.app.Activity).findViewById<View>(R.id.registerBtn)
            registerBtn?.setOnClickListener {
                completeRegistration(context, iProovManager)
            }
        }
        val url = Neuvote.getNeuvoteServerUrl() + "/registration/mfa/initiate/email"
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
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Failed to send verification email", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                (context as? android.app.Activity)?.runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Verification email sent!", Toast.LENGTH_LONG).show()
                        // Show email code input and register button, hide verifyEmailBtn
                        val emailCodeLabel = (context as android.app.Activity).findViewById<TextView>(R.id.emailCodeLabel)
                        val emailCodeInput = (context as android.app.Activity).findViewById<EditText>(R.id.emailCodeInput)
                        val registerBtn = (context as android.app.Activity).findViewById<View>(R.id.registerBtn)
                        val verifyEmailBtn = (context as android.app.Activity).findViewById<View>(R.id.verifyEmailBtn)
                        emailCodeLabel?.visibility = View.VISIBLE
                        emailCodeInput?.visibility = View.VISIBLE
                        registerBtn?.visibility = View.VISIBLE
                        verifyEmailBtn?.visibility = View.GONE
                    } else {
                        Toast.makeText(context, "Error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
    
    fun completeRegistration(context: Context, iProovManager: IProovManager?) {
        val email = (context as android.app.Activity).findViewById<EditText>(R.id.emailInput)?.text.toString()
        val mnemonicUuid = (context as android.app.Activity).findViewById<EditText>(R.id.mnemonicInput)?.text.toString()
        val verificationCode = (context as android.app.Activity).findViewById<EditText>(R.id.emailCodeInput)?.text.toString()
        val phone = (context as android.app.Activity).findViewById<EditText>(R.id.phoneInput)?.text.toString()
        val city = (context as android.app.Activity).findViewById<EditText>(R.id.cityInput)?.text.toString()
        val province = (context as android.app.Activity).findViewById<EditText>(R.id.provinceInput)?.text.toString()

        val nameText = (context as android.app.Activity).findViewById<TextView>(R.id.nameTv)?.text.toString().removePrefix("Name: ").trim()
        val nameParts = nameText.split(" ")
        val firstName = nameParts.getOrNull(0) ?: ""
        val middleName = if (nameParts.size > 1) nameParts.subList(1, nameParts.size).joinToString(" ") else ""
        val lastName = (context as android.app.Activity).findViewById<TextView>(R.id.surnameTv)?.text.toString().removePrefix("Surname:")
        val dateOfBirth = (context as android.app.Activity).findViewById<TextView>(R.id.dobTv)?.text.toString().removePrefix("Date of Birth: ")
        val streetAddress = (context as android.app.Activity).findViewById<EditText>(R.id.streetInput)?.text.toString()
        val postalCode = (context as android.app.Activity).findViewById<EditText>(R.id.postalInput)?.text.toString()
        val sex = (context as android.app.Activity).findViewById<TextView>(R.id.sexTv)?.text.toString().removePrefix("Sex: ").trim()
        val unitNumberPOBox = "" // Add logic if you have this field
        val electionOptIns = "confirmEligibleVoteInPSB,confirmEligibleVoteInCSLF" // Example value

        val url = getNeuvoteServerUrl() + "/registration/mfa/verify/email"
        val jsonBody = """
            {
                "votingChannel": "online",
                "firstName": "$firstName",
                "middleName": "$middleName",
                "lastName": "$lastName",
                "dateOfBirth": "$dateOfBirth",
                "email": "$email",
                "phone": "$phone",
                "address": {
                    "streetAddress": "$streetAddress",
                    "city": "$city",
                    "province": "$province",
                    "postalCode": "$postalCode",
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
                (context as? android.app.Activity)?.runOnUiThread {
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
                (context as? android.app.Activity)?.runOnUiThread {
                    if (response.isSuccessful) {
                        if (parseError != null) {
                            Toast.makeText(context, "Registration error: $parseError", Toast.LENGTH_LONG).show()
                            return@runOnUiThread
                        }
                        if (iProovManager != null) {
                            val verifyToken = iProovManager.getLastIProovToken() ?: ""
                            Log.d(TAG, "Sending verifyToken $verifyToken to validate-verification");
                            iProovManager.validateVerification(
                                verifyToken,
                                mnemonicUuid,
                                firstName,
                                lastName,
                                dateOfBirth,
                                sex,
                                onResult = { responseBody ->
                                    Log.d(TAG, "Backend /iproov/validate-verification response: $responseBody")
                                    updateAbisID(context, voterIdentifier, mnemonicUuid)
                                },
                                onError = { errorMsg ->
                                    Log.e(TAG, "Backend validate-verification error: $errorMsg")
                                    Toast.makeText(context, "Registration error!", Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            Toast.makeText(context, "iProovManager not available", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Registration error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    fun updateAbisID(context: Context, voterIdentifier: String, mnemonicUuid: String) {
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
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Failed to update ABIS ID", Toast.LENGTH_LONG).show()
                }
                Log.e(TAG, "Failed to update ABIS ID: " + e.message)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Server response from /$voterIdentifier/abis-id: $responseBody")
                (context as? android.app.Activity)?.runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "ABIS ID updated!", Toast.LENGTH_LONG).show()
                        updateAbisFacialScanFlag(context, mnemonicUuid);
                    } else {
                        Toast.makeText(context, "Failed to update ABIS ID", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    fun updateAbisFacialScanFlag(context: Context, abisID: String) {
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
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Failed to update facial scan flag", Toast.LENGTH_LONG).show()
                }
                Log.e(TAG, "Failed to update facial scan flag: " + e.message)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Server response from /voters/abis/$abisID/face-scan-flag: $responseBody")
                (context as? android.app.Activity)?.runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Facial scan flag updated!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to update facial scan flag", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}
