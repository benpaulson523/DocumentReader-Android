package com.regula.backend.processing

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.regula.backend.processing.databinding.ActivityRegistrationEnterAddressBinding
import androidx.appcompat.app.AlertDialog

class RegistrationEnterAddressActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationEnterAddressActivity"
    }

    private lateinit var binding: ActivityRegistrationEnterAddressBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var neuvoteManager: NeuvoteManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationEnterAddressActivity screen")
        
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationEnterAddressBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )

        // Enable saveBtn when all fields are non-empty
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val streetNotEmpty = binding.streetValue.text?.isNotEmpty() == true
                val cityNotEmpty = binding.cityValue.text?.isNotEmpty() == true
                val provinceNotEmpty = binding.provinceValue.text?.isNotEmpty() == true
                val postalNotEmpty = binding.postalValue.text?.isNotEmpty() == true
                binding.saveBtn.isEnabled = streetNotEmpty && cityNotEmpty && provinceNotEmpty && postalNotEmpty
            }
        }
        binding.streetValue.addTextChangedListener(watcher)
        binding.unitNumberValue.addTextChangedListener(watcher)
        binding.cityValue.addTextChangedListener(watcher)
        binding.provinceValue.addTextChangedListener(watcher)
        binding.postalValue.addTextChangedListener(watcher)

        binding.saveBtn.setOnClickListener {
            val street = binding.streetValue.text?.toString() ?: ""
            val unitNumber = binding.unitNumberValue.text?.toString() ?: ""
            val city = binding.cityValue.text?.toString() ?: ""
            val province = binding.provinceValue.text?.toString() ?: ""
            val postal = binding.postalValue.text?.toString() ?: ""

            neuvoteManager.setStreetAddress(street)
            neuvoteManager.setUnitNumber(unitNumber)
            neuvoteManager.setCity(city)
            neuvoteManager.setJurisdiction(province)
            neuvoteManager.setPostalCode(postal)

            Log.d(TAG, "Address saved: $street, Apt/Unit: $unitNumber, $city, $province, $postal")
            NavigationHelper.navigateToRegistrationSelectSupportDoc(this)
        }
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
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
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
