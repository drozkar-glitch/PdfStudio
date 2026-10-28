package cz.mares.pdfstudio.model

internal enum class EditTool(val label: String) {
    NONE("Posun / Zoom"),
    PEN("Pero / Podpis"),
    HIGHLIGHTER("Zvýrazňovač"),
    REDACT("Zabělení"),
    TEXT("Text")
}

internal data class NormalizedPoint(
    val x: Float,
    val y: Float
)

internal data class DrawStroke(
    val id: String,
    val points: List<NormalizedPoint>,
    val colorArgb: Int,
    val strokeWidthRatio: Float,
    val isHighlighter: Boolean = false
)

internal data class RedactRect(
    val id: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val colorArgb: Int
)

internal data class PdfTextBox(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val colorArgb: Int,
    val fontSizeRatio: Float
)

internal data class PdfPageModel(
    val id: String,
    val width: Int,
    val height: Int,
    val imageBytes: ByteArray,
    val strokes: List<DrawStroke> = emptyList(),
    val redactions: List<RedactRect> = emptyList(),
    val textBoxes: List<PdfTextBox> = emptyList(),
    val actionHistory: List<String> = emptyList()
)
