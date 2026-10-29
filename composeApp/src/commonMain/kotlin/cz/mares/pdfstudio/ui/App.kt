package cz.mares.pdfstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cz.mares.pdfstudio.model.EditTool
import cz.mares.pdfstudio.model.NormalizedPoint
import cz.mares.pdfstudio.model.PdfPageModel
import cz.mares.pdfstudio.model.PdfTextBox
import cz.mares.pdfstudio.platform.decodeImageBytes
import cz.mares.pdfstudio.platform.getPlatformBridge
import cz.mares.pdfstudio.viewmodel.PdfStateHolder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme {
        val stateHolder = remember { PdfStateHolder(getPlatformBridge()) }
        val state by stateHolder.uiState.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        var showCreateDialog by remember { mutableStateOf(false) }
        var showPageManagerDialog by remember { mutableStateOf(false) }
        var pendingTextTapPoint by remember { mutableStateOf<NormalizedPoint?>(null) }
        var editingTextBox by remember { mutableStateOf<PdfTextBox?>(null) }

        var isTopBarVisible by remember { mutableStateOf(true) }
        var isBottomBarVisible by remember { mutableStateOf(true) }

        var zoomScale by remember { mutableFloatStateOf(1f) }
        var panOffset by remember { mutableStateOf(Offset.Zero) }

        LaunchedEffect(state.statusMessage) {
            val msg = state.statusMessage
            if (!msg.isNullOrBlank()) {
                snackbarHostState.showSnackbar(msg)
                stateHolder.clearMessage()
            }
        }

        val availableColors = listOf(
            Color(0xFF1432A0),
            Color(0xFF000000),
            Color(0xFFD32F2F),
            Color(0xFF2E7D32),
            Color(0xFFFFEB3B),
            Color(0xFFFF9800)
        )

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (isTopBarVisible) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = state.documentTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (state.pages.isNotEmpty()) {
                                        "Strana ${state.currentPageIndex + 1} z ${state.pages.size}"
                                    } else {
                                        "Načítání..."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        actions = {
                            IconButton(onClick = {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                stateHolder.openDocument()
                            }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Otevřít PDF")
                            }
                            IconButton(onClick = { showCreateDialog = true }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "Nové PDF")
                            }
                            IconButton(onClick = { showPageManagerDialog = true }) {
                                Icon(Icons.Default.Layers, contentDescription = "Správa stránek")
                            }
                            IconButton(onClick = { stateHolder.printPdf() }) {
                                Icon(Icons.Default.Print, contentDescription = "Tisk PDF")
                            }
                            IconButton(onClick = { stateHolder.savePdf() }) {
                                Icon(Icons.Default.Save, contentDescription = "Uložit PDF")
                            }
                            IconButton(onClick = { stateHolder.sharePdf() }) {
                                Icon(Icons.Default.Share, contentDescription = "Sdílet PDF")
                            }
                            IconButton(onClick = {
                                isTopBarVisible = false
                                isBottomBarVisible = false
                            }) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Celá obrazovka")
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (isBottomBarVisible) {
                    Surface(
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = state.selectedTool == EditTool.NONE,
                                    onClick = { stateHolder.selectTool(EditTool.NONE) },
                                    label = { Text("Posun / Zoom") },
                                    leadingIcon = {
                                        Icon(Icons.Default.PanTool, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                                FilterChip(
                                    selected = state.selectedTool == EditTool.PEN,
                                    onClick = { stateHolder.selectTool(EditTool.PEN) },
                                    label = { Text("Pero / Podpis") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                                FilterChip(
                                    selected = state.selectedTool == EditTool.HIGHLIGHTER,
                                    onClick = { stateHolder.selectTool(EditTool.HIGHLIGHTER) },
                                    label = { Text("Zvýrazňovač") },
                                    leadingIcon = {
                                        Icon(Icons.Default.BorderColor, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                                FilterChip(
                                    selected = state.selectedTool == EditTool.REDACT,
                                    onClick = { stateHolder.selectTool(EditTool.REDACT) },
                                    label = { Text("Zabělení") },
                                    leadingIcon = {
                                        Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                                FilterChip(
                                    selected = state.selectedTool == EditTool.TEXT,
                                    onClick = { stateHolder.selectTool(EditTool.TEXT) },
                                    label = { Text("Vložit text") },
                                    leadingIcon = {
                                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }

                            if (state.selectedTool == EditTool.PEN ||
                                state.selectedTool == EditTool.HIGHLIGHTER ||
                                state.selectedTool == EditTool.TEXT
                            ) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        availableColors.forEach { color ->
                                            val isSelected = state.selectedColorArgb == color.toArgb()
                                            Box(
                                                modifier = Modifier
                                                    .size(if (isSelected) 28.dp else 22.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(
                                                        width = if (isSelected) 3.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { stateHolder.selectColor(color.toArgb()) }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    if (state.selectedTool == EditTool.TEXT) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Vel.:", style = MaterialTheme.typography.labelSmall)
                                            Slider(
                                                value = state.fontSizeRatio,
                                                onValueChange = { stateHolder.setFontSize(it) },
                                                valueRange = 0.015f..0.06f,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Tl.:", style = MaterialTheme.typography.labelSmall)
                                            Slider(
                                                value = state.strokeWidthRatio,
                                                onValueChange = { stateHolder.setStrokeWidth(it) },
                                                valueRange = 0.002f..0.025f,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                            stateHolder.prevPage()
                                        },
                                        enabled = state.currentPageIndex > 0
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Předchozí strana")
                                    }
                                    Text(
                                        text = if (state.pages.isNotEmpty()) {
                                            "${state.currentPageIndex + 1} / ${state.pages.size}"
                                        } else {
                                            "0 / 0"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(
                                        onClick = {
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                            stateHolder.nextPage()
                                        },
                                        enabled = state.currentPageIndex < state.pages.lastIndex
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Další strana")
                                    }
                                    IconButton(onClick = { stateHolder.addBlankPage() }) {
                                        Icon(Icons.Default.NoteAdd, contentDescription = "Přidat čistou stránku")
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (zoomScale > 1.01f || panOffset != Offset.Zero) {
                                        IconButton(
                                            onClick = {
                                                zoomScale = 1f
                                                panOffset = Offset.Zero
                                            }
                                        ) {
                                            Icon(Icons.Default.ZoomOutMap, contentDescription = "Resetovat přiblížení")
                                        }
                                    }
                                    IconButton(
                                        onClick = { stateHolder.undo() },
                                        enabled = state.pages.getOrNull(state.currentPageIndex)
                                            ?.actionHistory?.isNotEmpty() == true
                                    ) {
                                        Icon(Icons.Default.Undo, contentDescription = "Krok zpět")
                                    }
                                    IconButton(onClick = { stateHolder.clearPage() }) {
                                        Icon(Icons.Default.ClearAll, contentDescription = "Vymazat úpravy strany")
                                    }
                                    IconButton(onClick = { isBottomBarVisible = false }) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Skrýt spodní lištu")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFFE4E7EC)),
                contentAlignment = Alignment.Center
            ) {
                val currentPage = state.pages.getOrNull(state.currentPageIndex)
                if (currentPage != null) {
                    PdfPageEditorCanvas(
                        page = currentPage,
                        selectedTool = state.selectedTool,
                        selectedColorArgb = state.selectedColorArgb,
                        strokeWidthRatio = state.strokeWidthRatio,
                        zoomScale = zoomScale,
                        panOffset = panOffset,
                        onTransform = { zoomChange, panChange ->
                            val newScale = (zoomScale * zoomChange).coerceIn(1f, 4.5f)
                            zoomScale = newScale
                            panOffset = if (newScale <= 1.01f) Offset.Zero else panOffset + panChange
                        },
                        onAddStroke = { points -> stateHolder.addStroke(points) },
                        onAddRedaction = { l, t, r, b -> stateHolder.addRedaction(l, t, r, b) },
                        onTapForNewText = { point -> pendingTextTapPoint = point },
                        onMoveTextBox = { id, dx, dy -> stateHolder.moveTextBox(id, dx, dy) },
                        onEditTextBox = { box -> editingTextBox = box }
                    )
                }

                if (!isTopBarVisible) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    isTopBarVisible = true
                                    isBottomBarVisible = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.FullscreenExit,
                                    contentDescription = "Ukončit celou obrazovku"
                                )
                            }
                        }
                    }
                }

                if (!isBottomBarVisible) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            if (zoomScale > 1.01f || panOffset != Offset.Zero) {
                                IconButton(
                                    onClick = {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ZoomOutMap,
                                        contentDescription = "Resetovat přiblížení",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                    stateHolder.prevPage()
                                },
                                enabled = state.currentPageIndex > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Předchozí strana")
                            }
                            Text(
                                text = if (state.pages.isNotEmpty()) {
                                    "${state.currentPageIndex + 1}/${state.pages.size}"
                                } else {
                                    "0/0"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                    stateHolder.nextPage()
                                },
                                enabled = state.currentPageIndex < state.pages.lastIndex,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Další strana")
                            }
                            IconButton(
                                onClick = { isBottomBarVisible = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Zobrazit spodní lištu"
                                )
                            }
                        }
                    }
                }

                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Zpracovávám PDF...")
                            }
                        }
                    }
                }
            }
        }

        if (pendingTextTapPoint != null) {
            val point = pendingTextTapPoint!!
            AddOrEditTextDialog(
                initialText = "",
                dialogTitle = "Vložit text na stránku",
                onDismiss = { pendingTextTapPoint = null },
                onConfirm = { enteredText ->
                    stateHolder.addTextBox(enteredText, point.x, point.y)
                    pendingTextTapPoint = null
                }
            )
        }

        if (editingTextBox != null) {
            val box = editingTextBox!!
            AddOrEditTextDialog(
                initialText = box.text,
                dialogTitle = "Upravit textové pole",
                showDeleteButton = true,
                onDismiss = { editingTextBox = null },
                onDelete = {
                    stateHolder.deleteTextBox(box.id)
                    editingTextBox = null
                },
                onConfirm = { updatedText ->
                    stateHolder.updateTextBox(
                        id = box.id,
                        newText = updatedText,
                        colorArgb = state.selectedColorArgb,
                        fontSizeRatio = state.fontSizeRatio
                    )
                    editingTextBox = null
                }
            )
        }

        if (showCreateDialog) {
            CreateNewPdfDialog(
                onDismiss = { showCreateDialog = false },
                onCreateBlank = { title ->
                    zoomScale = 1f
                    panOffset = Offset.Zero
                    stateHolder.createBlank(title)
                    showCreateDialog = false
                },
                onCreateFromText = { title, body, ruled ->
                    zoomScale = 1f
                    panOffset = Offset.Zero
                    stateHolder.createNewFromText(title, body, ruled)
                    showCreateDialog = false
                }
            )
        }

        if (showPageManagerDialog) {
            PageManagerDialog(
                pages = state.pages,
                currentPageIndex = state.currentPageIndex,
                documentTitle = state.documentTitle,
                onUpdateTitle = { stateHolder.updateTitle(it) },
                onSelectPage = { index ->
                    zoomScale = 1f
                    panOffset = Offset.Zero
                    stateHolder.selectPage(index)
                    showPageManagerDialog = false
                },
                onMoveUp = { stateHolder.movePageUp(it) },
                onMoveDown = { stateHolder.movePageDown(it) },
                onDeletePage = { stateHolder.deletePage(it) },
                onAddBlankPage = { stateHolder.addBlankPage() },
                onAppendExternalPdf = {
                    showPageManagerDialog = false
                    stateHolder.appendDocument()
                },
                onDismiss = { showPageManagerDialog = false }
            )
        }
    }
}

@Composable
internal fun PdfPageEditorCanvas(
    page: PdfPageModel,
    selectedTool: EditTool,
    selectedColorArgb: Int,
    strokeWidthRatio: Float,
    zoomScale: Float,
    panOffset: Offset,
    onTransform: (Float, Offset) -> Unit,
    onAddStroke: (List<NormalizedPoint>) -> Unit,
    onAddRedaction: (Float, Float, Float, Float) -> Unit,
    onTapForNewText: (NormalizedPoint) -> Unit,
    onMoveTextBox: (String, Float, Float) -> Unit,
    onEditTextBox: (PdfTextBox) -> Unit
) {
    val activeStrokePoints = remember(page.id, selectedTool) { mutableStateListOf<NormalizedPoint>() }
    var redactStart by remember(page.id, selectedTool) { mutableStateOf<NormalizedPoint?>(null) }
    var redactEnd by remember(page.id, selectedTool) { mutableStateOf<NormalizedPoint?>(null) }

    val pageRatio = remember(page.width, page.height) {
        if (page.height > 0) page.width.toFloat() / page.height.toFloat() else 0.707f
    }

    val imageBitmap = remember(page.id, page.imageBytes) {
        decodeImageBytes(page.imageBytes)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .aspectRatio(pageRatio)
                .fillMaxHeight()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale,
                    translationX = panOffset.x,
                    translationY = panOffset.y
                )
                .shadow(4.dp, RoundedCornerShape(2.dp))
                .background(Color.White)
                .pointerInput(page.id, selectedTool) {
                    if (selectedTool == EditTool.NONE) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            onTransform(zoom, pan)
                        }
                    }
                }
                .pointerInput(page.id, selectedTool) {
                    if (selectedTool == EditTool.PEN || selectedTool == EditTool.HIGHLIGHTER) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                activeStrokePoints.clear()
                                if (size.width > 0 && size.height > 0) {
                                    activeStrokePoints.add(
                                        NormalizedPoint(
                                            x = (offset.x / size.width).coerceIn(0f, 1f),
                                            y = (offset.y / size.height).coerceIn(0f, 1f)
                                        )
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (size.width > 0 && size.height > 0) {
                                    activeStrokePoints.add(
                                        NormalizedPoint(
                                            x = (change.position.x / size.width).coerceIn(0f, 1f),
                                            y = (change.position.y / size.height).coerceIn(0f, 1f)
                                        )
                                    )
                                }
                            },
                            onDragEnd = {
                                if (activeStrokePoints.isNotEmpty()) {
                                    onAddStroke(activeStrokePoints.toList())
                                    activeStrokePoints.clear()
                                }
                            },
                            onDragCancel = {
                                activeStrokePoints.clear()
                            }
                        )
                    } else if (selectedTool == EditTool.REDACT) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (size.width > 0 && size.height > 0) {
                                    val pt = NormalizedPoint(
                                        x = (offset.x / size.width).coerceIn(0f, 1f),
                                        y = (offset.y / size.height).coerceIn(0f, 1f)
                                    )
                                    redactStart = pt
                                    redactEnd = pt
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (size.width > 0 && size.height > 0) {
                                    redactEnd = NormalizedPoint(
                                        x = (change.position.x / size.width).coerceIn(0f, 1f),
                                        y = (change.position.y / size.height).coerceIn(0f, 1f)
                                    )
                                }
                            },
                            onDragEnd = {
                                val s = redactStart
                                val e = redactEnd
                                if (s != null && e != null) {
                                    onAddRedaction(s.x, s.y, e.x, e.y)
                                }
                                redactStart = null
                                redactEnd = null
                            },
                            onDragCancel = {
                                redactStart = null
                                redactEnd = null
                            }
                        )
                    } else if (selectedTool == EditTool.TEXT) {
                        detectTapGestures { offset ->
                            if (size.width > 0 && size.height > 0) {
                                onTapForNewText(
                                    NormalizedPoint(
                                        x = (offset.x / size.width).coerceIn(0.02f, 0.90f),
                                        y = (offset.y / size.height).coerceIn(0.02f, 0.92f)
                                    )
                                )
                            }
                        }
                    }
                }
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val canvasWidthPx = with(density) { maxWidth.toPx() }
                val canvasHeightPx = with(density) { maxHeight.toPx() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(
                        image = imageBitmap,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )

                    for (rect in page.redactions) {
                        val left = min(rect.left, rect.right) * size.width
                        val right = max(rect.left, rect.right) * size.width
                        val top = min(rect.top, rect.bottom) * size.height
                        val bottom = max(rect.top, rect.bottom) * size.height
                        drawRect(
                            color = Color(rect.colorArgb),
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top)
                        )
                    }

                    val rStart = redactStart
                    val rEnd = redactEnd
                    if (rStart != null && rEnd != null) {
                        val left = min(rStart.x, rEnd.x) * size.width
                        val right = max(rStart.x, rEnd.x) * size.width
                        val top = min(rStart.y, rEnd.y) * size.height
                        val bottom = max(rStart.y, rEnd.y) * size.height
                        drawRect(
                            color = Color.White.copy(alpha = 0.85f),
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top)
                        )
                        drawRect(
                            color = Color.Red,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = Stroke(width = 2f)
                        )
                    }

                    for (stroke in page.strokes) {
                        if (stroke.points.isEmpty()) continue
                        val strokeColor = if (stroke.isHighlighter) {
                            Color(stroke.colorArgb).copy(alpha = 0.38f)
                        } else {
                            Color(stroke.colorArgb)
                        }
                        val strokeW = stroke.strokeWidthRatio * size.width

                        if (stroke.points.size == 1) {
                            val pt = stroke.points.first()
                            drawCircle(
                                color = strokeColor,
                                radius = strokeW / 2f,
                                center = Offset(pt.x * size.width, pt.y * size.height)
                            )
                        } else {
                            val path = Path()
                            val first = stroke.points.first()
                            path.moveTo(first.x * size.width, first.y * size.height)
                            for (i in 1 until stroke.points.size) {
                                val pt = stroke.points[i]
                                path.lineTo(pt.x * size.width, pt.y * size.height)
                            }
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = strokeW,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    if (activeStrokePoints.isNotEmpty()) {
                        val isHighlighter = selectedTool == EditTool.HIGHLIGHTER
                        val strokeColor = if (isHighlighter) {
                            Color(selectedColorArgb).copy(alpha = 0.38f)
                        } else {
                            Color(selectedColorArgb)
                        }
                        val ratio = if (isHighlighter) {
                            (strokeWidthRatio * 3.2f).coerceAtMost(0.06f)
                        } else {
                            strokeWidthRatio
                        }
                        val strokeW = ratio * size.width

                        val path = Path()
                        val first = activeStrokePoints.first()
                        path.moveTo(first.x * size.width, first.y * size.height)
                        for (i in 1 until activeStrokePoints.size) {
                            val pt = activeStrokePoints[i]
                            path.lineTo(pt.x * size.width, pt.y * size.height)
                        }
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(
                                width = strokeW,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                page.textBoxes.forEach { textBox ->
                    val offsetX = (textBox.x * canvasWidthPx).roundToInt()
                    val offsetY = (textBox.y * canvasHeightPx).roundToInt()
                    val dynamicFontSizeSp = with(density) {
                        (textBox.fontSizeRatio * canvasWidthPx).toSp()
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX, offsetY) }
                            .border(
                                width = 1.dp,
                                color = if (selectedTool == EditTool.TEXT || selectedTool == EditTool.NONE) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(4.dp)
                            )
                            .background(
                                color = if (selectedTool == EditTool.TEXT) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(4.dp)
                            )
                            .pointerInput(textBox.id, canvasWidthPx, canvasHeightPx) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (canvasWidthPx > 0f && canvasHeightPx > 0f) {
                                        onMoveTextBox(
                                            textBox.id,
                                            dragAmount.x / canvasWidthPx,
                                            dragAmount.y / canvasHeightPx
                                        )
                                    }
                                }
                            }
                            .clickable { onEditTextBox(textBox) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = textBox.text,
                            color = Color(textBox.colorArgb),
                            fontSize = dynamicFontSizeSp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddOrEditTextDialog(
    initialText: String,
    dialogTitle: String,
    showDeleteButton: Boolean = false,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Text na stránce") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tip: Vložený text můžeš na stránce kdykoliv chytit prstem a posunout na přesné místo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(textValue) }) {
                Text("Uložit")
            }
        },
        dismissButton = {
            Row {
                if (showDeleteButton && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Smazat", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Zrušit")
                }
            }
        }
    )
}

