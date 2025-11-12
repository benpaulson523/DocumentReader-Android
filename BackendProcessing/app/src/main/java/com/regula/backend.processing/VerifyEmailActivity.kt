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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.regula.backend.processing.R
import com.regula.backend.processing.databinding.ActivityVerifyEmailBinding

class VerifyEmailActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "VerifyEmailActivity"
    }
    
    private var loadingDialog: AlertDialog? = null
    private lateinit var binding: ActivityVerifyEmailBinding
    private lateinit var neuvoteManager: NeuvoteManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened VerifyEmailActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityVerifyEmailBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )

        // Populate mnemonicInput with value if needed
        val mnemonicUuid = neuvoteManager.getMnemonicUuid()
        binding.mnemonicInput.text = mnemonicUuid

        binding.backBtn.setOnClickListener { finish() }

        var email = neuvoteManager.getEmail()
        neuvoteManager.sendVerificationEmail(
            this,
            email.orEmpty(),
            mnemonicUuid.orEmpty()
        ) { success ->
            if (success) {
                Toast.makeText(this, getString(R.string.verification_email_sent), Toast.LENGTH_LONG).show()
                binding.emailCodeLabel.visibility = View.VISIBLE
                binding.emailCodeInput.visibility = View.VISIBLE
                binding.registerBtn.visibility = View.GONE
            } else {
                Toast.makeText(this, getString(R.string.verification_email_failed), Toast.LENGTH_LONG).show()
            }
        }
        
        binding.emailCodeInput.filters = arrayOf(
            android.text.InputFilter.LengthFilter(6),
            object : android.text.InputFilter {
                override fun filter(source: CharSequence?, start: Int, end: Int, dest: android.text.Spanned?, dstart: Int, dend: Int): CharSequence? {
                    val result = (dest?.subSequence(0, dstart).toString()) + (source?.subSequence(start, end).toString()) + (dest?.subSequence(dend, dest.length ?: 0).toString())
                    return if (result.length > 6 || !result.matches(Regex("\\d*"))) "" else null
                }
            }
        )
        
        binding.emailCodeInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val code = s?.toString() ?: ""
                binding.registerBtn.visibility = if (code.matches(Regex("^\\d{6}$"))) View.VISIBLE else View.GONE
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        binding.registerBtn.setOnClickListener {
            val code = binding.emailCodeInput.text?.toString() ?: ""
            neuvoteManager.completeRegistration(
                this,
                code,
                com.regula.backend.processing.IProovManager.getInstanceOrNull()
            ) { success ->
                if (success) {
                    binding.registerBtn.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.registration_received), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, getString(R.string.face_scan_flag_failed), Toast.LENGTH_LONG).show()
                }
            }
            binding.emailCodeInput.isEnabled = false
            binding.registerBtn.isEnabled = false
        }
        binding.registerBtn.visibility = View.GONE
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
