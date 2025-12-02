package com.regula.backend.processing

import android.util.Log
import android.widget.Toast
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.regula.backend.processing.databinding.ActivityRegistrationEnterContactBinding
import androidx.appcompat.app.AlertDialog

class RegistrationEnterContactActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationEnterContactActivity"
    }

    private lateinit var binding: ActivityRegistrationEnterContactBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var neuvoteManager: NeuvoteManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationEnterContactActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationEnterContactBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )
        var selectedIndex = 0
        
        //val buttons = listOf(binding.btnEmail, binding.btnSms, binding.btnBoth)
        val buttons = listOf(binding.btnEmail, binding.btnSms)
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                selectedIndex = index
                Log.d(TAG, "Radio button selected, Method index: $selectedIndex")
            }
        }
        // set email as selected by default
        buttons[0].performClick()

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
            var phone = binding.phoneValue.text?.toString() ?: ""

            if (!phone.startsWith("+")) {
                phone = "+1$phone"
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showToast(this, getString(R.string.invalid_email_address))
            } else if (((!android.util.Patterns.PHONE.matcher(phone).matches()) ||
                        (phone.filter { it.isDigit() }.length < 8)) &&
                        ((selectedIndex == 0) || (selectedIndex == 2))) {
                showToast(this, getString(R.string.invalid_phone_number))
            } else {
                neuvoteManager.setEmail(email)
                neuvoteManager.setPhone(phone)

                Log.d(TAG, "Send code button clicked. Email: $email, Phone: $phone, Method index: $selectedIndex")

                if (selectedIndex == 0) {
                    neuvoteManager.setVerifyMethod("email")
                    neuvoteManager.sendVerificationEmail(
                        this,
                        email.orEmpty()
                    ) { success ->
                        if (success) {
                            Log.d(TAG, "Verification email sent successfully")
                            NavigationHelper.navigateToRegistrationVerifyContact(this)
                        } else {
                            showToast(this, getString(R.string.verification_email_failed))
                        }
                    }
                } else if (selectedIndex == 1) {
                    neuvoteManager.setVerifyMethod("sms")
                    neuvoteManager.sendVerificationText(
                        this,
                        phone.orEmpty()
                    ) { success ->
                        if (success) {
                            Log.d(TAG, "Verification text sent successfully")
                            NavigationHelper.navigateToRegistrationVerifyContact(this)
                        } else {
                            showToast(this, getString(R.string.verification_text_failed))
                        }
                    }
                } else {
                    neuvoteManager.setVerifyMethod("email")
                    neuvoteManager.sendVerificationEmail(
                        this,
                        email.orEmpty()
                    ) { success ->
                        if (success) {
                            Log.d(TAG, "Verification email sent successfully")
                        } else {
                            showToast(this, getString(R.string.verification_email_failed))
                        }
                    }
                    neuvoteManager.sendVerificationText(
                        this,
                        phone.orEmpty()
                    ) { success ->
                        if (success) {
                            Log.d(TAG, "Verification text sent successfully")
                            NavigationHelper.navigateToRegistrationVerifyContact(this)
                        } else {
                            showToast(this, getString(R.string.verification_text_failed))
                        }
                    }
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
