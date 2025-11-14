package com.regula.backend.processing

import android.util.Log
import android.widget.Toast
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.regula.backend.processing.databinding.ActivityRegistrationDataBinding
import androidx.appcompat.app.AlertDialog

class RegistrationDataActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationDataActivity"
    }

    private lateinit var binding: ActivityRegistrationDataBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var neuvoteManager: NeuvoteManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationDataActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationDataBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        var selectedIndex = 0
        
        val buttons = listOf(binding.btnEmail, binding.btnSms)
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                buttons.forEach {
                    it.setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.white)))
                    it.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.black))
                }
                button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.philippines_blue)))
                button.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white))
                selectedIndex = index
            }
        }
        // set passport as selected by default
        buttons[0].performClick()
        
        binding.nameValue.text = neuvoteManager.getFullName()
        binding.dobValue.text = neuvoteManager.getDateOfBirth()
        binding.addressValue.text = neuvoteManager.getFullAddress()

        // Enable sendBtn when both email and phone are non-empty
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val emailNotEmpty = binding.emailValue.text?.isNotEmpty() == true
                val phoneNotEmpty = binding.phoneValue.text?.isNotEmpty() == true
                binding.sendBtn.isEnabled = emailNotEmpty && phoneNotEmpty
            }
        }
        binding.emailValue.addTextChangedListener(watcher)
        binding.phoneValue.addTextChangedListener(watcher)

        binding.sendBtn.setOnClickListener {
            val email = binding.emailValue.text?.toString() ?: ""
            val phone = binding.phoneValue.text?.toString() ?: ""

            val digitCount = phone.filter { it.isDigit() }.length
if (digitCount < 8) {
    // Not enough digits
}

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() && (selectedIndex == 0)) {
                Toast.makeText(this, getString(R.string.invalid_email_address), Toast.LENGTH_LONG).show()
            } else if (((!android.util.Patterns.PHONE.matcher(phone).matches()) ||
                        (phone.filter { it.isDigit() }.length < 8)) &&
                        (selectedIndex == 1)) {
                Toast.makeText(this, getString(R.string.invalid_phone_number), Toast.LENGTH_LONG).show()
            } else {
                neuvoteManager.setEmail(email)
                neuvoteManager.setPhone(phone)

                Log.d(TAG, "Send code button clicked. Email: $email, Phone: $phone, Method index: $selectedIndex")

                if (selectedIndex == 0) {
                    neuvoteManager.sendVerificationEmail(
                        this,
                        email.orEmpty()
                    ) { success ->
                        if (success) {
                            Log.d(TAG, "Verification email sent successfully")
                            NavigationHelper.navigateToRegistrationVerifyContact(this)
                        } else {
                            Toast.makeText(this, getString(R.string.verification_email_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Sending code via SMS", Toast.LENGTH_LONG).show()
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
