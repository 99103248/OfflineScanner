package com.scanner.offline.ui.screen.me

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanner.offline.BuildConfig
import com.scanner.offline.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    onSettingsClick: () -> Unit = {},
    viewModel: MeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pickDir = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onDirectoryChosen(uri)
    }

    val createBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) viewModel.backup(uri)
    }

    val openBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.restore(uri)
    }

    LaunchedEffect(state.lastActionMessage) {
        state.lastActionMessage?.let {
            scope.launch { snackbar.showSnackbar(it) }
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.me_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ───── 导出目录 ─────
            ListItem(
                headlineContent = { Text(stringResource(R.string.me_default_export_dir)) },
                supportingContent = {
                    Column {
                        Text(
                            text = state.exportDirSummary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!state.isCustomDirSet) {
                            Text(
                                text = state.defaultExportPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                leadingContent = { Icon(Icons.Outlined.FolderSpecial, null) }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Button(onClick = { pickDir.launch(null) }) {
                    Icon(Icons.Outlined.FolderOpen, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isCustomDirSet) "更改导出目录" else "选择导出目录")
                }
                if (state.isCustomDirSet) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.onResetToDefault() }) {
                        Icon(Icons.Outlined.RestartAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text("恢复默认")
                    }
                }
            }

            HorizontalDivider()

            // ───── 备份与恢复 ─────
            ListItem(
                headlineContent = { Text("备份与恢复") },
                supportingContent = { Text("把全部文档打包为一个 zip，或从 zip 恢复到本机") },
                leadingContent = { Icon(Icons.Outlined.Backup, null) }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Button(
                    onClick = {
                        createBackup.launch("offscan_backup_${System.currentTimeMillis()}.zip")
                    },
                    enabled = !state.busy
                ) {
                    Icon(Icons.Outlined.Backup, null)
                    Spacer(Modifier.width(6.dp))
                    Text("备份全部文档")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                    enabled = !state.busy
                ) {
                    Icon(Icons.Outlined.Restore, null)
                    Spacer(Modifier.width(6.dp))
                    Text("从备份恢复")
                }
            }
            if (state.busy) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                state.progressText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider()

            // ───── 设置 ─────
            ListItem(
                headlineContent = { Text("设置") },
                supportingContent = { Text("默认滤镜、导出质量、PDF 纸张、主题、回收站") },
                leadingContent = { Icon(Icons.Outlined.Settings, null) },
                modifier = Modifier.clickable(onClick = onSettingsClick)
            )

            HorizontalDivider()

            // ───── OCR 引擎 ─────
            ListItem(
                headlineContent = { Text(stringResource(R.string.me_ocr_engine)) },
                supportingContent = {
                    Text("中文：PaddleOCR PP-OCRv4（已集成）\n英文/拉丁：MLKit（默认）")
                },
                leadingContent = { Icon(Icons.Outlined.Memory, null) }
            )

            HorizontalDivider()

            // ───── 关于 ─────
            ListItem(
                headlineContent = { Text(stringResource(R.string.me_about)) },
                supportingContent = { Text("OffScan v${BuildConfig.VERSION_NAME} · 全部识别和导出均在本机完成") },
                leadingContent = { Icon(Icons.Outlined.Info, null) }
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "提示：所有图片、识别结果均存储在本机，不上传任何服务器。\n" +
                    "导出文件可写到你选择的任意位置（如 Download / 自建文件夹）。",
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
