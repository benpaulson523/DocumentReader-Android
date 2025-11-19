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
        
        // Setup code boxes logic
        val codeBoxes = listOf(
            binding.root.findViewById<android.widget.EditText>(R.id.codeBox1),
            binding.root.findViewById<android.widget.EditText>(R.id.codeBox2),
            binding.root.findViewById<android.widget.EditText>(R.id.codeBox3),
            binding.root.findViewById<android.widget.EditText>(R.id.codeBox4),
            binding.root.findViewById<android.widget.EditText>(R.id.codeBox5),
            binding.root.findViewById<android.widget.EditText>(R.id.codeBox6)
        )

        codeBoxes.forEachIndexed { i, editText ->
            editText.filters = arrayOf(android.text.InputFilter.LengthFilter(1))
            editText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val text = s?.toString() ?: ""
                    if (text.length == 1 && i < codeBoxes.size - 1) {
                        codeBoxes[i + 1].requestFocus()
                    } else if (text.isEmpty() && i > 0) {
                        codeBoxes[i - 1].requestFocus()
                    }
                    val code = codeBoxes.joinToString(separator = "") { it.text?.toString() ?: "" }
                    binding.verifyBtn.isEnabled = code.length == 6 && code.all { it.isDigit() }
                }
            })
        }

        binding.verifyBtn.setOnClickListener {
            binding.errorMessage.visibility = View.GONE
            val code = codeBoxes.joinToString(separator = "") { it.text?.toString() ?: "" }
            Log.d(TAG, "Confirm button clicked. Code: $code")

            neuvoteManager.completeRegistration(
                this,
                code,
                com.regula.backend.processing.IProovManager.getInstanceOrNull()
            ) { success, errorMsg ->
                if (success) {
                    Log.d(TAG, "Registration completed successfully")
                    Toast.makeText(this, getString(R.string.registration_received), Toast.LENGTH_LONG).show()
                    binding.verifyBtn.isEnabled = false
                    binding.errorMessage.visibility = View.GONE
                    NavigationHelper.navigateToRegistrationAuthorized(this)
                } else {
                    Log.d(TAG, "Registration failed")
                    val errorText = errorMsg ?: getString(R.string.registration_failed)
                    Toast.makeText(this, errorText, Toast.LENGTH_LONG).show()
                    binding.errorMessage.text = errorText
                    binding.errorMessage.visibility = View.VISIBLE
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
