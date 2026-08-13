package com.scanner.offline.ui.screen.tools

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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
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
    fun run(uris: List<String>, action: PdfToolAction, range: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val files = uris.mapNotNull { copyToCache(it) }
                    require(files.isNotEmpty()) { "请选择 PDF" }
                    when (action) {
                        PdfToolAction.MERGE -> {
                            val sink = storage.createExportSink(Time.nowDocName("merged"), "pdf", "application/pdf")
                            sink.openOutputStream().use { toolkit.merge(files, it) }
                            sink.displayName
                        }
                        PdfToolAction.SPLIT -> {
                            val pages = toolkit.renderPages(files.first())
                            pages.forEachIndexed { i, bmp ->
                                val sink = storage.createExportSink("${Time.nowDocName("page")}_p${i + 1}", "pdf", "application/pdf")
                                sink.openOutputStream().use { toolkit.writeBitmapsAsPdf(listOf(bmp), it) }
                                bmp.recycle()
                            }
                            "已拆成 ${pages.size} 个 PDF"
                        }
                        PdfToolAction.EXTRACT -> {
                            val idx = parseRange(range, toolkit.pageCount(files.first()))
                            val sink = storage.createExportSink(Time.nowDocName("extract"), "pdf", "application/pdf")
                            sink.openOutputStream().use { toolkit.extract(files.first(), idx, it) }
                            sink.displayName
                        }
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
                val (a, b) = p.split("-").map { it.trim().toInt() }
                for (i in a..b) out += (i - 1)
            } else out += (p.toInt() - 1)
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
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        uris = list.map { it.toString() }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("PDF 工具") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
        })
    }, snackbarHost = { SnackbarHost(snack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { pick.launch(arrayOf("application/pdf")) }) { Text("选择 PDF") }
            Text("已选 ${uris.size} 个文件")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = action == PdfToolAction.MERGE, onClick = { action = PdfToolAction.MERGE }, label = { Text("合并") })
                FilterChip(selected = action == PdfToolAction.SPLIT, onClick = { action = PdfToolAction.SPLIT }, label = { Text("拆页") })
                FilterChip(selected = action == PdfToolAction.EXTRACT, onClick = { action = PdfToolAction.EXTRACT }, label = { Text("抽页") })
            }
            if (action == PdfToolAction.EXTRACT) {
                OutlinedTextField(value = range, onValueChange = { range = it }, label = { Text("页码，如 1-3,5") }, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = {
                viewModel.run(uris, action, range,
                    onDone = { scope.launch { snack.showSnackbar(it) } },
                    onError = { scope.launch { snack.showSnackbar(it) } })
            }, enabled = uris.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("开始") }
        }
    }
}
