package com.regula.backend.processing

import android.util.Log
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.regula.backend.processing.databinding.ActivityRegistrationSelectDocBinding
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.app.Activity

class RegistrationSelectDocActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationSelectDocActivity"
    }

    private lateinit var binding: ActivityRegistrationSelectDocBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var neuvoteManager: NeuvoteManager
    private lateinit var downstreamLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationSelectDocActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationSelectDocBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        
        neuvoteManager = NeuvoteManager.getInstance(
            context = this,
            showDialog = { msg -> showDialog(msg) },
            dismissDialog = { dismissDialog() }
        )

        val buttons = listOf(binding.btnPassport, binding.btnDriverLicense, binding.btnGovernmentID)
        var selectedIndex = 0 // Default to Passport

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

        binding.continueBtn.setOnClickListener {
            neuvoteManager.setReadChip(selectedIndex == 0)

            val readChip = neuvoteManager.getReadChip()
            Log.d(TAG, "Reading chip: $readChip")
            val intent = Intent(this, RegistrationScanDocActivity::class.java)
            downstreamLauncher.launch(intent)
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