@Composable
internal fun CreateNewPdfDialog(
    onDismiss: () -> Unit,
    onCreateBlank: (String) -> Unit,
    onCreateFromText: (String, String, Boolean) -> Unit
) {
    var docTitle by remember { mutableStateOf("Nový dokument") }
    var docBody by remember { mutableStateOf("") }
    var addRuledLines by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vytvořit nové PDF") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = docTitle,
                    onValueChange = { docTitle = it },
                    label = { Text("Název / Nadpis dokumentu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = docBody,
                    onValueChange = { docBody = it },
                    label = { Text("Obsah dokumentu (volitelné)") },
                    placeholder = { Text("Napiš text, smlouvu, poznámky nebo nech prázdné...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { addRuledLines = !addRuledLines }
                ) {
                    Checkbox(
                        checked = addRuledLines,
                        onCheckedChange = { addRuledLines = it }
                    )
                    Text("Vykreslit pomocné linky (jako v sešitě)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (docBody.isBlank() && !addRuledLines) {
                        onCreateBlank(docTitle.ifBlank { "Cisty_dokument" })
                    } else {
                        onCreateFromText(docTitle, docBody, addRuledLines)
                    }
                }
            ) {
                Text("Vytvořit PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zrušit")
            }
        }
    )
}

@Composable
internal fun PageManagerDialog(
    pages: List<PdfPageModel>,
    currentPageIndex: Int,
    documentTitle: String,
    onUpdateTitle: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onAddBlankPage: () -> Unit,
    onAppendExternalPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    var editedTitle by remember(documentTitle) { mutableStateOf(documentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Správa stránek a slučování") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = {
                        editedTitle = it
                        onUpdateTitle(it)
                    },
                    label = { Text("Název souboru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddBlankPage,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Čistá str.")
                    }
                    Button(
                        onClick = onAppendExternalPdf,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Připojit PDF")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(pages, key = { _, item -> item.id }) { index, page ->
                        val isCurrent = index == currentPageIndex
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPage(index) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Strana ${index + 1}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    val annotationsCount =
                                        page.strokes.size + page.redactions.size + page.textBoxes.size
                                    Text(
                                        text = "Úprav na stránce: $annotationsCount",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onMoveUp(index) },
                                        enabled = index > 0
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Posunout výš")
                                    }
                                    IconButton(
                                        onClick = { onMoveDown(index) },
                                        enabled = index < pages.lastIndex
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Posunout níž")
                                    }
                                    IconButton(
                                        onClick = { onDeletePage(index) },
                                        enabled = pages.size > 1
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Smazat stranu",
                                            tint = if (pages.size > 1) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                Color.Gray
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zavřít")
            }
        }
    )
}
