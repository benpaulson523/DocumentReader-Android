package com.regula.backendprocessing

import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.regula.backend.processing.R
import com.regula.backend.processing.IProovManager
import com.regula.backend.processing.NeuvoteManager

class FacialScanActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "FacialScanActivity"
    }

    private var loadingDialog: AlertDialog? = null
    private lateinit var neuvoteManager: NeuvoteManager
    private lateinit var iProovManager: IProovManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_facial_scan)

        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        
        iProovManager = IProovManager.getInstanceOrNull() ?: return
        iProovManager.setShowResultHandler(this::onResult)


        val startFacialScanBtn = findViewById<Button>(R.id.startFacialScanBtn)
        startFacialScanBtn.setOnClickListener {
            if (iProovManager != null) {
                iProovManager.launchFacialScanSession()
                startFacialScanBtn.isEnabled = false
            } else {
                Toast.makeText(this, "Facial scan not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onResult(title: String?, resultMessage: String?) {
        Log.d(TAG, "Verification scan result: title=$title, resultMessage=$resultMessage")

        val startFacialScanBtn = findViewById<Button>(R.id.startFacialScanBtn)
        startFacialScanBtn?.visibility = View.GONE

        if (title == "Success") {
            Toast.makeText(this, "Facial scan matches photo", Toast.LENGTH_LONG).show()
            showExtraFields()
        } else {
            Toast.makeText(this, "Facial scan failed", Toast.LENGTH_LONG).show()
        }
    }

    fun showExtraFields() {
        val startFacialScanBtn = findViewById<Button>(R.id.startFacialScanBtn)
        startFacialScanBtn?.visibility = View.GONE

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
        registerBtn?.visibility = View.GONE

        val labelIds = listOf(R.id.emailLabel, R.id.phoneLabel, R.id.streetLabel, R.id.cityLabel, R.id.provinceLabel, R.id.postalLabel)
        val inputIds = listOf(R.id.emailInput, R.id.phoneInput, R.id.streetInput, R.id.cityInput, R.id.provinceInput, R.id.postalInput)
        for (i in labelIds.indices) {
            findViewById<TextView>(labelIds[i])?.visibility = View.VISIBLE
            findViewById<EditText>(inputIds[i])?.visibility = View.VISIBLE
        }

        val verifyEmailBtn = findViewById<View>(R.id.verifyEmailBtn)
        verifyEmailBtn?.setOnClickListener {
            val emailInput = findViewById<EditText>(R.id.emailInput)
            val email = emailInput?.text?.toString() ?: ""
            val mnemonicInput = findViewById<TextView>(R.id.mnemonicInput)
            val mnemonicUuid = mnemonicInput?.text?.toString() ?: ""
            neuvoteManager.sendVerificationEmail(
                this,
                email,
                mnemonicUuid,
                com.regula.backend.processing.IProovManager.getInstanceOrNull()
            )
        }

        for (inputId in inputIds) {
            findViewById<EditText>(inputId)?.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { checkVerifyEmailButtonVisibility() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        populateDefaultsForExtraFields()
        checkVerifyEmailButtonVisibility()
    }

    private fun checkVerifyEmailButtonVisibility() {
        val inputIds = listOf(R.id.emailInput, R.id.phoneInput, R.id.streetInput, R.id.cityInput, R.id.provinceInput, R.id.postalInput)
        val allFilled = inputIds.all { id ->
            val inputView = findViewById<EditText>(id)
            inputView?.visibility == View.VISIBLE && !inputView?.text.isNullOrBlank()
        }
        val verifyEmailBtn = findViewById<View>(R.id.verifyEmailBtn)
        verifyEmailBtn?.visibility = if (allFilled) View.VISIBLE else View.GONE
    }

    private fun populateDefaultsForExtraFields() {
        //findViewById<EditText>(R.id.emailInput)?.setText("test@example.com")
        findViewById<EditText>(R.id.phoneInput)?.setText("555-123-4567")
        findViewById<EditText>(R.id.streetInput)?.setText("123 Main St")
        findViewById<EditText>(R.id.cityInput)?.setText("Toronto")
        findViewById<EditText>(R.id.provinceInput)?.setText("ON")
        findViewById<EditText>(R.id.postalInput)?.setText("A1A 1A1")
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
