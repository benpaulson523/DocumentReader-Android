
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
        Log.d("Neuvote", "Register button clicked")
    }
}
