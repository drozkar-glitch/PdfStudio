package cz.mares.pdfstudio.viewmodel

import cz.mares.pdfstudio.model.DrawStroke
import cz.mares.pdfstudio.model.EditTool
import cz.mares.pdfstudio.model.NormalizedPoint
import cz.mares.pdfstudio.model.PdfPageModel
import cz.mares.pdfstudio.model.PdfTextBox
import cz.mares.pdfstudio.model.RedactRect
import cz.mares.pdfstudio.platform.PdfPlatformBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

internal data class PdfUiState(
    val documentTitle: String = "Novy_dokument",
    val pages: List<PdfPageModel> = emptyList(),
    val currentPageIndex: Int = 0,
    val selectedTool: EditTool = EditTool.NONE,
    val selectedColorArgb: Int = 0xFF1432A0.toInt(),
    val strokeWidthRatio: Float = 0.005f,
    val fontSizeRatio: Float = 0.028f,
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

internal class PdfStateHolder(private val bridge: PdfPlatformBridge) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _uiState = MutableStateFlow(PdfUiState())
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private var lastHandledExternalId: Long = -1L

    init {
        scope.launch {
            bridge.externalPdfFlow.collect { externalDoc ->
                if (externalDoc != null && externalDoc.id != lastHandledExternalId) {
                    lastHandledExternalId = externalDoc.id
                    loadExternalDocument(externalDoc.bytes, externalDoc.fileName)
                }
            }
        }

        scope.launch {
            if (bridge.externalPdfFlow.value == null) {
                val blank = withContext(Dispatchers.Default) {
                    bridge.generateBlankPage(1240, 1754)
                }
                if (bridge.externalPdfFlow.value == null && lastHandledExternalId == -1L) {
                    _uiState.update {
                        if (it.pages.isEmpty()) it.copy(pages = listOf(blank)) else it
                    }
                }
            }
        }
    }

    private fun loadExternalDocument(bytes: ByteArray, name: String) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val loaded = withContext(Dispatchers.Default) {
                bridge.loadPdfBytes(bytes, name)
            }
            if (loaded.isNotEmpty()) {
                val cleanTitle = name
                    .removeSuffix(".pdf")
                    .removeSuffix(".PDF")
                    .ifBlank { "Dokument" }
                _uiState.update {
                    it.copy(
                        documentTitle = cleanTitle,
                        pages = loaded,
                        currentPageIndex = 0,
                        selectedTool = EditTool.NONE,
                        isLoading = false,
                        statusMessage = "Načteno stránek: ${loaded.size}"
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

    fun openDocument() {
        bridge.pickPdfFile { bytes, name ->
            bridge.clearExternalPdf()
            loadExternalDocument(bytes, name)
        }
    }

    fun appendDocument() {
        bridge.pickPdfFile { bytes, name ->
            scope.launch {
                _uiState.update { it.copy(isLoading = true, statusMessage = null) }
                val loaded = withContext(Dispatchers.Default) {
                    bridge.loadPdfBytes(bytes, name)
                }
                if (loaded.isNotEmpty()) {
                    _uiState.update { state ->
                        val combined = state.pages + loaded
                        state.copy(
                            pages = combined,
                            currentPageIndex = combined.lastIndex,
                            isLoading = false,
                            statusMessage = "Připojeno stránek: ${loaded.size}"
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
    }

    fun createNewFromText(title: String, bodyText: String, addRuledLines: Boolean) {
        bridge.clearExternalPdf()
        scope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val generated = withContext(Dispatchers.Default) {
                bridge.generatePagesFromText(title, bodyText, addRuledLines)
            }
            val safeTitle = title.trim().ifBlank { "Novy_dokument" }
            _uiState.update {
                it.copy(
                    documentTitle = safeTitle,
                    pages = generated,
                    currentPageIndex = 0,
                    selectedTool = EditTool.NONE,
                    isLoading = false,
                    statusMessage = "Vytvořen nový dokument (${generated.size} str.)"
                )
            }
        }
    }

    fun createBlank(title: String = "Cisty_dokument") {
        bridge.clearExternalPdf()
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val blank = withContext(Dispatchers.Default) {
                bridge.generateBlankPage(1240, 1754)
            }
            _uiState.update {
                it.copy(
                    documentTitle = title.ifBlank { "Cisty_dokument" },
                    pages = listOf(blank),
                    currentPageIndex = 0,
                    selectedTool = EditTool.NONE,
                    isLoading = false,
                    statusMessage = "Vytvořen čistý dokument."
                )
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(documentTitle = title) }
    }

    fun selectPage(index: Int) {
        _uiState.update { state ->
            if (index in state.pages.indices) state.copy(currentPageIndex = index) else state
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

    fun prevPage() {
        _uiState.update { state ->
            if (state.currentPageIndex > 0) {
                state.copy(currentPageIndex = state.currentPageIndex - 1)
            } else {
                state
            }
        }
    }

    fun addBlankPage() {
        scope.launch {
            val state = _uiState.value
            val current = state.pages.getOrNull(state.currentPageIndex)
            val w = current?.width ?: 1240
            val h = current?.height ?: 1754
            val newPage = withContext(Dispatchers.Default) {
                bridge.generateBlankPage(w, h)
            }
            _uiState.update { currentState ->
                val list = currentState.pages.toMutableList()
                val insertIdx = (currentState.currentPageIndex + 1).coerceAtMost(list.size)
                list.add(insertIdx, newPage)
                currentState.copy(
                    pages = list,
                    currentPageIndex = insertIdx,
                    statusMessage = "Přidána čistá stránka."
                )
            }
        }
    }

    fun movePageUp(index: Int) {
        _uiState.update { state ->
            if (index <= 0 || index > state.pages.lastIndex) return@update state
            val list = state.pages.toMutableList()
            val page = list.removeAt(index)
            list.add(index - 1, page)
            val newCurrent = when (state.currentPageIndex) {
                index -> index - 1
                index - 1 -> index
                else -> state.currentPageIndex
            }
            state.copy(pages = list, currentPageIndex = newCurrent)
        }
    }

    fun movePageDown(index: Int) {
        _uiState.update { state ->
            if (index < 0 || index >= state.pages.lastIndex) return@update state
            val list = state.pages.toMutableList()
            val page = list.removeAt(index)
            list.add(index + 1, page)
            val newCurrent = when (state.currentPageIndex) {
                index -> index + 1
                index + 1 -> index
                else -> state.currentPageIndex
            }
            state.copy(pages = list, currentPageIndex = newCurrent)
        }
    }

    fun deletePage(index: Int) {
        _uiState.update { state ->
            if (state.pages.size <= 1) {
                return@update state.copy(statusMessage = "Dokument musí mít alespoň 1 stránku.")
            }
            if (index !in state.pages.indices) return@update state
            val list = state.pages.toMutableList()
            list.removeAt(index)
            val newCurrent = state.currentPageIndex.coerceAtMost(list.lastIndex)
            state.copy(
                pages = list,
                currentPageIndex = newCurrent,
                statusMessage = "Stránka byla smazána."
            )
        }
    }

    fun selectTool(tool: EditTool) {
        _uiState.update { state ->
            val adjustedColor = if (tool == EditTool.HIGHLIGHTER && state.selectedColorArgb == 0xFF000000.toInt()) {
                0xFFFFEB3B.toInt()
            } else {
                state.selectedColorArgb
            }
            state.copy(selectedTool = tool, selectedColorArgb = adjustedColor)
        }
    }

    fun selectColor(colorArgb: Int) {
        _uiState.update { it.copy(selectedColorArgb = colorArgb) }
    }

    fun setStrokeWidth(ratio: Float) {
        _uiState.update { it.copy(strokeWidthRatio = ratio.coerceIn(0.002f, 0.025f)) }
    }

    fun setFontSize(ratio: Float) {
        _uiState.update { it.copy(fontSizeRatio = ratio.coerceIn(0.015f, 0.06f)) }
    }

    fun addStroke(points: List<NormalizedPoint>) {
        if (points.isEmpty()) return
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val isHighlighter = state.selectedTool == EditTool.HIGHLIGHTER
            val widthRatio = if (isHighlighter) {
                (state.strokeWidthRatio * 3.2f).coerceAtMost(0.06f)
            } else {
                state.strokeWidthRatio
            }
            val stroke = DrawStroke(
                id = "s_${Random.nextLong()}",
                points = points,
                colorArgb = state.selectedColorArgb,
                strokeWidthRatio = widthRatio,
                isHighlighter = isHighlighter
            )
            val updated = page.copy(
                strokes = page.strokes + stroke,
                actionHistory = page.actionHistory + "STROKE:${stroke.id}"
            )
            val list = state.pages.toMutableList()
            list[state.currentPageIndex] = updated
            state.copy(pages = list)
        }
    }

    fun addRedaction(left: Float, top: Float, right: Float, bottom: Float) {
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val rect = RedactRect(
                id = "r_${Random.nextLong()}",
                left = left.coerceIn(0f, 1f),
                top = top.coerceIn(0f, 1f),
                right = right.coerceIn(0f, 1f),
                bottom = bottom.coerceIn(0f, 1f),
                colorArgb = 0xFFFFFFFF.toInt()
            )
            val updated = page.copy(
                redactions = page.redactions + rect,
                actionHistory = page.actionHistory + "REDACT:${rect.id}"
            )
            val list = state.pages.toMutableList()
            list[state.currentPageIndex] = updated
            state.copy(pages = list)
        }
    }

    fun addTextBox(text: String, x: Float, y: Float) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val box = PdfTextBox(
                id = "t_${Random.nextLong()}",
                text = clean,
                x = x.coerceIn(0.02f, 0.90f),
                y = y.coerceIn(0.02f, 0.92f),
                colorArgb = state.selectedColorArgb,
                fontSizeRatio = state.fontSizeRatio
            )
            val updated = page.copy(
                textBoxes = page.textBoxes + box,
                actionHistory = page.actionHistory + "TEXT:${box.id}"
            )
            val list = state.pages.toMutableList()
            list[state.currentPageIndex] = updated
            state.copy(pages = list)
        }
    }

    fun moveTextBox(id: String, dxRatio: Float, dyRatio: Float) {
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val updatedBoxes = page.textBoxes.map { box ->
                if (box.id == id) {
                    box.copy(
                        x = (box.x + dxRatio).coerceIn(0.01f, 0.92f),
                        y = (box.y + dyRatio).coerceIn(0.01f, 0.95f)
                    )
                } else {
                    box
                }
            }
            val updated = page.copy(textBoxes = updatedBoxes)
            val list = state.pages.toMutableList()
            list[state.currentPageIndex] = updated
            state.copy(pages = list)
        }
    }

    fun updateTextBox(id: String, newText: String, colorArgb: Int, fontSizeRatio: Float) {
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val clean = newText.trim()
            val updatedBoxes = if (clean.isEmpty()) {
                page.textBoxes.filterNot { it.id == id }
            } else {
                page.textBoxes.map { box ->
                    if (box.id == id) {
                        box.copy(text = clean, colorArgb = colorArgb, fontSizeRatio = fontSizeRatio)
                    } else {
                        box
                    }
                }
            }
            val updated = page.copy(textBoxes = updatedBoxes)
            val list = state.pages.toMutableList()
            list[state.currentPageIndex] = updated
            state.copy(pages = list)
        }
    }

    fun deleteTextBox(id: String) {
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val updated = page.copy(
                textBoxes = page.textBoxes.filterNot { it.id == id },
                actionHistory = page.actionHistory.filterNot { it == "TEXT:$id" }
            )
            val list = state.pages.toMutableList()
            list[state.currentPageIndex] = updated
            state.copy(pages = list)
        }
    }

    fun undo() {
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val lastAction = page.actionHistory.lastOrNull() ?: return@update state
            val parts = lastAction.split(":", limit = 2)
            if (parts.size != 2) return@update state
            val type = parts[0]
            val id = parts[1]

            val updated = when (type) {
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
            val list = state.pages.toMutableList()
            list[state.currentPageIndex] = updated
            state.copy(pages = list)
        }
    }

    fun clearPage() {
        _uiState.update { state ->
            val page = state.pages.getOrNull(state.currentPageIndex) ?: return@update state
            val updated = page.copy(
                strokes = emptyList(),
                redactions = emptyList(),
                textBoxes = emptyList(),
                actionHistory = emptyList()
            )
            val list = state.pages.toMutableList()
            list[state.currentPageIndex] = updated
            state.copy(pages = list, statusMessage = "Úpravy na stránce byly vymazány.")
        }
    }

    fun savePdf() {
        scope.launch {
            val state = _uiState.value
            if (state.pages.isEmpty()) return@launch
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val bytes = withContext(Dispatchers.Default) {
                bridge.exportPdfBytes(state.pages)
            }
            val fileName = if (state.documentTitle.endsWith(".pdf", ignoreCase = true)) {
                state.documentTitle
            } else {
                "${state.documentTitle}.pdf"
            }
            bridge.savePdfFile(fileName, bytes)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun sharePdf() {
        scope.launch {
            val state = _uiState.value
            if (state.pages.isEmpty()) return@launch
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val bytes = withContext(Dispatchers.Default) {
                bridge.exportPdfBytes(state.pages)
            }
            val fileName = if (state.documentTitle.endsWith(".pdf", ignoreCase = true)) {
                state.documentTitle
            } else {
                "${state.documentTitle}.pdf"
            }
            bridge.shareFile(fileName, bytes)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun printPdf() {
        scope.launch {
            val state = _uiState.value
            if (state.pages.isEmpty()) return@launch
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val bytes = withContext(Dispatchers.Default) {
                bridge.exportPdfBytes(state.pages)
            }
            val fileName = if (state.documentTitle.endsWith(".pdf", ignoreCase = true)) {
                state.documentTitle
            } else {
                "${state.documentTitle}.pdf"
            }
            bridge.printFile(fileName, bytes)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
