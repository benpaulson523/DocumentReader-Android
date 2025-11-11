package com.regula.backend.processing

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Button
import android.widget.EditText
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.regula.backend.processing.R

class VerifyEmailActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "VerifyEmailActivity"
    }
    
    private var loadingDialog: AlertDialog? = null
    private lateinit var neuvoteManager: NeuvoteManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened VerifyEmailActivity screen")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )

        // Populate mnemonicInput with value if needed
        val mnemonicInputView = findViewById<TextView>(R.id.mnemonicInput)
        var mnemonicUuid = neuvoteManager.getMnemonicUuid()
        mnemonicInputView?.text = mnemonicUuid

        val backBtn = findViewById<Button>(R.id.backBtn)
        backBtn?.setOnClickListener { finish() }

        var email = neuvoteManager.getEmail()
        neuvoteManager.sendVerificationEmail(
            this,
            email.orEmpty(),
            mnemonicUuid.orEmpty()
        ) { success ->
            if (success) {
                Toast.makeText(this, "Verification email sent", Toast.LENGTH_LONG).show()
                val emailCodeLabel = findViewById<TextView>(R.id.emailCodeLabel)
                val emailCodeInput = findViewById<EditText>(R.id.emailCodeInput)
                val registerBtn = findViewById<View>(R.id.registerBtn)

                emailCodeLabel?.visibility = View.VISIBLE
                emailCodeInput?.visibility = View.VISIBLE
                registerBtn?.visibility = View.GONE
            } else {
                Toast.makeText(this, "Failed to send email", Toast.LENGTH_LONG).show()
            }
        }
        
        val emailCodeInput = findViewById<EditText>(R.id.emailCodeInput)
        if (emailCodeInput != null) {
            emailCodeInput.filters = arrayOf(
                android.text.InputFilter.LengthFilter(6),
                object : android.text.InputFilter {
                    override fun filter(source: CharSequence?, start: Int, end: Int, dest: android.text.Spanned?, dstart: Int, dend: Int): CharSequence? {
                        val result = (dest?.subSequence(0, dstart).toString()) + (source?.subSequence(start, end).toString()) + (dest?.subSequence(dend, dest.length ?: 0).toString())
                        return if (result.length > 6 || !result.matches(Regex("\\d*"))) "" else null
                    }
                }
            )
        }
        
        val registerBtn = findViewById<View>(R.id.registerBtn)
        emailCodeInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val code = s?.toString() ?: ""
                registerBtn?.visibility = if (code.matches(Regex("^\\d{6}$"))) View.VISIBLE else View.GONE
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        registerBtn?.setOnClickListener {
            val code = emailCodeInput?.text?.toString() ?: ""
            neuvoteManager.completeRegistration(this, code, com.regula.backend.processing.IProovManager.getInstanceOrNull())
            emailCodeInput?.isEnabled = false
            registerBtn.isEnabled = false
        }
        registerBtn?.visibility = View.GONE
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
}
