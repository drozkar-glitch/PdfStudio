package cz.mares.pdfstudio.model

import android.graphics.Bitmap
import android.graphics.Color
import java.util.UUID

enum class EditTool(val label: String) {
    NONE("Posun / Zoom"),
    PEN("Pero / Podpis"),
    HIGHLIGHTER("Zvýrazňovač"),
    REDACT("Zabělení"),
    TEXT("Text")
}

data class NormalizedPoint(
    val x: Float,
    val y: Float
)

data class DrawStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<NormalizedPoint>,
    val colorArgb: Int,
    val strokeWidthRatio: Float,
    val isHighlighter: Boolean = false
)

data class RedactRect(
    val id: String = UUID.randomUUID().toString(),
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val colorArgb: Int = Color.WHITE
)

data class PdfTextBox(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val x: Float,
    val y: Float,
    val colorArgb: Int = Color.BLACK,
    val fontSizeRatio: Float = 0.025f
)

data class PdfPageModel(
    val id: String = UUID.randomUUID().toString(),
    val originalBitmap: Bitmap,
    val width: Int,
    val height: Int,
    val strokes: List<DrawStroke> = emptyList(),
    val redactions: List<RedactRect> = emptyList(),
    val textBoxes: List<PdfTextBox> = emptyList(),
    val actionHistory: List<String> = emptyList()
)
