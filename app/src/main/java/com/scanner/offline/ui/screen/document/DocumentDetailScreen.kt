package com.scanner.offline.ui.screen.document

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scanner.offline.R
import com.scanner.offline.domain.model.Page
import com.scanner.offline.util.PrintUtils
import com.scanner.offline.util.Time
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
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
                            onOcrClick = { onOcrClick(page.processedPath, page.id) },
                            onEditClick = { onEditClick(page.processedPath, page.id) },
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
}

@Composable
private fun PageCard(
    page: Page,
    index: Int,
    pageCount: Int,
    enabled: Boolean,
    onOcrClick: () -> Unit,
    onEditClick: () -> Unit,
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
        ) {
            if (File(page.processedPath).exists()) {
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
