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
import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.app.Activity

class RegistrationDataActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationDataActivity"
    }

    private lateinit var binding: ActivityRegistrationDataBinding
    private var loadingDialog: AlertDialog? = null
    private lateinit var neuvoteManager: NeuvoteManager
    private lateinit var downstreamLauncher: ActivityResultLauncher<Intent>

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
        
        binding.nameValue.text = neuvoteManager.getFullName()
        binding.dobValue.text = neuvoteManager.getDateOfBirth()
        binding.addressValue.text = neuvoteManager.getFullAddress()

        binding.confirmBtn.setOnClickListener {
            val intent = Intent(this, RegistrationEnterContactActivity::class.java)
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
