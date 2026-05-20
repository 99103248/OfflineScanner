package com.scanner.offline.ui.screen.export

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scanner.offline.R
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.InputDocumentType
import com.scanner.offline.domain.model.PdfImageLayout
import com.scanner.offline.util.ShareUtils
import com.scanner.offline.util.Time
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatConvertScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var sourceUri by remember { mutableStateOf<String?>(null) }
    var sourceLabel by remember { mutableStateOf<String?>(null) }
    var targetFormat by remember { mutableStateOf(ExportFormat.PNG) }
    var pdfLayout by remember { mutableStateOf(PdfImageLayout.SEPARATE_PAGES) }
    val isPdfSource = isPdfUri(sourceUri, sourceLabel)

    val pickDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            sourceUri = uri.toString()
            sourceLabel = uri.lastPathSegment ?: uri.toString()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { scope.launch { snackbar.showSnackbar(it) } }
    }
    LaunchedEffect(state.lastResults) {
        if (state.lastResults.isNotEmpty() && !state.running) {
            val msg = when {
                state.lastResults.size == 1 && state.lastResults.first().displayName.contains("_long") ->
                    "转换完成：已生成长图 ${state.lastResults.first().displayName}"
                state.lastResults.size == 1 ->
                    "转换完成：${state.lastResults.first().displayName}"
                else -> "转换完成：共 ${state.lastResults.size} 张图片"
            }
            scope.launch { snackbar.showSnackbar(msg) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_format)) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.tool_format_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Card(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (sourceUri != null) {
                        if (isImageUri(sourceUri!!, sourceLabel)) {
                            AsyncImage(
                                model = sourceUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            DocumentPreview(
                                label = sourceLabel,
                                type = state.inputType
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                pickDocument.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "text/plain",
                                        "image/*"
                                    )
                                )
                            }) {
                                Text(stringResource(R.string.tool_format_pick))
                            }
                        }
                    }
                }
            }

            if (sourceUri != null) {
                sourceLabel?.let {
                    Text(
                        "已选择：$it",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (isPdfSource) {
                    Text(stringResource(R.string.tool_format_pdf_layout), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PdfImageLayout.entries.forEach { layout ->
                            FilterChip(
                                selected = layout == pdfLayout,
                                onClick = { pdfLayout = layout },
                                label = { Text(layout.displayName) }
                            )
                        }
                    }
                }

                Text("目标格式：", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ExportFormat.JPG, ExportFormat.PNG, ExportFormat.WEBP).forEach { f ->
                        FilterChip(
                            selected = f == targetFormat,
                            onClick = { targetFormat = f },
                            label = { Text(f.displayName) }
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.convertDocumentFromUri(
                            uriString = sourceUri!!,
                            format = targetFormat,
                            baseName = Time.nowDocName("converted"),
                            pdfLayout = pdfLayout
                        )
                    },
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.running) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (state.running) "转换中..." else stringResource(R.string.tool_format_start))
                }

                if (state.lastResults.isNotEmpty()) {
                    Text(
                        "输出文件",
                        style = MaterialTheme.typography.titleMedium
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.lastResults, key = { it.displayName + it.humanLocation }) { result ->
                            Card(
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(result.displayName)
                                    Text(
                                        result.humanLocation,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    AssistChip(
                                        onClick = {
                                            ShareUtils.share(context, result.shareUri, result.mimeType)
                                        },
                                        label = { Text("分享") },
                                        leadingIcon = { Icon(Icons.Outlined.IosShare, null) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isImageUri(uri: String, label: String?): Boolean {
    val probe = (label ?: uri).lowercase()
    return probe.endsWith(".jpg") || probe.endsWith(".jpeg") ||
        probe.endsWith(".png") || probe.endsWith(".webp") || probe.endsWith(".gif")
}

private fun isPdfUri(uri: String?, label: String?): Boolean {
    if (uri == null) return false
    val probe = (label ?: uri).lowercase()
    return probe.endsWith(".pdf") || probe.contains(".pdf")
}

@Composable
private fun DocumentPreview(label: String?, type: InputDocumentType?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val icon = when (type) {
            InputDocumentType.PDF -> Icons.Outlined.PictureAsPdf
            InputDocumentType.WORD, InputDocumentType.TXT -> Icons.Outlined.Description
            InputDocumentType.IMAGE -> Icons.Outlined.Image
            null -> Icons.Outlined.Description
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            type?.displayName ?: "文档",
            style = MaterialTheme.typography.titleMedium
        )
        label?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
