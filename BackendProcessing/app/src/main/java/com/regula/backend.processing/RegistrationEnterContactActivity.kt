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
import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.app.Activity

class RegistrationEnterContactActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationEnterContactActivity"
    }

    private lateinit var binding: ActivityRegistrationEnterContactBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var neuvoteManager: NeuvoteManager
    private lateinit var downstreamLauncher: ActivityResultLauncher<Intent>

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

        if (isInternetAvailable(this)) {
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
        } else {
            binding.sendBtn.setText(R.string.continueString)
            binding.verifyInstruction.visibility = View.GONE
            binding.confirmationMethodGroup.visibility = View.GONE
        }
        
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
                        (phone.filter { it.isDigit() }.length < 11))) {
                showToast(this, getString(R.string.invalid_phone_number))
            } else {
                neuvoteManager.setEmail(email)
                neuvoteManager.setPhone(phone)

                Log.d(TAG, "Send code button clicked. Email: $email, Phone: $phone, Method index: $selectedIndex")

                if (isInternetAvailable(this)) {
                    if (selectedIndex == 0) {
                        neuvoteManager.setVerifyMethod("email")
                        neuvoteManager.sendVerificationEmail(
                            this,
                            email.orEmpty()
                        ) { success ->
                            if (success) {
                                Log.d(TAG, "Verification email sent successfully")
                                val intent = Intent(this, RegistrationVerifyContactActivity::class.java)
                                downstreamLauncher.launch(intent)
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
                                val intent = Intent(this, RegistrationVerifyContactActivity::class.java)
                                downstreamLauncher.launch(intent)
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
                                val intent = Intent(this, RegistrationVerifyContactActivity::class.java)
                                downstreamLauncher.launch(intent)
                            } else {
                                showToast(this, getString(R.string.verification_text_failed))
                            }
                        }
                    }
                } else {
                    val intent = Intent(this, RegistrationAuthorizedActivity::class.java)
                    downstreamLauncher.launch(intent)
                }
            }
        }
        
        // Register the ActivityResultLauncher
        downstreamLauncher = registerForActivityResult(StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val resultString = data?.getStringExtra("resultString")
                Log.i(TAG, "Received result string from downstream activity: " + resultString)
                val resultIntent = Intent()
                resultIntent.putExtra("resultString", resultString)
                resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setResult(RESULT_OK, resultIntent)
                finish()
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
