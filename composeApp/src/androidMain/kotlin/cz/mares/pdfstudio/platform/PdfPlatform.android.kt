package cz.mares.pdfstudio.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import cz.mares.pdfstudio.model.PdfPageModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

internal object AndroidBridgeHolder {
    var appContext: Context? = null
    var pickPdfTrigger: (((ByteArray, String) -> Unit) -> Unit)? = null
    var savePdfTrigger: ((String, ByteArray) -> Unit)? = null
    var printPdfTrigger: ((String, ByteArray) -> Unit)? = null

    private val _externalPdfFlow = MutableStateFlow<ExternalPdfDocument?>(null)
    val externalPdfFlow: StateFlow<ExternalPdfDocument?> = _externalPdfFlow.asStateFlow()

    fun deliverExternalPdf(bytes: ByteArray, fileName: String) {
        _externalPdfFlow.value = ExternalPdfDocument(
            id = System.nanoTime(),
            bytes = bytes,
            fileName = fileName
        )
    }

    fun clearExternalPdf() {
        _externalPdfFlow.value = null
    }
}

internal class AndroidPdfPlatformBridge : PdfPlatformBridge {

    private val defaultPageWidth = 1240
    private val defaultPageHeight = 1754

    override val externalPdfFlow: StateFlow<ExternalPdfDocument?>
        get() = AndroidBridgeHolder.externalPdfFlow

    override fun clearExternalPdf() {
        AndroidBridgeHolder.clearExternalPdf()
    }

