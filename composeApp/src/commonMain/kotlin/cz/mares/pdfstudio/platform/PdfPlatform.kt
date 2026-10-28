package cz.mares.pdfstudio.platform

import androidx.compose.ui.graphics.ImageBitmap
import cz.mares.pdfstudio.model.PdfPageModel
import kotlinx.coroutines.flow.StateFlow

internal data class ExternalPdfDocument(
    val id: Long,
    val bytes: ByteArray,
    val fileName: String
)

internal interface PdfPlatformBridge {
    val externalPdfFlow: StateFlow<ExternalPdfDocument?>
    fun clearExternalPdf()
    suspend fun loadPdfBytes(bytes: ByteArray, fileNameHint: String?): List<PdfPageModel>
    suspend fun generateBlankPage(width: Int, height: Int): PdfPageModel
    suspend fun generatePagesFromText(title: String, bodyText: String, addRuledLines: Boolean): List<PdfPageModel>
    suspend fun exportPdfBytes(pages: List<PdfPageModel>): ByteArray
    fun shareFile(fileName: String, bytes: ByteArray)
    fun printFile(fileName: String, bytes: ByteArray)
    fun pickPdfFile(onFileSelected: (ByteArray, String) -> Unit)
    fun savePdfFile(fileName: String, bytes: ByteArray)
}

internal expect fun getPlatformBridge(): PdfPlatformBridge

internal expect fun decodeImageBytes(bytes: ByteArray): ImageBitmap
