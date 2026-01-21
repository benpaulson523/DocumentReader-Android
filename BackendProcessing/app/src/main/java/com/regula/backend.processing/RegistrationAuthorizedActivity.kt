package com.regula.backend.processing

import android.util.Log
import android.widget.Toast
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.regula.backend.processing.databinding.ActivityRegistrationAuthorizedBinding
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import android.content.Intent
import android.app.Activity

class RegistrationAuthorizedActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationAuthorizedActivity"
    }

    private lateinit var binding: ActivityRegistrationAuthorizedBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var neuvoteManager: NeuvoteManager
    private lateinit var iProovManager: IProovManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationAuthorizedActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationAuthorizedBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )

        val registrationCode = neuvoteManager.getRegistrationCode()
        binding.registrationCode.setText(registrationCode)
        binding.nameValue.setText(neuvoteManager.getFullName())

        neuvoteManager.setRegistrationQRCode(
            generateRegistrationQRCode(registrationCode,
                neuvoteManager.getFirstName(),
                neuvoteManager.getMiddleName(),
                neuvoteManager.getSurname(),
                neuvoteManager.getDateOfBirth(),
                neuvoteManager.getSex()
            )
        )

        binding.qrCodeImage.setImageBitmap(neuvoteManager.getRegistrationQRCode())

        var resultString = neuvoteManager.getRegistrationQRCodeString()

        if (!isInternetAvailable(this)) {
            resultString += "%%" + neuvoteManager.getEmail()
            resultString += "%%" + neuvoteManager.getPhone()
            resultString += "%%" + neuvoteManager.getStreetAddress()
            resultString += "%%" + neuvoteManager.getUnitNumber()
            resultString += "%%" + neuvoteManager.getCity()
            resultString += "%%" + neuvoteManager.getJurisdiction()
            resultString += "%%" + neuvoteManager.getCountry()
            resultString += "%%" + bitmapToBase64(neuvoteManager.getPhoto())
        }
        
        Log.i(TAG, "Returning: " + resultString)

        binding.doneBtn.setOnClickListener {
            // Clear any in-memory session state before closing so reopening starts fresh
            try {
                neuvoteManager.reset()
                iProovManager = IProovManager.getInstance(
                    context = this
                )
                iProovManager.reset()
            } catch (t: Exception) {
                Log.w(TAG, "Failed to reset before exit", t)
            }

            // Pass resultString back to the calling app
            val resultIntent = Intent()
            resultIntent.putExtra("resultString", resultString)
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        // Disable back navigation using OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    showToast(this@RegistrationAuthorizedActivity, getString(R.string.unable_to_return))
                }
            }
        )
    }

    override fun onBackPressed() {
        showToast(this, getString(R.string.unable_to_return))
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
