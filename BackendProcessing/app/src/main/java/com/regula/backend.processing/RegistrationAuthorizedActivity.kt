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

class RegistrationAuthorizedActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationAuthorizedActivity"
    }

    private lateinit var binding: ActivityRegistrationAuthorizedBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var neuvoteManager: NeuvoteManager

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

        binding.biometricsId.setText(neuvoteManager.getBiometricsId())
        binding.nameValue.setText(neuvoteManager.getFullName())

        binding.exitBtn.setOnClickListener {
            finishAffinity()
        }

        // Disable back navigation using OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Toast.makeText(this@RegistrationAuthorizedActivity, "Unable to return to previous screen", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    override fun onBackPressed() {
        Toast.makeText(this, "Unable to return to previous screen", Toast.LENGTH_LONG).show()
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
