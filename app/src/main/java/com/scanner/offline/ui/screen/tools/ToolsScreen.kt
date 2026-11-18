package com.scanner.offline.ui.screen.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.R
import com.scanner.offline.domain.usecase.SaveBurstPagesUseCase
import com.scanner.offline.util.Time
import com.scanner.offline.util.UriUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val saveBurst: SaveBurstPagesUseCase
) : ViewModel() {
    fun buildDocumentFromImages(
        paths: List<String>,
        prefix: String,
        onDone: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                saveBurst(paths, Time.nowDocName(prefix))
            }.onSuccess(onDone).onFailure { onError(it.message ?: "导入图片失败") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onScanClick: () -> Unit,
    onPickImageForOcr: (String) -> Unit,
    onQuickExport: (Long, String) -> Unit = { _, _ -> },
    onFormatConvertClick: () -> Unit,
    onPickImageForEdit: (String, String) -> Unit,
    onBatchClick: () -> Unit,
    onStitchClick: () -> Unit,
    onPdfToolsClick: () -> Unit,
    onBarcodeClick: () -> Unit,
    onIdCardClick: () -> Unit,
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingEditMode by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportFormat by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val editMode = pendingEditMode
        pendingEditMode = null
        if (uri == null) return@rememberLauncherForActivityResult
        val path = UriUtils.copyUriToCache(context, uri) ?: uri.toString()
        if (editMode != null) {
            onPickImageForEdit(path, editMode)
        } else {
            onPickImageForOcr(path)
        }
    }

    val pickImagesForExport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val targetExt = pendingExportFormat
        pendingExportFormat = null
        if (uris.isEmpty() || targetExt.isNullOrBlank()) return@rememberLauncherForActivityResult
        val paths = uris.mapNotNull { UriUtils.copyUriToCache(context, it) }
        if (paths.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        viewModel.buildDocumentFromImages(
            paths = paths,
            prefix = "工具导入",
            onDone = { docId ->
                busy = false
                onQuickExport(docId, targetExt)
            },
            onError = { err ->
                busy = false
                scope.launch { snackbar.showSnackbar(err) }
            }
        )
    }

    val tools = listOf(
        Tool(Icons.Outlined.CameraAlt, R.string.tool_scan, R.string.tool_scan_desc, onScanClick),
        Tool(Icons.Outlined.TextFields, R.string.tool_ocr, R.string.tool_ocr_desc) {
            pendingEditMode = null
            pickImage.launch("image/*")
        },
        Tool(Icons.Outlined.Crop, R.string.tool_crop, R.string.tool_crop_desc) {
            pendingEditMode = "crop"
            pickImage.launch("image/*")
        },
        Tool(Icons.Outlined.Flip, R.string.tool_flip, R.string.tool_flip_desc) {
            pendingEditMode = "flip"
            pickImage.launch("image/*")
        },
        Tool(Icons.Outlined.VisibilityOff, R.string.tool_redact, R.string.tool_redact_desc) {
            pendingEditMode = "redact"
            pickImage.launch("image/*")
        },
        Tool(Icons.Outlined.PictureAsPdf, R.string.tool_to_pdf, R.string.tool_to_pdf_desc) {
            pendingExportFormat = "pdf"
            pickImagesForExport.launch("image/*")
        },
        Tool(Icons.Outlined.Description, R.string.tool_to_word, R.string.tool_to_word_desc) {
            pendingExportFormat = "docx"
            pickImagesForExport.launch("image/*")
        },
        Tool(Icons.Outlined.GridOn, R.string.tool_to_excel, R.string.tool_to_excel_desc) {
            pendingExportFormat = "xlsx"
            pickImagesForExport.launch("image/*")
        },
        Tool(Icons.Outlined.Image, R.string.tool_format, R.string.tool_format_desc, onFormatConvertClick),
        Tool(Icons.Outlined.GridOn, R.string.tool_batch, R.string.tool_batch_desc, onBatchClick),
        Tool(Icons.Outlined.Image, R.string.tool_stitch, R.string.tool_stitch_desc, onStitchClick),
        Tool(Icons.Outlined.PictureAsPdf, R.string.tool_pdf, R.string.tool_pdf_desc, onPdfToolsClick),
        Tool(Icons.Outlined.TextFields, R.string.tool_barcode, R.string.tool_barcode_desc, onBarcodeClick),
        Tool(Icons.Outlined.Badge, R.string.tool_idcard, R.string.tool_idcard_desc, onIdCardClick)
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tools_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = tools, key = { it.titleRes }) { tool ->
                    ToolCard(tool = tool, enabled = !busy)
                }
            }
        }
    }
}

private data class Tool(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolCard(tool: Tool, enabled: Boolean = true) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = tool.onClick,
        enabled = enabled
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(tool.titleRes),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(tool.descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
