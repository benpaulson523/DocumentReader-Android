package com.regula.backend.processing

import android.util.Log
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.regula.documentreader.api.DocumentReader
import com.regula.documentreader.api.completions.IDocumentReaderCompletion
import com.regula.documentreader.api.completions.IDocumentReaderInitCompletion
import com.regula.documentreader.api.completions.rfid.IRfidReaderCompletion
import com.regula.documentreader.api.config.ScannerConfig
import com.regula.documentreader.api.enums.DocReaderAction
import com.regula.documentreader.api.enums.DocReaderOrientation
import com.regula.documentreader.api.enums.Scenario
import com.regula.documentreader.api.enums.eGraphicFieldType
import com.regula.documentreader.api.enums.eVisualFieldType
import com.regula.documentreader.api.errors.DocumentReaderException
import com.regula.documentreader.api.params.BackendProcessingConfig
import com.regula.documentreader.api.params.DocReaderConfig
import com.regula.documentreader.api.results.DocumentReaderResults
import com.regula.documentreader.api.results.TransactionInfo
import java.util.concurrent.Executors


class RegulaScanner private constructor(
    var context: Context,
    var showDialog: (String?) -> Unit,
    var dismissDialog: () -> Unit
) {
    @Volatile
    private var isInitialized: Boolean = false

    companion object {
        private const val TAG = "RegulaScanner"
        @Volatile
        private var instance: RegulaScanner? = null

        @JvmStatic
        fun getInstance(
            context: Context,
            showDialog: (String?) -> Unit,
            dismissDialog: () -> Unit
        ): RegulaScanner {
            return if (instance == null) {
                synchronized(this) {
                    instance ?: RegulaScanner(context, showDialog, dismissDialog).also { instance = it }
                }
            } else {
                instance!!.context = context
                instance!!.showDialog = showDialog
                instance!!.dismissDialog = dismissDialog
                instance!!
            }
        }
    }

    fun initializeReader() {
        val initCompletionWithCallback = IDocumentReaderInitCompletion { result: Boolean, error: DocumentReaderException? ->
            dismissDialog()
            if (result) {
                isInitialized = true
                if (DocumentReader.Instance().availableScenarios.size == 0) {
                    showToast(context, "Init failed: available scenarios list is empty")
                }
            } else {
                Log.d(TAG, "Exception during initialization1")
                showToast(context, "Init failed: ${error?.message}")
                return@IDocumentReaderInitCompletion
            }
        }
        Executors.newSingleThreadExecutor().execute {
            try {
                val licInput = context.resources.openRawResource(R.raw.regula)
                val available = licInput.available()
                val license = ByteArray(available)
                licInput.read(license)
                licInput.close()
                val handler = Handler(Looper.getMainLooper())
                val activity = context as? androidx.appcompat.app.AppCompatActivity
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    handler.post {
                        val docReaderConfig = DocReaderConfig(license)
                        DocumentReader.Instance().functionality().edit().setOrientation(DocReaderOrientation.PORTRAIT).apply()
                        DocumentReader.Instance()
                            .initializeReader(activity, docReaderConfig, initCompletionWithCallback)
                    }
                }
            } catch (ex: Exception) {
                Log.d(TAG, "Exception during initialization2")
                ex.printStackTrace()
                showToast(context, "Init error: " + ex.localizedMessage)
            }
        }
    }

    private fun getCompletion(
        readChip: Boolean,
        onFinalize: (DocumentReaderResults?) -> Unit,
        onFailure: () -> Unit
    ) = IDocumentReaderCompletion { action, results, _ ->
        if (action == DocReaderAction.COMPLETE) {
            Log.d(TAG, "IDocumentReaderCompletion COMPLETE")
            if (readChip) {
                    // Start RFID reader with a 30 second timeout. If onCompleted isn't called within
                    // the timeout, stop the RFID reader and call onFailure so caller can handle it.
                    val mainHandler = Handler(Looper.getMainLooper())
                    val timeoutHandler = Handler(Looper.getMainLooper())
                    val timeoutRunnable = Runnable {
                        Log.w(TAG, "RFID read timeout reached, stopping RFID reader")
                        DocumentReader.Instance().stopRFIDReader(context)
                        mainHandler.post { onFinalize(results) }
                    }

                    // Schedule the timeout
                    timeoutHandler.postDelayed(timeoutRunnable, 30_000)

                    DocumentReader.Instance().startRFIDReader(context, object : IRfidReaderCompletion() {
                        override fun onCompleted(
                            rfidAction: Int,
                            documentReaderResults: DocumentReaderResults?,
                            e: DocumentReaderException?
                        ) {
                            // Cancel timeout since callback has been invoked
                            timeoutHandler.removeCallbacks(timeoutRunnable)

                            // Successful RFID read — run finalizer on main thread
                            mainHandler.post { onFinalize(documentReaderResults) }
                        }
                    })
            } else {
                onFinalize(results)
            }
        } else {
            if (action == DocReaderAction.CANCEL) {
                Log.d(TAG, "IDocumentReaderCompletion CANCEL")
                onFailure()
            } else if (action == DocReaderAction.ERROR) {
                Log.d(TAG, "IDocumentReaderCompletion ERROR")
                onFailure()
            } else if (action == DocReaderAction.TIMEOUT) {
                Log.d(TAG, "IDocumentReaderCompletion TIMEOUT")
                onFailure()
            }
        }
    }

    fun showScanner(
        readChip: Boolean,
        locateScenario: Boolean,
        onFinalize: (DocumentReaderResults?) -> Unit,
        onFailure: () -> Unit
    ) {
        if (!isInitialized) {
            showDialog("Initializing document reader...")
            // Poll until initialized, then proceed
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed(object : Runnable {
                override fun run() {
                    if (isInitialized) {
                        dismissDialog()
                        startScannerInternal(readChip, locateScenario, onFinalize, onFailure)
                    } else {
                        handler.postDelayed(this, 500)
                    }
                }
            }, 500)
        } else {
            startScannerInternal(readChip, locateScenario, onFinalize, onFailure)
        }
    }

    private fun startScannerInternal(
        readChip: Boolean,
        locateScenario: Boolean,
        onFinalize: (DocumentReaderResults?) -> Unit,
        onFailure: () -> Unit
    ) {
        DocumentReader.Instance().functionality().edit().setDoRecordProcessingVideo(true).apply()

        val internetAvailable = isInternetAvailable(instance!!.context)
        Log.d(TAG, "Internet connectivity: $internetAvailable")
        if (internetAvailable) {
            val backendProcessingConfig = BackendProcessingConfig(Constants.REGULA_BASE_URL)
            DocumentReader.Instance().processParams().backendProcessingConfig = backendProcessingConfig
        }

        var scannerConfig = ScannerConfig.Builder(Scenario.SCENARIO_FULL_PROCESS).build()
        if (locateScenario) {
            scannerConfig = ScannerConfig.Builder(Scenario.SCENARIO_LOCATE).build()
        }
        DocumentReader.Instance().startScanner(context, scannerConfig, getCompletion(readChip, onFinalize, onFailure))
    }
}
