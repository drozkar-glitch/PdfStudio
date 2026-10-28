package cz.mares.pdfstudio.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import cz.mares.pdfstudio.model.PdfPageModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

object PdfHelper {

    private const val DEFAULT_PAGE_WIDTH = 1240
    private const val DEFAULT_PAGE_HEIGHT = 1754

    suspend fun loadPdfFromUri(context: Context, uri: Uri): List<PdfPageModel> = withContext(Dispatchers.IO) {
        val resultPages = mutableListOf<PdfPageModel>()
        val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.pdf")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val scale = if (page.width > 0) {
                    DEFAULT_PAGE_WIDTH.toFloat() / page.width.toFloat()
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

                resultPages.add(
                    PdfPageModel(
                        originalBitmap = bitmap,
                        width = bmpWidth,
                        height = bmpHeight
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

        resultPages
    }

    fun createBlankPage(
        width: Int = DEFAULT_PAGE_WIDTH,
        height: Int = DEFAULT_PAGE_HEIGHT
    ): PdfPageModel {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        return PdfPageModel(
            originalBitmap = bitmap,
            width = width,
            height = height
        )
    }

    suspend fun createDocumentFromText(
        title: String,
        bodyText: String,
        addRuledLines: Boolean = false
    ): List<PdfPageModel> = withContext(Dispatchers.Default) {
        val pages = mutableListOf<PdfPageModel>()
        val width = DEFAULT_PAGE_WIDTH
        val height = DEFAULT_PAGE_HEIGHT
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
                canvas.drawLine(margin.toFloat(), yOffset, (width - margin).toFloat(), yOffset, dividerPaint)
                yOffset += 36f
            }

            if (addRuledLines) {
                var ruleY = yOffset + 40f
                while (ruleY < height - margin) {
                    canvas.drawLine(margin.toFloat(), ruleY, (width - margin).toFloat(), ruleY, linePaint)
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

            pages.add(
                PdfPageModel(
                    originalBitmap = bitmap,
                    width = width,
                    height = height
                )
            )

            currentLine = endLine
            isFirstPage = false
            if (currentLine >= totalLines) break
        }

        pages
    }

    suspend fun exportPagesToStream(
        pages: List<PdfPageModel>,
        outputStream: OutputStream
    ): Boolean = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext false

        val pdfDocument = PdfDocument()
        try {
            pages.forEachIndexed { index, pageModel ->
                val pageWidth = pageModel.width
                val pageHeight = pageModel.height

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                drawPageModelOntoCanvas(canvas, pageModel, pageWidth.toFloat(), pageHeight.toFloat())

                pdfDocument.finishPage(pdfPage)
            }

            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            pdfDocument.close()
        }
    }

    suspend fun exportToCacheFile(
        context: Context,
        pages: List<PdfPageModel>,
        fileName: String
    ): File? = withContext(Dispatchers.IO) {
        val cleanName = if (fileName.endsWith(".pdf", ignoreCase = true)) {
            fileName
        } else {
            "$fileName.pdf"
        }
        val outFile = File(context.cacheDir, cleanName)
        val success = FileOutputStream(outFile).use { fos ->
            exportPagesToStream(pages, fos)
        }
        if (success) outFile else null
    }

    private fun drawPageModelOntoCanvas(
        canvas: Canvas,
        pageModel: PdfPageModel,
        targetWidth: Float,
        targetHeight: Float
    ) {
        val destRect = Rect(0, 0, targetWidth.toInt(), targetHeight.toInt())
        canvas.drawBitmap(pageModel.originalBitmap, null, destRect, null)

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
    }
}
