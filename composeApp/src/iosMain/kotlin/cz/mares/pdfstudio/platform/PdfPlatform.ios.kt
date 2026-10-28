package cz.mares.pdfstudio.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import cz.mares.pdfstudio.model.PdfPageModel
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsBeginPDFContextToData
import platform.UIKit.UIGraphicsBeginPDFPageWithInfo
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UILabel
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIRectFill
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len <= 0) return ByteArray(0)
    val ptr = bytes ?: return ByteArray(0)
    val result = ByteArray(len)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), ptr, length)
    }
    return result
}

private fun colorFromArgb(argb: Int, alphaMultiplier: Double = 1.0): UIColor {
    val a = ((argb ushr 24) and 0xFF) / 255.0
    val r = ((argb ushr 16) and 0xFF) / 255.0
    val g = ((argb ushr 8) and 0xFF) / 0xFF.toDouble()
    val b = (argb and 0xFF) / 255.0
    return UIColor.colorWithRed(r, green = g, blue = b, alpha = a * alphaMultiplier)
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}

private class IosDocumentPickerDelegate(
    private val onPicked: (ByteArray, String) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val securityStarted = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url)
            if (data != null) {
                val fileName = url.lastPathComponent ?: "Dokument.pdf"
                onPicked(data.toByteArray(), fileName)
            }
        } finally {
            if (securityStarted) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }
}

internal object IosBridgeHolder {
    private val _externalPdfFlow = MutableStateFlow<ExternalPdfDocument?>(null)
    val externalPdfFlow: StateFlow<ExternalPdfDocument?> = _externalPdfFlow.asStateFlow()

    fun clearExternalPdf() {
        _externalPdfFlow.value = null
    }
}

@OptIn(ExperimentalForeignApi::class)
internal class IosPdfPlatformBridge : PdfPlatformBridge {

    private val defaultPageWidth = 1240
    private val defaultPageHeight = 1754
    private var activePickerDelegate: IosDocumentPickerDelegate? = null

    override val externalPdfFlow: StateFlow<ExternalPdfDocument?>
        get() = IosBridgeHolder.externalPdfFlow

    override fun clearExternalPdf() {
        IosBridgeHolder.clearExternalPdf()
    }

    override suspend fun loadPdfBytes(bytes: ByteArray, fileNameHint: String?): List<PdfPageModel> {
        val pages = mutableListOf<PdfPageModel>()
        val nsData = bytes.toNSData()
        val pdfDoc = PDFDocument(data = nsData) ?: return emptyList()
        val pageCount = pdfDoc.pageCount.toInt()

        for (i in 0 until pageCount) {
            val pdfPage = pdfDoc.pageAtIndex(i.toULong()) ?: continue
            val bounds = pdfPage.boundsForBox(kPDFDisplayBoxMediaBox)
            val (origW, origH) = bounds.useContents {
                size.width to size.height
            }

            val scale = if (origW > 0.0) {
                (defaultPageWidth.toDouble() / origW).coerceIn(1.0, 3.0)
            } else {
                2.0
            }

            val targetW = (origW * scale).toInt().coerceAtLeast(100)
            val targetH = (origH * scale).toInt().coerceAtLeast(100)

            val thumbImage = pdfPage.thumbnailOfSize(
                CGSizeMake(targetW.toDouble(), targetH.toDouble()),
                forBox = kPDFDisplayBoxMediaBox
            )
            val pngData = UIImagePNGRepresentation(thumbImage)
            if (pngData != null) {
                pages.add(
                    PdfPageModel(
                        id = "p_${Random.nextLong()}",
                        width = targetW,
                        height = targetH,
                        imageBytes = pngData.toByteArray()
                    )
                )
            }
        }

        return pages
    }

