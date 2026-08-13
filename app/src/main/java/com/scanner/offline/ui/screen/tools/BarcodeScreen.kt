package com.scanner.offline.ui.screen.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.scanner.offline.engine.ocr.BarcodeHit
import com.scanner.offline.engine.ocr.BarcodeScannerEngine
import com.scanner.offline.util.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: BarcodeScannerEngine
) : ViewModel() {
    fun scan(uriString: String, onDone: (List<BarcodeHit>) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bmp = decode(uriString) ?: error("无法读取图片")
                    scanner.scan(bmp)
                }
            }.onSuccess(onDone).onFailure { onError(it.message ?: "识别失败") }
        }
    }
    private fun decode(imageUri: String) = File(imageUri).takeIf { it.exists() }?.let { BitmapUtils.decode(it) }
        ?: run {
            val uri = Uri.parse(imageUri)
            if (uri.scheme == "file" || uri.scheme == null) BitmapUtils.decode(File(uri.path ?: imageUri))
            else BitmapUtils.decode(context.contentResolver, uri)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(onBack: () -> Unit, viewModel: BarcodeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var hits by remember { mutableStateOf<List<BarcodeHit>>(emptyList()) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.toString()?.let { u ->
            viewModel.scan(u,
                onDone = { hits = it; if (it.isEmpty()) scope.launch { snack.showSnackbar("未识别到条码") } },
                onError = { scope.launch { snack.showSnackbar(it) } })
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("扫码 / 条码") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
        })
    }, snackbarHost = { SnackbarHost(snack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { pick.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("从相册识别") }
            hits.forEach { h ->
                Text("${h.format}: ${h.rawValue}")
                Button(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("barcode", h.rawValue))
                    scope.launch { snack.showSnackbar("已复制") }
                }) { Text("复制") }
            }
        }
    }
}
