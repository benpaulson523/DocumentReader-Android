package com.regula.backend.processing

import android.util.Log
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.regula.backend.processing.databinding.ActivityRegistrationSelectSupportDocBinding
import androidx.appcompat.app.AlertDialog

class RegistrationSelectSupportDocActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationSelectSupportDocActivity"
    }

    private lateinit var binding: ActivityRegistrationSelectSupportDocBinding
    private var loadingDialog: AlertDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Opened RegistrationSelectSupportDocActivity screen")

        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationSelectSupportDocBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val buttons = listOf(binding.btnUtilityBill, binding.btnCarInsurance, binding.btnTaxReceipt)
        var selectedIndex = 0 // Default to utility bill
        
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
            NavigationHelper.navigateToRegistrationScanSupportDoc(this)
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
