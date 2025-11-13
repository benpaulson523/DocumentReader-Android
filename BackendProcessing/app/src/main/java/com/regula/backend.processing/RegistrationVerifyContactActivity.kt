package com.regula.backend.processing

import android.util.Log
import android.widget.Toast
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.regula.backend.processing.databinding.ActivityRegistrationVerifyContactBinding
import androidx.appcompat.app.AlertDialog

class RegistrationVerifyContactActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationVerifyContactActivity"
    }

    private lateinit var binding: ActivityRegistrationVerifyContactBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var neuvoteManager: NeuvoteManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationVerifyContactActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationVerifyContactBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        
        // Set max length for codeValue to 6
        val filterArray = arrayOf(android.text.InputFilter.LengthFilter(6))
        binding.codeValue.filters = filterArray

        // Enable confirmBtn when codeValue contains 6 digits
        binding.codeValue.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val code = s?.toString() ?: ""
                binding.confirmBtn.isEnabled = code.length == 6 && code.all { it.isDigit() }
            }
        })

        binding.confirmBtn.setOnClickListener {
            val code = binding.codeValue.text?.toString() ?: ""
            Log.d(TAG, "Confirm button clicked. Code: $code")

            neuvoteManager.completeRegistration(
                this,
                code,
                com.regula.backend.processing.IProovManager.getInstanceOrNull()
            ) { success ->
                if (success) {
                    binding.confirmBtn.isEnabled = false
                    Log.d(TAG, "Registration completed successfully")
                    Toast.makeText(this, getString(R.string.registration_received), Toast.LENGTH_LONG).show()
                } else {
                    Log.d(TAG, "Registration failed")
                    Toast.makeText(this, getString(R.string.registration_failed), Toast.LENGTH_LONG).show()
                }
            }
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
