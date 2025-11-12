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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.regula.backend.processing.R
import com.regula.backend.processing.IProovManager
import com.regula.backend.processing.NeuvoteManager
import com.regula.backend.processing.databinding.ActivityFacialScanBinding

class FacialScanActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "FacialScanActivity"
    }

    private var loadingDialog: AlertDialog? = null
    private lateinit var binding: ActivityFacialScanBinding
    private lateinit var neuvoteManager: NeuvoteManager
    private lateinit var iProovManager: IProovManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened FacialScanActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityFacialScanBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )

        // Populate mnemonicInput with NeuvoteManager.mnemonicUuid
        binding.mnemonicInput.text = neuvoteManager.getMnemonicUuid()
        
        iProovManager = IProovManager.getInstanceOrNull() ?: return
        iProovManager.setShowResultHandler(this::onResult)

        binding.startFacialScanBtn.setOnClickListener {
            iProovManager.launchFacialScanSession()
            binding.startFacialScanBtn.isEnabled = false
        }
        
        binding.nextBtn.setOnClickListener {
            neuvoteManager.setEmail(binding.emailInput.text?.toString())
            neuvoteManager.setPhone(binding.phoneInput.text?.toString())
            neuvoteManager.setStreetAddress(binding.streetInput.text?.toString())
            neuvoteManager.setCity(binding.cityInput.text?.toString())
            neuvoteManager.setProvince(binding.provinceInput.text?.toString())
            neuvoteManager.setPostalCode(binding.postalInput.text?.toString())

            NavigationHelper.navigateToVerifyEmail(this)
        }
        binding.nextBtn.isEnabled = false
        
        // Handle back button navigation
        binding.backBtn.setOnClickListener {
            finish()
        }
    }

    private fun onResult(title: String?, resultMessage: String?) {
        Log.d(TAG, "Verification scan result: title=$title, resultMessage=$resultMessage")

        binding.startFacialScanBtn.visibility = View.GONE

        if (title == "Success") {
            Toast.makeText(this, "Facial scan matches photo", Toast.LENGTH_LONG).show()
            showExtraFields()
        } else {
            Toast.makeText(this, "Facial scan failed", Toast.LENGTH_LONG).show()
        }
    }

    fun showExtraFields() {
        binding.startFacialScanBtn.visibility = View.GONE
        binding.emailLabel.visibility = View.VISIBLE
        binding.phoneLabel.visibility = View.VISIBLE
        binding.streetLabel.visibility = View.VISIBLE
        binding.cityLabel.visibility = View.VISIBLE
        binding.provinceLabel.visibility = View.VISIBLE
        binding.postalLabel.visibility = View.VISIBLE

        binding.emailInput.visibility = View.VISIBLE
        binding.phoneInput.visibility = View.VISIBLE
        binding.streetInput.visibility = View.VISIBLE
        binding.cityInput.visibility = View.VISIBLE
        binding.provinceInput.visibility = View.VISIBLE
        binding.postalInput.visibility = View.VISIBLE

        val inputFields = listOf(
            binding.emailInput,
            binding.phoneInput,
            binding.streetInput,
            binding.cityInput,
            binding.provinceInput,
            binding.postalInput
        )
        for (inputField in inputFields) {
            inputField.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { checkNextButtonEnablement() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        populateDefaultsForExtraFields()
        checkNextButtonEnablement()
    }

    private fun checkNextButtonEnablement() {
        val inputFields = listOf(
            binding.emailInput,
            binding.phoneInput,
            binding.streetInput,
            binding.cityInput,
            binding.provinceInput,
            binding.postalInput
        )
        val allFilled = inputFields.all { it.visibility == View.VISIBLE && !it.text.isNullOrBlank() }
        binding.nextBtn.isEnabled = allFilled
    }

    private fun populateDefaultsForExtraFields() {
        //binding.emailInput.setText("test@example.com")
        binding.phoneInput.setText("555-123-4567")
        binding.streetInput.setText("123 Main St")
        binding.cityInput.setText("Toronto")
        binding.provinceInput.setText("ON")
        binding.postalInput.setText("A1A 1A1")
    }
    
    override fun setContentView(view: View?) {
        super.setContentView(view)
        applyEdgeToEdgeInsets()
    }

    private fun applyEdgeToEdgeInsets() {
        val rootView = window.decorView.findViewWithTag<View>("content")
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
                val systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout()
                )
                view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
                )
                insets
            }
        }
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
