package com.scanner.offline.ui.screen.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanner.offline.BuildConfig
import com.scanner.offline.data.settings.ThemeMode
import com.scanner.offline.domain.model.FilterMode
import com.scanner.offline.domain.model.PdfPaperSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ───── 扫描与保存 ─────
            ListItem(
                headlineContent = { Text("默认滤镜") },
                supportingContent = { Text("扫描保存时默认套用的图像增强方式") }
            )
            ChipRow {
                FilterMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.defaultFilter == mode,
                        onClick = { viewModel.setDefaultFilter(mode) },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            ListItem(
                headlineContent = { Text("保存后自动 OCR") },
                supportingContent = { Text("每保存一页就立即识别文字，稍慢但文档可直接搜索") },
                trailingContent = {
                    Switch(
                        checked = settings.autoOcrOnSave,
                        onCheckedChange = viewModel::setAutoOcr
                    )
                }
            )

            HorizontalDivider()

            // ───── 导出 ─────
            ListItem(
                headlineContent = { Text("图片导出质量") },
                supportingContent = { Text("${settings.exportQuality}%（越高越清晰，文件越大）") }
            )
            Slider(
                value = settings.exportQuality.toFloat(),
                onValueChange = { viewModel.setExportQuality(it.toInt()) },
                valueRange = 40f..100f,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            ListItem(
                headlineContent = { Text("PDF 纸张") },
                supportingContent = { Text("A4 / Letter 会自动留白并等比缩放") }
            )
            ChipRow {
                PdfPaperSize.entries.forEach { paper ->
                    FilterChip(
                        selected = settings.pdfPaper == paper,
                        onClick = { viewModel.setPdfPaper(paper) },
                        label = { Text(paper.displayName) }
                    )
                }
            }

            ListItem(
                headlineContent = { Text("PDF 图片最大边长") },
                supportingContent = { Text("越小 PDF 体积越小，文字清晰度略降") }
            )
            ChipRow {
                listOf(2000, 3000, 4000).forEach { dim ->
                    FilterChip(
                        selected = settings.pdfMaxImageDimension == dim,
                        onClick = { viewModel.setPdfMaxDimension(dim) },
                        label = { Text("${dim}px") }
                    )
                }
            }

            HorizontalDivider()

            // ───── 文档库 ─────
            ListItem(
                headlineContent = { Text("默认网格视图") },
                supportingContent = { Text("文档库以缩略图网格展示") },
                trailingContent = {
                    Switch(
                        checked = settings.gridView,
                        onCheckedChange = viewModel::setGridView
                    )
                }
            )

            ListItem(
                headlineContent = { Text("回收站保留天数") },
                supportingContent = { Text("超期的文档会在打开文档库时自动清理") }
            )
            ChipRow {
                listOf(0, 7, 30, 90).forEach { days ->
                    FilterChip(
                        selected = settings.trashRetentionDays == days,
                        onClick = { viewModel.setTrashRetentionDays(days) },
                        label = { Text(if (days == 0) "永久保留" else "$days 天") }
                    )
                }
            }

            HorizontalDivider()

            // ───── 外观 ─────
            ListItem(headlineContent = { Text("主题") })
            ChipRow {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "OffScan v${BuildConfig.VERSION_NAME} · 全部处理均在本机完成",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
