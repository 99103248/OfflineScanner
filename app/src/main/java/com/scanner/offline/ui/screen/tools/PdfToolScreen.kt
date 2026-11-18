package com.scanner.offline.ui.screen.tools

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.engine.export.PdfToolkit
import com.scanner.offline.util.Time
import com.scanner.offline.util.UriUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

enum class PdfToolAction { MERGE, SPLIT, EXTRACT }

@HiltViewModel
class PdfToolViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val toolkit: PdfToolkit,
    private val storage: StorageManager
) : ViewModel() {
    fun run(
        uris: List<String>,
        action: PdfToolAction,
        range: String,
        onDone: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val files = uris.mapNotNull { copyToCache(it) }
                    require(files.isNotEmpty()) { "请选择 PDF" }
                    try {
                        when (action) {
                            PdfToolAction.MERGE -> {
                                val sink = storage.createExportSink(Time.nowDocName("merged"), "pdf", "application/pdf")
                                sink.openOutputStream().use { toolkit.merge(files, it) }
                                sink.displayName
                            }
                            PdfToolAction.SPLIT -> {
                                val baseName = Time.nowDocName("page")
                                val count = toolkit.splitStreaming(files.first()) { i, bmp ->
                                    val sink = storage.createExportSink("${baseName}_p${i + 1}", "pdf", "application/pdf")
                                    sink.openOutputStream().use { toolkit.writeBitmapsAsPdf(listOf(bmp), it) }
                                }
                                "已拆成 $count 个 PDF"
                            }
                            PdfToolAction.EXTRACT -> {
                                val idx = parseRange(range, toolkit.pageCount(files.first()))
                                require(idx.isNotEmpty()) { "请输入有效页码范围" }
                                val sink = storage.createExportSink(Time.nowDocName("extract"), "pdf", "application/pdf")
                                sink.openOutputStream().use { toolkit.extract(files.first(), idx, it) }
                                sink.displayName
                            }
                        }
                    } finally {
                        files.forEach { storage.deleteCacheFileIfTemp(it.absolutePath) }
                    }
                }
            }.onSuccess(onDone).onFailure { onError(it.message ?: "PDF 处理失败") }
        }
    }

    private fun parseRange(raw: String, pageCount: Int): List<Int> {
        val parts = raw.split(",", "，", " ").map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<Int>()
        parts.forEach { p ->
            if ("-" in p) {
                val bounds = p.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (bounds.size == 2) {
                    val a = minOf(bounds[0], bounds[1])
                    val b = maxOf(bounds[0], bounds[1])
                    for (i in a..b) out += (i - 1)
                }
            } else {
                p.toIntOrNull()?.let { out += (it - 1) }
            }
        }
        return out.filter { it in 0 until pageCount }.distinct()
    }

    private fun copyToCache(uriString: String): File? {
        File(uriString).takeIf { it.exists() }?.let { return it }
        val uri = Uri.parse(uriString)
        val out = storage.newCacheImage("pdf").let { File(it.parentFile, it.nameWithoutExtension + ".pdf") }
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { input.copyTo(it) }
        } ?: return null
        return out
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolScreen(onBack: () -> Unit, viewModel: PdfToolViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var uris by remember { mutableStateOf<List<String>>(emptyList()) }
    var action by remember { mutableStateOf(PdfToolAction.MERGE) }
    var range by remember { mutableStateOf("1-1") }
    var running by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        uris = list.mapNotNull { UriUtils.copyUriToCache(context, it, "pdf") }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("PDF 工具") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
        })
    }, snackbarHost = { SnackbarHost(snack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = { pick.launch(arrayOf("application/pdf")) },
                enabled = !running
            ) { Text("选择 PDF") }
            Text("已选 ${uris.size} 个文件")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = action == PdfToolAction.MERGE,
                    onClick = { action = PdfToolAction.MERGE },
                    label = { Text("合并") },
                    enabled = !running
                )
                FilterChip(
                    selected = action == PdfToolAction.SPLIT,
                    onClick = { action = PdfToolAction.SPLIT },
                    label = { Text("拆页") },
                    enabled = !running
                )
                FilterChip(
                    selected = action == PdfToolAction.EXTRACT,
                    onClick = { action = PdfToolAction.EXTRACT },
                    label = { Text("抽页") },
                    enabled = !running
                )
            }
            if (action == PdfToolAction.EXTRACT) {
                OutlinedTextField(
                    value = range,
                    onValueChange = { range = it },
                    label = { Text("页码，如 1-3,5") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !running
                )
            }
            Button(
                onClick = {
                    running = true
                    viewModel.run(
                        uris,
                        action,
                        range,
                        onDone = {
                            running = false
                            uris = emptyList()
                            scope.launch { snack.showSnackbar(it) }
                        },
                        onError = {
                            running = false
                            scope.launch { snack.showSnackbar(it) }
                        }
                    )
                },
                enabled = uris.isNotEmpty() && !running,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (running) "处理中..." else "开始") }
        }
    }
}
