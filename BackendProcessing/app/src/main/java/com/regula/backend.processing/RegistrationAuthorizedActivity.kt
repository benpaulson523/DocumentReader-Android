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
import java.io.File

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

        var resultString = "online_registration"

        if (!isInternetAvailable(this)) {
            binding.registeredMessage.setText(R.string.registration_recorded_message)

            // Assemble data into a JSON object
            val json = org.json.JSONObject().apply {
                put("registrationCode", neuvoteManager.getRegistrationCode())
                put("firstName", neuvoteManager.getFirstName())
                put("middleName", neuvoteManager.getMiddleName())
                put("lastName", neuvoteManager.getSurname())
                put("dateOfBirth", neuvoteManager.getDateOfBirth())
                put("sex", neuvoteManager.getSex())
                put("email", neuvoteManager.getEmail())
                put("phone", neuvoteManager.getPhone())
                put("streetAddress", neuvoteManager.getStreetAddress())
                put("unitNumber", neuvoteManager.getUnitNumber())
                put("city", neuvoteManager.getCity())
                put("jurisdiction", neuvoteManager.getJurisdiction())
                put("postalCode", neuvoteManager.getPostalCode())
                put("country", neuvoteManager.getCountry())
                put("photo", bitmapToBase64(neuvoteManager.getPhoto()))
                put("officialDocument", bitmapToBase64(neuvoteManager.getOfficialDocumentScan()))
                put("supportDocument", bitmapToBase64(neuvoteManager.getSupportDocumentScan()))
            }
            // Write JSON to a file in the app's files directory
            val dir = File(filesDir, "exports").apply { mkdirs() }
            val outFile = File(dir, "registration_data_${System.currentTimeMillis()}.json")
            outFile.writeText(json.toString())
            Log.i(TAG, "File written to filesDir: ${outFile.absolutePath}, exists: ${outFile.exists()}")
            // Grant URI permission to the known calling app and return a content URI
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "com.regula.backend.processing.fileprovider",
                outFile
            )
            // Grant temporary read permission to the known calling app
            grantUriPermission(
                Constants.BIOMETRICS_APP_PACKAGE,
                contentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            resultString = contentUri.toString()
        }

        Log.i(TAG, "Returning: $resultString")

        binding.doneBtn.setOnClickListener {
            showDialog("Processing...")
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
            resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setResult(RESULT_OK, resultIntent)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                finish()
            }, 1000) // 1 second delay
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
