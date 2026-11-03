package com.scanner.offline.ui.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scanner.offline.R
import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.model.DocumentSort
import com.scanner.offline.util.Time
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScanClick: () -> Unit,
    onDocumentClick: (Long) -> Unit,
    onTrashClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val docs by viewModel.documents.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val defaultGrid by viewModel.gridViewSetting.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var gridView by rememberSaveable { mutableStateOf(defaultGrid) }
    LaunchedEffect(defaultGrid) { gridView = defaultGrid }
    var sortMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Document?>(null) }
    var renameText by remember { mutableStateOf("") }
    var confirmBatchDelete by remember { mutableStateOf(false) }

    val selectionMode = selected.isNotEmpty()

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("已选 ${selected.size}") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Outlined.Close, contentDescription = "取消选择")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll(docs.map { it.id }) }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "全选")
                        }
                        IconButton(onClick = { viewModel.batchFavorite(selected, true) }) {
                            Icon(Icons.Outlined.Star, contentDescription = "收藏")
                        }
                        IconButton(onClick = { viewModel.batchExportPdf(selected) }) {
                            Icon(Icons.Outlined.IosShare, contentDescription = "导出 PDF")
                        }
                        IconButton(onClick = { confirmBatchDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_title)) },
                    actions = {
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "排序")
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                DocumentSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.displayName) },
                                        onClick = {
                                            viewModel.setSort(option)
                                            sortMenu = false
                                        },
                                        trailingIcon = {
                                            if (option == sort) {
                                                Icon(Icons.Outlined.CheckCircle, null)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            gridView = !gridView
                            viewModel.setGridView(gridView)
                        }) {
                            Icon(
                                if (gridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                                contentDescription = if (gridView) "列表视图" else "网格视图"
                            )
                        }
                        IconButton(onClick = onTrashClick) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.trash_title))
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onScanClick,
                    icon = { Icon(Icons.Outlined.DocumentScanner, null) },
                    text = { Text(stringResource(R.string.home_fab_scan)) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            if (ui.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (!selectionMode) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    placeholder = { Text(stringResource(R.string.home_search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Outlined.Close, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            if (docs.isEmpty()) {
                EmptyView(padding, query.isNotEmpty())
            } else if (gridView) {
                DocumentGrid(
                    docs = docs,
                    selected = selected,
                    selectionMode = selectionMode,
                    bottomPadding = padding.calculateBottomPadding() + 96.dp,
                    onClick = { id -> if (selectionMode) viewModel.toggleSelection(id) else onDocumentClick(id) },
                    onLongClick = { id -> viewModel.toggleSelection(id) }
                )
            } else {
                DocumentList(
                    docs = docs,
                    selected = selected,
                    selectionMode = selectionMode,
                    contentPadding = padding,
                    onClick = { id -> if (selectionMode) viewModel.toggleSelection(id) else onDocumentClick(id) },
                    onLongClick = { id -> viewModel.toggleSelection(id) },
                    onDelete = viewModel::delete,
                    onToggleFavorite = viewModel::setFavorite,
                    onRename = { doc ->
                        renameTarget = doc
                        renameText = doc.name
                    }
                )
            }
        }
    }

    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("删除 ${selected.size} 个文档？") },
            text = { Text("文档会先进入回收站，可以随时恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.batchDelete(selected)
                    confirmBatchDelete = false
                }) { Text("移入回收站") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text("取消") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
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
                    viewModel.rename(target.id, renameText)
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyView(padding: PaddingValues, searching: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(if (searching) R.string.home_search_empty else R.string.home_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DocumentList(
    docs: List<Document>,
    selected: Set<Long>,
    selectionMode: Boolean,
    contentPadding: PaddingValues,
    onClick: (Long) -> Unit,
    onLongClick: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onRename: (Document) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = docs, key = { it.id }) { doc ->
            DocumentListItem(
                doc = doc,
                selected = doc.id in selected,
                selectionMode = selectionMode,
                onClick = { onClick(doc.id) },
                onLongClick = { onLongClick(doc.id) },
                onDelete = { onDelete(doc.id) },
                onToggleFavorite = { onToggleFavorite(doc.id, !doc.isFavorite) },
                onRename = { onRename(doc) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentListItem(
    doc: Document,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Thumbnail(doc)
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${doc.pageCount} 页 · ${Time.formatShort(doc.updatedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (doc.isFavorite) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (!selectionMode) {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (doc.isFavorite) R.string.action_unfavorite else R.string.action_favorite)) },
                        leadingIcon = {
                            Icon(if (doc.isFavorite) Icons.Outlined.StarBorder else Icons.Outlined.Star, null)
                        },
                        onClick = { menu = false; onToggleFavorite() }
                    )
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentGrid(
    docs: List<Document>,
    selected: Set<Long>,
    selectionMode: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onClick: (Long) -> Unit,
    onLongClick: (Long) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = docs, key = { it.id }) { doc ->
            GridItem(
                doc = doc,
                selected = doc.id in selected,
                onClick = { onClick(doc.id) },
                onLongClick = { onLongClick(doc.id) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridItem(
    doc: Document,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                Thumbnail(doc)
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (doc.isFavorite) {
                    Icon(
                        Icons.Outlined.Star,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${doc.pageCount} 页 · ${Time.formatShort(doc.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(doc: Document) {
    val path = doc.coverPath
    if (path != null && File(path).exists()) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.DocumentScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
