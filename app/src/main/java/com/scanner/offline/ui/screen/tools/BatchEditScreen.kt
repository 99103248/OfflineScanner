package com.scanner.offline.ui.screen.tools

import android.graphics.BitmapFactory
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.usecase.SaveEditedImageUseCase
import com.scanner.offline.engine.image.ImageTransform
import com.scanner.offline.util.BitmapUtils
import com.scanner.offline.util.Time
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BatchEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transform: ImageTransform,
    private val saveEdited: SaveEditedImageUseCase
) : ViewModel() {
    fun run(
        uris: List<String>,
        rotate90: Boolean,
        flipH: Boolean,
        format: ExportFormat,
        quality: Int,
        onDone: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    var n = 0
                    uris.forEach { u ->
                        var bmp = decode(u) ?: return@forEach
                        if (flipH) bmp = transform.flipHorizontal(bmp)
                        if (rotate90) bmp = transform.rotate90Clockwise(bmp)
                        saveEdited(bmp, format, Time.nowDocName("batch"), quality)
                        n++
                    }
                    n
                }
            }.onSuccess(onDone).onFailure { onError(it.message ?: "批量失败") }
        }
    }

    private fun decode(imageUri: String): android.graphics.Bitmap? {
        File(imageUri).takeIf { it.exists() }?.let { return BitmapUtils.decode(it) }
        val uri = Uri.parse(imageUri)
        return if (uri.scheme == "file" || uri.scheme == null) BitmapUtils.decode(File(uri.path ?: imageUri))
        else BitmapUtils.decode(context.contentResolver, uri)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchEditScreen(onBack: () -> Unit, viewModel: BatchEditViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var uris by remember { mutableStateOf<List<String>>(emptyList()) }
    var rotate by remember { mutableStateOf(false) }
    var flip by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(ExportFormat.JPG) }
    var quality by remember { mutableIntStateOf(80) }
    var running by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        list.forEach {
            runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        uris = list.map { it.toString() }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("批量处理") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
            })
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { pick.launch(arrayOf("image/*")) }) { Text("选择多张图片") }
            Text("已选 ${uris.size} 张")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = flip, onClick = { flip = !flip }, label = { Text("水平翻转") })
                FilterChip(selected = rotate, onClick = { rotate = !rotate }, label = { Text("旋转90°") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ExportFormat.JPG, ExportFormat.PNG, ExportFormat.WEBP).forEach { f ->
                    FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f.extension.uppercase()) })
                }
            }
            Text("压缩质量 $quality")
            Slider(value = quality.toFloat(), onValueChange = { quality = it.toInt() }, valueRange = 40f..100f)
            Button(
                onClick = {
                    running = true
                    viewModel.run(uris, rotate, flip, format, quality,
                        onDone = { n -> running = false; scope.launch { snack.showSnackbar("已导出 $n 张") } },
                        onError = { e -> running = false; scope.launch { snack.showSnackbar(e) } }
                    )
                },
                enabled = uris.isNotEmpty() && !running,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (running) "处理中..." else "开始批量导出") }
        }
    }
}
