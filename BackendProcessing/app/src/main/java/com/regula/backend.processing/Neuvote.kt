
package com.regula.backend.processing

import android.widget.TextView
import android.view.View
import android.util.Log
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType

object Neuvote {
    fun getNeuvoteServerUrl(): String {
        val address = SettingsManager.getServerAddress()
        val port = SettingsManager.getServerPort()
        return "http://$address:$port"
    }

    fun sendVerificationEmail(context: Context, email: String, mnemonicUuid: String) {
        (context as? android.app.Activity)?.runOnUiThread {
            val registerBtn = (context as android.app.Activity).findViewById<View>(R.id.registerBtn)
            registerBtn?.setOnClickListener {
                completeRegistration(context)
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
    
    fun completeRegistration(context: Context) {
        val email = (context as android.app.Activity).findViewById<EditText>(R.id.emailInput)?.text.toString()
        val mnemonicUuid = (context as android.app.Activity).findViewById<EditText>(R.id.mnemonicInput)?.text.toString()
        val verificationCode = (context as android.app.Activity).findViewById<EditText>(R.id.emailCodeInput)?.text.toString()
        val phone = (context as android.app.Activity).findViewById<EditText>(R.id.phoneInput)?.text.toString()
        val street = (context as android.app.Activity).findViewById<EditText>(R.id.streetInput)?.text.toString()
        val city = (context as android.app.Activity).findViewById<EditText>(R.id.cityInput)?.text.toString()
        val province = (context as android.app.Activity).findViewById<EditText>(R.id.provinceInput)?.text.toString()
        val postal = (context as android.app.Activity).findViewById<EditText>(R.id.postalInput)?.text.toString()

        val firstName = (context as android.app.Activity).findViewById<TextView>(R.id.nameTv)?.text.toString().removePrefix("Name: ")
        val lastName = (context as android.app.Activity).findViewById<TextView>(R.id.surnameTv)?.text.toString().removePrefix("Surname:")
        val dateOfBirth = (context as android.app.Activity).findViewById<TextView>(R.id.dobTv)?.text.toString().removePrefix("Date of Birth: ")
        val streetAddress = (context as android.app.Activity).findViewById<EditText>(R.id.streetInput)?.text.toString()
        val postalCode = (context as android.app.Activity).findViewById<EditText>(R.id.postalInput)?.text.toString()
        val unitNumberPOBox = "" // Add logic if you have this field
        val middleName = "" // Add logic if you have this field
        val knownIdNumber = "" // Add logic if you have this field
        val electionOptIns = "confirmEligibleVoteInPSB,confirmEligibleVoteInCSLF" // Example value

        val url = Neuvote.getNeuvoteServerUrl() + "/registration/mfa/verify/email"
        val jsonBody = """
            {
                "votingChannel": "online",
                "firstName": "$firstName",
                "middleName": "$middleName",
                "lastName": "$lastName",
                "dateOfBirth": "$dateOfBirth",
                "email": "$email",
                "phone": "$phone",
                "knownIdNumber": "$knownIdNumber",
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
                (context as? android.app.Activity)?.runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Registration successful!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Registration error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}
