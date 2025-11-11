package com.regula.backend.processing

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
        Log.d(TAG, "Opened FacialScanActivity screen")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_facial_scan)

        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )

        // Populate mnemonicInput with NeuvoteManager.mnemonicUuid
        val mnemonicInputView = findViewById<TextView>(R.id.mnemonicInput)
        mnemonicInputView?.text = neuvoteManager.getMnemonicUuid()
        
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
        
        val nextBtn = findViewById<Button>(R.id.nextBtn)
        nextBtn?.setOnClickListener {
            val emailInput = findViewById<EditText>(R.id.emailInput)
            val email = emailInput?.text?.toString()
            neuvoteManager.setEmail(email)
            
            val phoneInput = findViewById<EditText>(R.id.phoneInput)
            val phone = phoneInput?.text?.toString()
            neuvoteManager.setPhone(phone)
            
            val streetAddressInput = findViewById<EditText>(R.id.streetInput)
            val streetAddress = streetAddressInput?.text?.toString()
            neuvoteManager.setStreetAddress(streetAddress)
            
            val cityInput = findViewById<EditText>(R.id.cityInput)
            val city = cityInput?.text?.toString()
            neuvoteManager.setCity(city)
            
            val provinceInput = findViewById<EditText>(R.id.provinceInput)
            val province = provinceInput?.text?.toString()
            neuvoteManager.setProvince(province)
            
            val postalCodeInput = findViewById<EditText>(R.id.postalInput)
            val postalCode = postalCodeInput?.text?.toString()
            neuvoteManager.setPostalCode(postalCode)

            val intent = android.content.Intent(this, com.regula.backend.processing.VerifyEmailActivity::class.java)
            startActivity(intent)
        }
        nextBtn?.isEnabled = false
        
        // Handle back button navigation
        val backBtn = findViewById<Button>(R.id.backBtn)
        backBtn?.setOnClickListener {
            finish()
        }
    }
    
    override fun setContentView(view: View?) {
        super.setContentView(view)
        applyEdgeToEdgeInsets()
    }

    private fun applyEdgeToEdgeInsets() {
        val rootView = window.decorView.findViewWithTag<View>("content")
        if (rootView != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
                val systemBars = androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                val sbInsets = insets.getInsets(systemBars)
                view.setPadding(sbInsets.left, sbInsets.top, sbInsets.right, sbInsets.bottom)
                insets
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

        val labelIds = listOf(R.id.emailLabel, R.id.phoneLabel, R.id.streetLabel, R.id.cityLabel, R.id.provinceLabel, R.id.postalLabel)
        val inputIds = listOf(R.id.emailInput, R.id.phoneInput, R.id.streetInput, R.id.cityInput, R.id.provinceInput, R.id.postalInput)
        for (i in labelIds.indices) {
            findViewById<TextView>(labelIds[i])?.visibility = View.VISIBLE
            findViewById<EditText>(inputIds[i])?.visibility = View.VISIBLE
        }

        for (inputId in inputIds) {
            findViewById<EditText>(inputId)?.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { checkNextButtonEnablement() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        populateDefaultsForExtraFields()
        checkNextButtonEnablement()
    }

    private fun checkNextButtonEnablement() {
        val inputIds = listOf(R.id.emailInput, R.id.phoneInput, R.id.streetInput, R.id.cityInput, R.id.provinceInput, R.id.postalInput)
        val allFilled = inputIds.all { id ->
            val inputView = findViewById<EditText>(id)
            inputView?.visibility == View.VISIBLE && !inputView?.text.isNullOrBlank()
        }
        val nextBtn = findViewById<View>(R.id.nextBtn)
        nextBtn?.isEnabled = if (allFilled) true else false
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