    override suspend fun loadPdfBytes(bytes: ByteArray, fileNameHint: String?): List<PdfPageModel> {
        val context = AndroidBridgeHolder.appContext ?: return emptyList()
        val resultPages = mutableListOf<PdfPageModel>()
        val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.pdf")

        try {
            tempFile.writeBytes(bytes)
            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val scale = if (page.width > 0) {
                    defaultPageWidth.toFloat() / page.width.toFloat()
                } else {
                    2.0f
                }.coerceIn(1.0f, 3.0f)

                val bmpWidth = (page.width * scale).toInt().coerceAtLeast(100)
                val bmpHeight = (page.height * scale).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)

                resultPages.add(
                    PdfPageModel(
                        id = UUID.randomUUID().toString(),
                        width = bmpWidth,
                        height = bmpHeight,
                        imageBytes = out.toByteArray()
                    )
                )
            }

            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }

        return resultPages
    }

    override suspend fun generateBlankPage(width: Int, height: Int): PdfPageModel {
        val safeW = width.coerceAtLeast(100)
        val safeH = height.coerceAtLeast(100)
        val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)

        return PdfPageModel(
            id = UUID.randomUUID().toString(),
            width = safeW,
            height = safeH,
            imageBytes = out.toByteArray()
        )
    }

    override suspend fun generatePagesFromText(
        title: String,
        bodyText: String,
        addRuledLines: Boolean
    ): List<PdfPageModel> {
        val pages = mutableListOf<PdfPageModel>()
        val width = defaultPageWidth
        val height = defaultPageHeight
        val margin = 96

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 40, 80)
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(225, 230, 240)
            strokeWidth = 2f
        }

        val contentWidth = width - (margin * 2)
        val fullText = if (bodyText.isBlank()) " " else bodyText

        val staticLayout = StaticLayout.Builder
            .obtain(fullText, 0, fullText.length, bodyPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(8f, 1.15f)
            .setIncludePad(true)
            .build()

        var currentLine = 0
        val totalLines = staticLayout.lineCount
        var isFirstPage = true

        while (currentLine < totalLines || isFirstPage) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            var yOffset = margin.toFloat()

            if (isFirstPage && title.isNotBlank()) {
                val titleLayout = StaticLayout.Builder
                    .obtain(title, 0, title.length, titlePaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(true)
                    .build()

                canvas.save()
                canvas.translate(margin.toFloat(), yOffset)
                titleLayout.draw(canvas)
                canvas.restore()

                yOffset += titleLayout.height + 28f

                val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(180, 195, 220)
                    strokeWidth = 4f
                }
                canvas.drawLine(
                    margin.toFloat(),
                    yOffset,
                    (width - margin).toFloat(),
                    yOffset,
                    dividerPaint
                )
                yOffset += 36f
            }

            if (addRuledLines) {
                var ruleY = yOffset + 40f
                while (ruleY < height - margin) {
                    canvas.drawLine(
                        margin.toFloat(),
                        ruleY,
                        (width - margin).toFloat(),
                        ruleY,
                        linePaint
                    )
                    ruleY += 54f
                }
            }

            val availableHeight = (height - margin) - yOffset
            val startTop = staticLayout.getLineTop(currentLine)
            var endLine = currentLine

            while (endLine < totalLines) {
                val lineBottom = staticLayout.getLineBottom(endLine)
                if (lineBottom - startTop > availableHeight && endLine > currentLine) {
                    break
                }
                endLine++
            }

            val endBottom = staticLayout.getLineBottom(max(currentLine, endLine - 1))

            canvas.save()
            canvas.translate(margin.toFloat(), yOffset)
            canvas.clipRect(0, 0, contentWidth, endBottom - startTop)
            canvas.translate(0f, -startTop.toFloat())
            staticLayout.draw(canvas)
            canvas.restore()

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)

            pages.add(
                PdfPageModel(
                    id = UUID.randomUUID().toString(),
                    width = width,
                    height = height,
                    imageBytes = out.toByteArray()
                )
            )

            currentLine = endLine
            isFirstPage = false
            if (currentLine >= totalLines) break
        }

        return pages
    }

    override suspend fun exportPdfBytes(pages: List<PdfPageModel>): ByteArray {
        val pdfDocument = PdfDocument()
        try {
            pages.forEachIndexed { index, pageModel ->
                val pageWidth = pageModel.width.coerceAtLeast(100)
                val pageHeight = pageModel.height.coerceAtLeast(100)

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                val baseBitmap = BitmapFactory.decodeByteArray(
                    pageModel.imageBytes,
                    0,
                    pageModel.imageBytes.size
                )
                if (baseBitmap != null) {
                    val destRect = Rect(0, 0, pageWidth, pageHeight)
                    canvas.drawBitmap(baseBitmap, null, destRect, null)
                }

                val targetWidth = pageWidth.toFloat()
                val targetHeight = pageHeight.toFloat()

                val redactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                }
                for (rect in pageModel.redactions) {
                    redactPaint.color = rect.colorArgb
                    val left = min(rect.left, rect.right) * targetWidth
                    val right = max(rect.left, rect.right) * targetWidth
                    val top = min(rect.top, rect.bottom) * targetHeight
                    val bottom = max(rect.top, rect.bottom) * targetHeight
                    canvas.drawRect(RectF(left, top, right, bottom), redactPaint)
                }

                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                for (stroke in pageModel.strokes) {
                    if (stroke.points.isEmpty()) continue
                    strokePaint.color = stroke.colorArgb
                    strokePaint.alpha = if (stroke.isHighlighter) 95 else 255
                    strokePaint.strokeWidth = stroke.strokeWidthRatio * targetWidth

                    if (stroke.points.size == 1) {
                        val pt = stroke.points.first()
                        canvas.drawPoint(pt.x * targetWidth, pt.y * targetHeight, strokePaint)
                    } else {
                        val path = Path()
                        val first = stroke.points.first()
                        path.moveTo(first.x * targetWidth, first.y * targetHeight)
                        for (i in 1 until stroke.points.size) {
                            val pt = stroke.points[i]
                            path.lineTo(pt.x * targetWidth, pt.y * targetHeight)
                        }
                        canvas.drawPath(path, strokePaint)
                    }
                }

                for (textBox in pageModel.textBoxes) {
                    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textBox.colorArgb
                        textSize = textBox.fontSizeRatio * targetWidth
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    val startX = textBox.x * targetWidth
                    val startY = textBox.y * targetHeight
                    val availableWidth = (targetWidth - startX).toInt().coerceAtLeast(100)

                    val staticLayout = StaticLayout.Builder
                        .obtain(textBox.text, 0, textBox.text.length, textPaint, availableWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(false)
                        .build()

                    canvas.save()
                    canvas.translate(startX, startY)
                    staticLayout.draw(canvas)
                    canvas.restore()
                }

                pdfDocument.finishPage(pdfPage)
            }

            val output = ByteArrayOutputStream()
            pdfDocument.writeTo(output)
            return output.toByteArray()
        } finally {
            pdfDocument.close()
        }
    }

    override fun shareFile(fileName: String, bytes: ByteArray) {
        val context = AndroidBridgeHolder.appContext ?: return
        try {
            val cleanName = if (fileName.endsWith(".pdf", ignoreCase = true)) {
                fileName
            } else {
                "$fileName.pdf"
            }
            val outFile = File(context.cacheDir, cleanName)
            outFile.writeBytes(bytes)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Sdílet PDF dokument").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun printFile(fileName: String, bytes: ByteArray) {
        AndroidBridgeHolder.printPdfTrigger?.invoke(fileName, bytes)
    }

    override fun pickPdfFile(onFileSelected: (ByteArray, String) -> Unit) {
        AndroidBridgeHolder.pickPdfTrigger?.invoke(onFileSelected)
    }

    override fun savePdfFile(fileName: String, bytes: ByteArray) {
        AndroidBridgeHolder.savePdfTrigger?.invoke(fileName, bytes)
    }
}

internal actual fun getPlatformBridge(): PdfPlatformBridge = AndroidPdfPlatformBridge()

internal actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    return bmp.asImageBitmap()
}
