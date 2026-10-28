package cz.mares.pdfstudio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import cz.mares.pdfstudio.platform.AndroidBridgeHolder
import cz.mares.pdfstudio.ui.App
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var pendingPickCallback: ((ByteArray, String) -> Unit)? = null
    private var pendingSaveBytes: ByteArray? = null

    private val openPdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val callback = pendingPickCallback
        pendingPickCallback = null
        if (uri != null && callback != null) {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val fileName = try {
                    resolveFileName(uri)
                } catch (_: Exception) {
                    null
                } ?: "Dokument.pdf"
                if (bytes != null) {
                    callback(bytes, fileName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val createPdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val bytesToWrite = pendingSaveBytes
        pendingSaveBytes = null
        if (uri != null && bytesToWrite != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(bytesToWrite)
                    output.flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AndroidBridgeHolder.appContext = applicationContext
        AndroidBridgeHolder.pickPdfTrigger = { callback ->
            pendingPickCallback = callback
            openPdfLauncher.launch(arrayOf("application/pdf"))
        }
        AndroidBridgeHolder.savePdfTrigger = { fileName, bytes ->
            pendingSaveBytes = bytes
            val cleanName = if (fileName.endsWith(".pdf", ignoreCase = true)) {
                fileName
            } else {
                "$fileName.pdf"
            }
            createPdfLauncher.launch(cleanName)
        }
        AndroidBridgeHolder.printPdfTrigger = { fileName, bytes ->
            printPdfDocument(fileName, bytes)
        }

        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        addOnNewIntentListener { newIntent ->
            setIntent(newIntent)
            handleIncomingIntent(newIntent)
        }

        setContent {
            App()
        }
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        val action = incomingIntent.action
        if (action == Intent.ACTION_MAIN) return

        val uri: Uri? = incomingIntent.data
            ?: incomingIntent.clipData?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0)?.uri else null
            }
            ?: @Suppress("DEPRECATION") (incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)

        if (uri != null) {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val fileName = try {
                    resolveFileName(uri)
                } catch (_: Exception) {
                    null
                } ?: "Dokument.pdf"

                if (bytes != null && bytes.isNotEmpty()) {
                    AndroidBridgeHolder.deliverExternalPdf(bytes, fileName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun printPdfDocument(fileName: String, bytes: ByteArray) {
        try {
            val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
            val cleanDocName = if (fileName.endsWith(".pdf", ignoreCase = true)) {
                fileName
            } else {
                "$fileName.pdf"
            }
            val jobName = "PDF Studio - $cleanDocName"

            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder(cleanDocName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onWriteCancelled()
                        return
                    }
                    if (destination == null) {
                        callback?.onWriteFailed("Chybí cílový soubor pro tisk.")
                        return
                    }
                    try {
                        FileOutputStream(destination.fileDescriptor).use { out ->
                            out.write(bytes)
                            out.flush()
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.localizedMessage)
                    }
                }
            }

            printManager.print(jobName, adapter, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.lastPathSegment
        }
        return result
    }
}
