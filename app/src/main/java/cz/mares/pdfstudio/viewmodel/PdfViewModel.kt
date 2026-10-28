package cz.mares.pdfstudio.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.mares.pdfstudio.model.DrawStroke
import cz.mares.pdfstudio.model.EditTool
import cz.mares.pdfstudio.model.NormalizedPoint
import cz.mares.pdfstudio.model.PdfPageModel
import cz.mares.pdfstudio.model.PdfTextBox
import cz.mares.pdfstudio.model.RedactRect
import cz.mares.pdfstudio.util.PdfHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PdfUiState(
    val documentTitle: String = "Novy_dokument",
    val pages: List<PdfPageModel> = emptyList(),
    val currentPageIndex: Int = 0,
    val selectedTool: EditTool = EditTool.NONE,
    val selectedColorArgb: Int = Color.rgb(20, 50, 160),
    val strokeWidthRatio: Float = 0.005f,
    val fontSizeRatio: Float = 0.028f,
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

class PdfViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        PdfUiState(
            pages = listOf(PdfHelper.createBlankPage())
        )
    )
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    fun openPdfFromUri(context: Context, uri: Uri, fileNameHint: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val loadedPages = PdfHelper.loadPdfFromUri(context, uri)
            if (loadedPages.isNotEmpty()) {
                val cleanTitle = fileNameHint
                    ?.removeSuffix(".pdf")
                    ?.removeSuffix(".PDF")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Dokument_${System.currentTimeMillis() / 1000}"

                _uiState.update {
                    it.copy(
                        documentTitle = cleanTitle,
                        pages = loadedPages,
                        currentPageIndex = 0,
                        isLoading = false,
                        statusMessage = "Načteno stránek: ${loadedPages.size}"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Soubor PDF se nepodařilo načíst."
                    )
                }
            }
        }
    }

    fun appendPdfFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val loadedPages = PdfHelper.loadPdfFromUri(context, uri)
            if (loadedPages.isNotEmpty()) {
                _uiState.update { state ->
                    val combined = state.pages + loadedPages
                    state.copy(
                        pages = combined,
                        currentPageIndex = combined.lastIndex,
                        isLoading = false,
                        statusMessage = "Připojeno stránek: ${loadedPages.size}"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Další PDF se nepodařilo připojit."
                    )
                }
            }
        }
    }

    fun createNewDocumentFromText(title: String, bodyText: String, addRuledLines: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val generatedPages = PdfHelper.createDocumentFromText(
                title = title,
                bodyText = bodyText,
                addRuledLines = addRuledLines
            )
            val safeTitle = title.trim().ifBlank { "Novy_dokument" }
            _uiState.update {
                it.copy(
                    documentTitle = safeTitle,
                    pages = generatedPages,
                    currentPageIndex = 0,
                    selectedTool = EditTool.NONE,
                    isLoading = false,
                    statusMessage = "Vytvořen nový dokument (${generatedPages.size} str.)"
                )
            }
        }
    }

    fun createBlankDocument(title: String = "Cisty_dokument") {
        val blankPage = PdfHelper.createBlankPage()
        _uiState.update {
            it.copy(
                documentTitle = title,
                pages = listOf(blankPage),
                currentPageIndex = 0,
                selectedTool = EditTool.NONE,
                statusMessage = "Vytvořen čistý dokument."
            )
        }
    }

    fun updateDocumentTitle(newTitle: String) {
        _uiState.update { it.copy(documentTitle = newTitle) }
    }

    fun selectPage(index: Int) {
        _uiState.update { state ->
            if (index in state.pages.indices) {
                state.copy(currentPageIndex = index)
            } else {
                state
            }
        }
    }

    fun nextPage() {
        _uiState.update { state ->
            if (state.currentPageIndex < state.pages.lastIndex) {
                state.copy(currentPageIndex = state.currentPageIndex + 1)
            } else {
                state
            }
        }
    }

    fun previousPage() {
        _uiState.update { state ->
            if (state.currentPageIndex > 0) {
                state.copy(currentPageIndex = state.currentPageIndex - 1)
            } else {
                state
            }
        }
    }

    fun addBlankPageAfterCurrent() {
        _uiState.update { state ->
            val currentPage = state.pages.getOrNull(state.currentPageIndex)
            val width = currentPage?.width ?: 1240
            val height = currentPage?.height ?: 1754
            val newPage = PdfHelper.createBlankPage(width, height)
            val mutablePages = state.pages.toMutableList()
            val insertIndex = (state.currentPageIndex + 1).coerceAtMost(mutablePages.size)
            mutablePages.add(insertIndex, newPage)
            state.copy(
                pages = mutablePages,
                currentPageIndex = insertIndex,
                statusMessage = "Přidána nová čistá stránka."
            )
        }
    }

    fun movePageUp(index: Int) {
        _uiState.update { state ->
            if (index <= 0 || index > state.pages.lastIndex) return@update state
            val mutablePages = state.pages.toMutableList()
            val page = mutablePages.removeAt(index)
            mutablePages.add(index - 1, page)
            val newCurrent = when (state.currentPageIndex) {
                index -> index - 1
                index - 1 -> index
                else -> state.currentPageIndex
            }
            state.copy(pages = mutablePages, currentPageIndex = newCurrent)
        }
    }

    fun movePageDown(index: Int) {
        _uiState.update { state ->
            if (index < 0 || index >= state.pages.lastIndex) return@update state
            val mutablePages = state.pages.toMutableList()
            val page = mutablePages.removeAt(index)
            mutablePages.add(index + 1, page)
            val newCurrent = when (state.currentPageIndex) {
                index -> index + 1
                index + 1 -> index
                else -> state.currentPageIndex
            }
            state.copy(pages = mutablePages, currentPageIndex = newCurrent)
        }
    }

    fun deletePage(index: Int) {
        _uiState.update { state ->
            if (state.pages.size <= 1) {
                return@update state.copy(statusMessage = "Dokument musí obsahovat alespoň 1 stránku.")
            }
            if (index !in state.pages.indices) return@update state
            val mutablePages = state.pages.toMutableList()
            mutablePages.removeAt(index)
            val newCurrent = state.currentPageIndex.coerceAtMost(mutablePages.lastIndex)
            state.copy(
                pages = mutablePages,
                currentPageIndex = newCurrent,
                statusMessage = "Stránka byla odstraněna."
            )
        }
    }

    fun selectTool(tool: EditTool) {
        _uiState.update { state ->
            val adjustedColor = if (tool == EditTool.HIGHLIGHTER && state.selectedColorArgb == Color.BLACK) {
                Color.rgb(255, 235, 59)
            } else {
                state.selectedColorArgb
            }
            state.copy(selectedTool = tool, selectedColorArgb = adjustedColor)
        }
    }

    fun selectColor(colorArgb: Int) {
        _uiState.update { it.copy(selectedColorArgb = colorArgb) }
    }

    fun setStrokeWidthRatio(ratio: Float) {
        _uiState.update { it.copy(strokeWidthRatio = ratio.coerceIn(0.002f, 0.04f)) }
    }

    fun setFontSizeRatio(ratio: Float) {
        _uiState.update { it.copy(fontSizeRatio = ratio.coerceIn(0.015f, 0.08f)) }
    }

    fun addStrokeToCurrentPage(points: List<NormalizedPoint>) {
        if (points.isEmpty()) return
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val isHighlighter = state.selectedTool == EditTool.HIGHLIGHTER
            val widthRatio = if (isHighlighter) {
                (state.strokeWidthRatio * 3.2f).coerceAtMost(0.06f)
            } else {
                state.strokeWidthRatio
            }
            val newStroke = DrawStroke(
                points = points,
                colorArgb = state.selectedColorArgb,
                strokeWidthRatio = widthRatio,
                isHighlighter = isHighlighter
            )
            val updatedPage = page.copy(
                strokes = page.strokes + newStroke,
                actionHistory = page.actionHistory + "STROKE:${newStroke.id}"
            )
            val mutablePages = state.pages.toMutableList()
            mutablePages[state.currentPageIndex] = updatedPage
            state.copy(pages = mutablePages)
        }
    }

    fun addRedactionToCurrentPage(left: Float, top: Float, right: Float, bottom: Float) {
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val newRect = RedactRect(
                left = left.coerceIn(0f, 1f),
                top = top.coerceIn(0f, 1f),
                right = right.coerceIn(0f, 1f),
                bottom = bottom.coerceIn(0f, 1f),
                colorArgb = Color.WHITE
            )
            val updatedPage = page.copy(
                redactions = page.redactions + newRect,
                actionHistory = page.actionHistory + "REDACT:${newRect.id}"
            )
            val mutablePages = state.pages.toMutableList()
            mutablePages[state.currentPageIndex] = updatedPage
            state.copy(pages = mutablePages)
        }
    }

    fun addTextBoxToCurrentPage(text: String, x: Float, y: Float) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val newTextBox = PdfTextBox(
                text = cleanText,
                x = x.coerceIn(0.02f, 0.90f),
                y = y.coerceIn(0.02f, 0.92f),
                colorArgb = state.selectedColorArgb,
                fontSizeRatio = state.fontSizeRatio
            )
            val updatedPage = page.copy(
                textBoxes = page.textBoxes + newTextBox,
                actionHistory = page.actionHistory + "TEXT:${newTextBox.id}"
            )
            val mutablePages = state.pages.toMutableList()
            mutablePages[state.currentPageIndex] = updatedPage
            state.copy(pages = mutablePages)
        }
    }

    fun moveTextBoxOnCurrentPage(textBoxId: String, deltaXRatio: Float, deltaYRatio: Float) {
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val updatedBoxes = page.textBoxes.map { box ->
                if (box.id == textBoxId) {
                    box.copy(
                        x = (box.x + deltaXRatio).coerceIn(0.01f, 0.92f),
                        y = (box.y + deltaYRatio).coerceIn(0.01f, 0.95f)
                    )
                } else {
                    box
                }
            }
            val updatedPage = page.copy(textBoxes = updatedBoxes)
            val mutablePages = state.pages.toMutableList()
            mutablePages[state.currentPageIndex] = updatedPage
            state.copy(pages = mutablePages)
        }
    }

    fun updateTextBoxContent(textBoxId: String, newText: String, newColorArgb: Int, newFontSizeRatio: Float) {
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val clean = newText.trim()
            val updatedBoxes = if (clean.isEmpty()) {
                page.textBoxes.filterNot { it.id == textBoxId }
            } else {
                page.textBoxes.map { box ->
                    if (box.id == textBoxId) {
                        box.copy(
                            text = clean,
                            colorArgb = newColorArgb,
                            fontSizeRatio = newFontSizeRatio
                        )
                    } else {
                        box
                    }
                }
            }
            val updatedPage = page.copy(textBoxes = updatedBoxes)
            val mutablePages = state.pages.toMutableList()
            mutablePages[state.currentPageIndex] = updatedPage
            state.copy(pages = mutablePages)
        }
    }

    fun deleteTextBoxOnCurrentPage(textBoxId: String) {
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val updatedPage = page.copy(
                textBoxes = page.textBoxes.filterNot { it.id == textBoxId },
                actionHistory = page.actionHistory.filterNot { it == "TEXT:$textBoxId" }
            )
            val mutablePages = state.pages.toMutableList()
            mutablePages[state.currentPageIndex] = updatedPage
            state.copy(pages = mutablePages)
        }
    }

    fun undoOnCurrentPage() {
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val lastAction = page.actionHistory.lastOrNull() ?: return@update state
            val parts = lastAction.split(":", limit = 2)
            if (parts.size != 2) return@update state
            val type = parts[0]
            val id = parts[1]

            val updatedPage = when (type) {
                "STROKE" -> page.copy(
                    strokes = page.strokes.filterNot { it.id == id },
                    actionHistory = page.actionHistory.dropLast(1)
                )
                "REDACT" -> page.copy(
                    redactions = page.redactions.filterNot { it.id == id },
                    actionHistory = page.actionHistory.dropLast(1)
                )
                "TEXT" -> page.copy(
                    textBoxes = page.textBoxes.filterNot { it.id == id },
                    actionHistory = page.actionHistory.dropLast(1)
                )
                else -> page.copy(actionHistory = page.actionHistory.dropLast(1))
            }

            val mutablePages = state.pages.toMutableList()
            mutablePages[state.currentPageIndex] = updatedPage
            state.copy(pages = mutablePages)
        }
    }

    fun clearCurrentPageEdits() {
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val updatedPage = page.copy(
                strokes = emptyList(),
                redactions = emptyList(),
                textBoxes = emptyList(),
                actionHistory = emptyList()
            )
            val mutablePages = state.pages.toMutableList()
            mutablePages[state.currentPageIndex] = updatedPage
            state.copy(
                pages = mutablePages,
                statusMessage = "Úpravy na aktuální stránce byly vymazány."
            )
        }
    }

    fun savePdfToUri(context: Context, targetUri: Uri) {
        viewModelScope.launch {
            val currentPages = _uiState.value.pages
            if (currentPages.isEmpty()) return@launch

            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val outputStream = context.contentResolver.openOutputStream(targetUri)
                val success = if (outputStream != null) {
                    outputStream.use { stream ->
                        PdfHelper.exportPagesToStream(currentPages, stream)
                    }
                } else {
                    false
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = if (success) {
                            "PDF bylo úspěšně uloženo."
                        } else {
                            "Chyba při ukládání PDF."
                        }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Chyba při ukládání: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun sharePdf(context: Context) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.pages.isEmpty()) return@launch

            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val file = PdfHelper.exportToCacheFile(
                context = context,
                pages = state.pages,
                fileName = state.documentTitle.ifBlank { "Dokument" }
            )

            _uiState.update { it.copy(isLoading = false) }

            if (file != null && file.exists()) {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
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
                    _uiState.update {
                        it.copy(statusMessage = "Nepodařilo se otevřít sdílení: ${e.localizedMessage}")
                    }
                }
            } else {
                _uiState.update {
                    it.copy(statusMessage = "Nepodařilo se připravit PDF pro sdílení.")
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
