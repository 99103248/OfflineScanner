package com.scanner.offline.ui.screen.document

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scanner.offline.R
import com.scanner.offline.domain.model.FilterMode
import com.scanner.offline.domain.model.Page
import com.scanner.offline.util.PrintUtils
import com.scanner.offline.util.Time
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentDetailScreen(
    docId: Long,
    onExportClick: () -> Unit,
    onOcrClick: (String, Long) -> Unit,
    onEditClick: (String, Long) -> Unit,
    onAddPageCamera: () -> Unit,
    onBack: () -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(docId) { viewModel.load(docId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var pageToDelete by remember { mutableStateOf<Page?>(null) }
    var pageToFilter by remember { mutableStateOf<Page?>(null) }
    var viewerPageIndex by remember { mutableStateOf<Int?>(null) }

    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.appendImages(docId, uris.map { it.toString() })
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.doc?.name ?: "文档", maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = onExportClick, enabled = state.doc?.pages?.isNotEmpty() == true) {
                        Icon(Icons.Outlined.IosShare, contentDescription = "导出")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                            onClick = {
                                menuOpen = false
                                renameText = state.doc?.name.orEmpty()
                                renameOpen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("全文识别（OCR）") },
                            leadingIcon = { Icon(Icons.Outlined.TextFields, null) },
                            enabled = !state.busy && state.doc?.pages?.isNotEmpty() == true,
                            onClick = {
                                menuOpen = false
                                viewModel.ocrAll(docId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("打印") },
                            leadingIcon = { Icon(Icons.Outlined.Print, null) },
                            enabled = state.doc?.pages?.isNotEmpty() == true,
                            onClick = {
                                menuOpen = false
                                val doc = state.doc ?: return@DropdownMenuItem
                                PrintUtils.printDocument(
                                    context = context,
                                    jobName = doc.name,
                                    imagePaths = doc.pages.map { it.processedPath }
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("移入回收站") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                            onClick = {
                                menuOpen = false
                                viewModel.moveToTrash(docId)
                                onBack()
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val current = state.doc
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.busy) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.progressText?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (current == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中...")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 8.dp, bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "${current.pages.size} 页 · 创建于 ${Time.formatFull(current.createdAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (current.pages.isEmpty()) {
                        item {
                            Text(
                                text = "还没有页面，点击下方按钮追加",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 32.dp)
                            )
                        }
                    }
                    items(items = current.pages, key = { it.id }) { page ->
                        val index = current.pages.indexOfFirst { it.id == page.id }
                        PageCard(
                            page = page,
                            index = index,
                            pageCount = current.pages.size,
                            enabled = !state.busy,
                            onPreviewClick = { viewerPageIndex = index },
                            onOcrClick = { onOcrClick(page.processedPath, page.id) },
                            onEditClick = { onEditClick(page.processedPath, page.id) },
                            onFilterClick = { pageToFilter = page },
                            onMoveUp = { viewModel.movePage(docId, page.id, -1) },
                            onMoveDown = { viewModel.movePage(docId, page.id, 1) },
                            onRotate = { viewModel.rotatePage(docId, page.id) },
                            onSetCover = { viewModel.setCover(docId, page.id) },
                            onDelete = { pageToDelete = page }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddPageCamera,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, null)
                        Spacer(Modifier.width(6.dp))
                        Text("拍照追加")
                    }
                    Button(
                        onClick = { pickImages.launch("image/*") },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, null)
                        Spacer(Modifier.width(6.dp))
                        Text("相册追加")
                    }
                }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("重命名文档") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("文档名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(docId, renameText)
                    renameOpen = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("取消") }
            }
        )
    }

    pageToDelete?.let { page ->
        AlertDialog(
            onDismissRequest = { pageToDelete = null },
            title = { Text("删除这一页？") },
            text = { Text("第 ${page.index + 1} 页将从文档中永久删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePage(docId, page.id)
                    pageToDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pageToDelete = null }) { Text("取消") }
            }
        )
    }

    pageToFilter?.let { page ->
        AlertDialog(
            onDismissRequest = { pageToFilter = null },
            title = { Text("重新应用滤镜") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "基于保存的原图无损重新生成第 ${page.index + 1} 页画面：",
                        style = MaterialTheme.typography.bodySmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterMode.entries.forEach { mode ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    viewModel.applyFilter(docId, page.id, mode)
                                    pageToFilter = null
                                },
                                label = { Text(mode.displayName) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pageToFilter = null }) { Text("关闭") }
            }
        )
    }

    val pagesList = state.doc?.pages.orEmpty()
    viewerPageIndex?.let { startIdx ->
        if (pagesList.isNotEmpty() && startIdx in pagesList.indices) {
            ZoomablePageViewerDialog(
                pages = pagesList,
                initialIndex = startIdx,
                onDismiss = { viewerPageIndex = null },
                onOcrClick = { page ->
                    viewerPageIndex = null
                    onOcrClick(page.processedPath, page.id)
                },
                onEditClick = { page ->
                    viewerPageIndex = null
                    onEditClick(page.processedPath, page.id)
                },
                onFilterClick = { page ->
                    viewerPageIndex = null
                    pageToFilter = page
                }
            )
        }
    }
}