    override suspend fun generateBlankPage(width: Int, height: Int): PdfPageModel {
        val safeW = width.coerceAtLeast(100)
        val safeH = height.coerceAtLeast(100)

        UIGraphicsBeginImageContextWithOptions(
            CGSizeMake(safeW.toDouble(), safeH.toDouble()),
            true,
            1.0
        )
        UIColor.whiteColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, safeW.toDouble(), safeH.toDouble()))
        val img = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        val bytes = if (img != null) {
            UIImagePNGRepresentation(img)?.toByteArray() ?: ByteArray(0)
        } else {
            ByteArray(0)
        }

        return PdfPageModel(
            id = "p_${Random.nextLong()}",
            width = safeW,
            height = safeH,
            imageBytes = bytes
        )
    }

    override suspend fun generatePagesFromText(
        title: String,
        bodyText: String,
        addRuledLines: Boolean
    ): List<PdfPageModel> {
        val width = defaultPageWidth
        val height = defaultPageHeight
        val widthD = width.toDouble()
        val heightD = height.toDouble()
        val margin = 96.0
        val contentWidth = widthD - (margin * 2.0)

        val lines = if (bodyText.isBlank()) listOf("") else bodyText.lines()
        val linesPerPage = 24
        val chunks = lines.chunked(linesPerPage).ifEmpty { listOf(emptyList()) }
        val pages = mutableListOf<PdfPageModel>()

        chunks.forEachIndexed { index, chunkLines ->
            UIGraphicsBeginImageContextWithOptions(
                CGSizeMake(widthD, heightD),
                true,
                1.0
            )
            UIColor.whiteColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, widthD, heightD))

            var yOffset = margin

            if (index == 0 && title.isNotBlank()) {
                val titleLabel = UILabel(
                    frame = CGRectMake(margin, yOffset, contentWidth, 120.0)
                ).apply {
                    text = title
                    font = UIFont.boldSystemFontOfSize(48.0)
                    textColor = colorFromArgb(0xFF142850.toInt())
                    numberOfLines = 2
                }
                titleLabel.drawTextInRect(CGRectMake(margin, yOffset, contentWidth, 120.0))
                yOffset += 130.0
            }

            if (addRuledLines) {
                UIColor.colorWithRed(0.88, green = 0.90, blue = 0.94, alpha = 1.0).setStroke()
                var ruleY = yOffset + 40.0
                while (ruleY < heightD - margin) {
                    val linePath = UIBezierPath()
                    linePath.lineWidth = 2.0
                    linePath.moveToPoint(CGPointMake(margin, ruleY))
                    linePath.addLineToPoint(CGPointMake(widthD - margin, ruleY))
                    linePath.stroke()
                    ruleY += 54.0
                }
            }

            val pageBody = chunkLines.joinToString("\n")
            if (pageBody.isNotBlank()) {
                val bodyHeight = (heightD - margin) - yOffset
                val bodyLabel = UILabel(
                    frame = CGRectMake(margin, yOffset, contentWidth, bodyHeight)
                ).apply {
                    text = pageBody
                    font = UIFont.systemFontOfSize(32.0)
                    textColor = UIColor.blackColor
                    numberOfLines = 0
                }
                bodyLabel.drawTextInRect(CGRectMake(margin, yOffset, contentWidth, bodyHeight))
            }

            val img = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()

            val pngBytes = if (img != null) {
                UIImagePNGRepresentation(img)?.toByteArray() ?: ByteArray(0)
            } else {
                ByteArray(0)
            }

            pages.add(
                PdfPageModel(
                    id = "p_${Random.nextLong()}",
                    width = width,
                    height = height,
                    imageBytes = pngBytes
                )
            )
        }

        return pages
    }

    override suspend fun exportPdfBytes(pages: List<PdfPageModel>): ByteArray {
        val pdfData = NSMutableData()
        UIGraphicsBeginPDFContextToData(
            pdfData,
            CGRectMake(0.0, 0.0, defaultPageWidth.toDouble(), defaultPageHeight.toDouble()),
            null
        )

        for (page in pages) {
            val w = page.width.coerceAtLeast(100).toDouble()
            val h = page.height.coerceAtLeast(100).toDouble()
            val pageRect = CGRectMake(0.0, 0.0, w, h)

            UIGraphicsBeginPDFPageWithInfo(pageRect, null)

            val baseImage = UIImage.imageWithData(page.imageBytes.toNSData())
            baseImage?.drawInRect(pageRect)

            for (rect in page.redactions) {
                colorFromArgb(rect.colorArgb, 1.0).setFill()
                val left = min(rect.left, rect.right).toDouble() * w
                val right = max(rect.left, rect.right).toDouble() * w
                val top = min(rect.top, rect.bottom).toDouble() * h
                val bottom = max(rect.top, rect.bottom).toDouble() * h
                UIRectFill(CGRectMake(left, top, right - left, bottom - top))
            }

            for (stroke in page.strokes) {
                if (stroke.points.isEmpty()) continue
                val alpha = if (stroke.isHighlighter) 0.38 else 1.0
                val strokeW = stroke.strokeWidthRatio.toDouble() * w

                if (stroke.points.size == 1) {
                    val pt = stroke.points.first()
                    colorFromArgb(stroke.colorArgb, alpha).setFill()
                    val circle = UIBezierPath.bezierPathWithOvalInRect(
                        CGRectMake(
                            pt.x.toDouble() * w - strokeW / 2.0,
                            pt.y.toDouble() * h - strokeW / 2.0,
                            strokeW,
                            strokeW
                        )
                    )
                    circle.fill()
                } else {
                    colorFromArgb(stroke.colorArgb, alpha).setStroke()
                    val path = UIBezierPath()
                    path.lineWidth = strokeW
                    val first = stroke.points.first()
                    path.moveToPoint(CGPointMake(first.x.toDouble() * w, first.y.toDouble() * h))
                    for (i in 1 until stroke.points.size) {
                        val pt = stroke.points[i]
                        path.addLineToPoint(CGPointMake(pt.x.toDouble() * w, pt.y.toDouble() * h))
                    }
                    path.stroke()
                }
            }

            for (textBox in page.textBoxes) {
                val startX = textBox.x.toDouble() * w
                val startY = textBox.y.toDouble() * h
                val availW = (w - startX).coerceAtLeast(80.0)
                val availH = (h - startY).coerceAtLeast(50.0)

                val label = UILabel(
                    frame = CGRectMake(startX, startY, availW, availH)
                ).apply {
                    text = textBox.text
                    font = UIFont.boldSystemFontOfSize(textBox.fontSizeRatio.toDouble() * w)
                    textColor = colorFromArgb(textBox.colorArgb, 1.0)
                    numberOfLines = 0
                }
                label.sizeToFit()
                val fittedRect = label.frame.useContents {
                    CGRectMake(startX, startY, size.width.coerceAtLeast(40.0), size.height.coerceAtLeast(24.0))
                }
                label.drawTextInRect(fittedRect)
            }
        }

        UIGraphicsEndPDFContext()
        return pdfData.toByteArray()
    }

    override fun shareFile(fileName: String, bytes: ByteArray) {
        val cleanName = if (fileName.endsWith(".pdf", ignoreCase = true)) {
            fileName
        } else {
            "$fileName.pdf"
        }
        val filePath = NSTemporaryDirectory() + cleanName
        val fileUrl = NSURL.fileURLWithPath(filePath)
        bytes.toNSData().writeToURL(fileUrl, atomically = true)

        val controller = topViewController() ?: return
        val activityVC = UIActivityViewController(
            activityItems = listOf(fileUrl),
            applicationActivities = null
        )
        activityVC.popoverPresentationController?.sourceView = controller.view
        controller.presentViewController(activityVC, animated = true, completion = null)
    }

    override fun printFile(fileName: String, bytes: ByteArray) {
        val cleanName = if (fileName.endsWith(".pdf", ignoreCase = true)) {
            fileName
        } else {
            "$fileName.pdf"
        }
        val filePath = NSTemporaryDirectory() + cleanName
        val fileUrl = NSURL.fileURLWithPath(filePath)
        bytes.toNSData().writeToURL(fileUrl, atomically = true)

        val printController = UIPrintInteractionController.sharedPrintController
        printController.printingItem = fileUrl
        printController.presentAnimated(true, completionHandler = null)
    }

    override fun pickPdfFile(onFileSelected: (ByteArray, String) -> Unit) {
        val controller = topViewController() ?: return
        val delegate = IosDocumentPickerDelegate(onFileSelected)
        activePickerDelegate = delegate

        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("com.adobe.pdf"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
        )
        picker.delegate = delegate
        controller.presentViewController(picker, animated = true, completion = null)
    }

    override fun savePdfFile(fileName: String, bytes: ByteArray) {
        shareFile(fileName, bytes)
    }
}

internal actual fun getPlatformBridge(): PdfPlatformBridge = IosPdfPlatformBridge()

internal actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap {
    return try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        ImageBitmap(10, 10)
    }
}
