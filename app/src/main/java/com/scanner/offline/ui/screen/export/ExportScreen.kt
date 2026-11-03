package com.scanner.offline.ui.screen.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.PdfPaperSize
import com.scanner.offline.util.ShareUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    docId: Long,
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 可搜索 PDF 需要先对缺文字的页面跑 OCR，耗时较长，默认关闭由用户按需开启。
    var searchablePdf by remember { mutableStateOf(false) }
    var pdfPaper by remember { mutableStateOf(viewModel.defaultPdfPaper) }

    LaunchedEffect(state.error) {
        state.error?.let { scope.launch { snackbar.showSnackbar(it) } }
    }
    LaunchedEffect(state.lastResult) {
        state.lastResult?.let {
            val msg = if (state.lastResults.size > 1) {
                "已导出 ${state.lastResults.size} 个文件"
            } else {
                "已导出：${it.displayName}"
            }
            scope.launch { snackbar.showSnackbar(msg) }
        }
    }

    val items = listOf(
        ExportTile(Icons.Outlined.PictureAsPdf, ExportFormat.PDF),
        ExportTile(Icons.Outlined.Description, ExportFormat.WORD),
        ExportTile(Icons.Outlined.GridOn, ExportFormat.EXCEL),
        ExportTile(Icons.AutoMirrored.Outlined.TextSnippet, ExportFormat.TXT),
        ExportTile(Icons.Outlined.Image, ExportFormat.JPG),
        ExportTile(Icons.Outlined.Image, ExportFormat.PNG),
        ExportTile(Icons.Outlined.Image, ExportFormat.WEBP)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导出") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("可搜索 PDF", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "导出 PDF 时内嵌隐形 OCR 文字层，可全文搜索",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = searchablePdf,
                    onCheckedChange = { searchablePdf = it },
                    enabled = !state.running
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("PDF 纸张：", style = MaterialTheme.typography.bodyMedium)
                PdfPaperSize.entries.forEach { paper ->
                    FilterChip(
                        selected = paper == pdfPaper,
                        onClick = { pdfPaper = paper },
                        label = { Text(paper.displayName) },
                        enabled = !state.running
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = items, key = { it.format.extension }) { tile ->
                    ExportCard(tile = tile, enabled = !state.running) {
                        viewModel.export(
                            docId = docId,
                            format = tile.format,
                            searchablePdf = searchablePdf && tile.format == ExportFormat.PDF,
                            pdfPaper = pdfPaper
                        )
                    }
                }
            }

            if (state.running) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("导出中...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            state.lastResult?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("导出成功", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (state.lastResults.size > 1) {
                                "已导出 ${state.lastResults.size} 个文件\n${result.humanLocation}"
                            } else {
                                result.humanLocation
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        AssistChip(
                            onClick = {
                                ShareUtils.share(context, result.shareUri, result.mimeType)
                            },
                            label = { Text("分享") }
                        )
                    }
                }
            }
        }
    }
}

private data class ExportTile(val icon: ImageVector, val format: ExportFormat)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportCard(
    tile: ExportTile,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
        enabled = enabled
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(tile.icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(tile.format.displayName, style = MaterialTheme.typography.titleMedium)
        }
    }
}