@Composable
private fun ZoomablePageViewerDialog(
    pages: List<Page>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onOcrClick: (Page) -> Unit,
    onEditClick: (Page) -> Unit,
    onFilterClick: (Page) -> Unit
) {
    var currentIndex by remember(initialIndex, pages.size) {
        mutableIntStateOf(initialIndex.coerceIn(0, pages.lastIndex))
    }
    var scale by remember(currentIndex) { mutableFloatStateOf(1f) }
    var offset by remember(currentIndex) { mutableStateOf(Offset.Zero) }
    val page = pages[currentIndex]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = File(page.processedPath),
                contentDescription = "第 ${currentIndex + 1} 页大图预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentIndex) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(currentIndex) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            scale = newScale
                            offset = if (newScale > 1.02f) {
                                Offset(offset.x + pan.x, offset.y + pan.y)
                            } else {
                                Offset.Zero
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )

            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭预览", tint = Color.White)
                }
                Text(
                    text = "第 ${currentIndex + 1} / ${pages.size} 页（双击或双指缩放）",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
            }

            // 底部翻页与快捷操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentIndex > 0) currentIndex-- },
                    enabled = currentIndex > 0
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "上一页",
                        tint = if (currentIndex > 0) Color.White else Color.Gray
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onFilterClick(page) }) { Text("滤镜", color = Color.White) }
                    TextButton(onClick = { onEditClick(page) }) { Text("编辑", color = Color.White) }
                    TextButton(onClick = { onOcrClick(page) }) { Text("OCR", color = Color.White) }
                }
                IconButton(
                    onClick = { if (currentIndex < pages.lastIndex) currentIndex++ },
                    enabled = currentIndex < pages.lastIndex
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "下一页",
                        tint = if (currentIndex < pages.lastIndex) Color.White else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun PageCard(
    page: Page,
    index: Int,
    pageCount: Int,
    enabled: Boolean,
    onPreviewClick: () -> Unit,
    onOcrClick: () -> Unit,
    onEditClick: () -> Unit,
    onFilterClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRotate: () -> Unit,
    onSetCover: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .clickable(onClick = onPreviewClick)
        ) {
            if (page.processedPath.isNotBlank()) {
                AsyncImage(
                    model = File(page.processedPath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 ${index + 1} 页",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            AssistChip(
                onClick = onEditClick,
                enabled = enabled,
                label = { Text("编辑") },
                leadingIcon = { Icon(Icons.Outlined.Crop, null) }
            )
            AssistChip(
                onClick = onOcrClick,
                enabled = enabled,
                label = { Text("OCR") },
                leadingIcon = { Icon(Icons.Outlined.TextFields, null) }
            )
            Box {
                IconButton(onClick = { menu = true }, enabled = enabled) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "页面操作")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("重新应用滤镜") },
                        leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) },
                        onClick = { menu = false; onFilterClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("上移") },
                        leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) },
                        enabled = index > 0,
                        onClick = { menu = false; onMoveUp() }
                    )
                    DropdownMenuItem(
                        text = { Text("下移") },
                        leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) },
                        enabled = index < pageCount - 1,
                        onClick = { menu = false; onMoveDown() }
                    )
                    DropdownMenuItem(
                        text = { Text("顺时针旋转 90°") },
                        leadingIcon = { Icon(Icons.Outlined.Rotate90DegreesCw, null) },
                        onClick = { menu = false; onRotate() }
                    )
                    DropdownMenuItem(
                        text = { Text("设为封面") },
                        leadingIcon = {
                            Icon(
                                if (index == 0) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                null
                            )
                        },
                        enabled = index > 0,
                        onClick = { menu = false; onSetCover() }
                    )
                    DropdownMenuItem(
                        text = { Text("删除此页") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
        if (!page.ocrText.isNullOrBlank()) {
            Text(
                text = page.ocrText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4
            )
        }
    }
}
