package com.regula.backend.processing

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
import com.regula.documentreader.api.enums.Scenario
import com.regula.documentreader.api.enums.eGraphicFieldType
import com.regula.documentreader.api.enums.eVisualFieldType
import com.regula.documentreader.api.errors.DocumentReaderException
import com.regula.documentreader.api.params.BackendProcessingConfig
import com.regula.documentreader.api.params.DocReaderConfig
import com.regula.documentreader.api.results.DocumentReaderResults
import com.regula.documentreader.api.results.TransactionInfo
import java.util.concurrent.Executors

class RegulaScanner(
    private val context: Context,
    private val onResults: (DocumentReaderResults?) -> Unit,
    private val onFinalize: (DocumentReaderResults?) -> Unit,
    private val showDialog: (String?) -> Unit,
    private val dismissDialog: () -> Unit
) {
    fun initializeReader(onInitialized: (() -> Unit)? = null) {
        showDialog("Initializing...")
        val initCompletionWithCallback = IDocumentReaderInitCompletion { result: Boolean, error: DocumentReaderException? ->
            dismissDialog()
            if (result) {
                if (DocumentReader.Instance().availableScenarios.size == 0) {
                    Toast.makeText(
                        context,
                        "Available scenarios list is empty",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                onInitialized?.invoke()
            } else {
                Toast.makeText(context, "Init failed: ${error?.message}", Toast.LENGTH_LONG).show()
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
                if (context is androidx.appcompat.app.AppCompatActivity && !context.isFinishing && !context.isDestroyed) {
                    handler.post {
                        val docReaderConfig = DocReaderConfig(license)
                        DocumentReader.Instance()
                            .initializeReader(context, docReaderConfig, initCompletionWithCallback)
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                Toast.makeText(
                    context,
                    "init error: " + ex.localizedMessage,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun getCompletion(readChip: Boolean) =
        IDocumentReaderCompletion { action, results, error ->
            if (action == DocReaderAction.COMPLETE) {
                if (readChip) {
                    DocumentReader.Instance().startRFIDReader(context, object : IRfidReaderCompletion() {
                        override fun onCompleted(
                            rfidAction: Int,
                            documentReaderResults: DocumentReaderResults?,
                            e: DocumentReaderException?
                        ) {
                            onFinalize(documentReaderResults)
                        }
                    })
                } else {
                    onFinalize(results)
                }
            } else {
                if (action == DocReaderAction.CANCEL) {
                    Toast.makeText(context, "Scanning was cancelled", Toast.LENGTH_LONG)
                        .show()
                } else if (action == DocReaderAction.ERROR) {
                    Toast.makeText(context, "Error:${error?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

    fun showScanner(readChip: Boolean) {
        val backendProcessingConfig = BackendProcessingConfig(Constants.REGULA_BASE_URL)
        DocumentReader.Instance().functionality().edit().setDoRecordProcessingVideo(true).apply()
        DocumentReader.Instance().processParams().backendProcessingConfig = backendProcessingConfig
        val scannerConfig = ScannerConfig.Builder(Scenario.SCENARIO_FULL_PROCESS).build()
        DocumentReader.Instance().startScanner(context, scannerConfig, getCompletion(readChip))
    }

    fun finalize(results: DocumentReaderResults?) {
        showDialog("Finalizing process...")
        DocumentReader.Instance()
            .finalizePackage { action: Int, transactionInfo: TransactionInfo?, documentReaderException: DocumentReaderException? ->
                dismissDialog()
                if (action == DocReaderAction.COMPLETE) {
                    Toast.makeText(
                        context,
                        "Finalize Done. TransactionId " + transactionInfo?.transactionId,
                        Toast.LENGTH_LONG
                    ).show()
                    onResults(results)
                } else if (documentReaderException != null) {
                    Toast.makeText(
                        context,
                        "Failed to Finalize. Error " + documentReaderException.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}
