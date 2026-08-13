package com.scanner.offline.ui.screen.tools

import android.graphics.Bitmap
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
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.StitchMode
import com.scanner.offline.domain.usecase.SaveEditedImageUseCase
import com.scanner.offline.engine.export.BitmapStitcher
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
class StitchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveEdited: SaveEditedImageUseCase
) : ViewModel() {
    fun stitch(uris: List<String>, mode: StitchMode, onDone: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bmps = uris.mapNotNull { decode(it) }
                    require(bmps.size >= 2) { "至少选两张图" }
                    val out = BitmapStitcher.stitch(bmps, mode)
                    val r = saveEdited(out, ExportFormat.JPG, Time.nowDocName("stitch"))
                    r.displayName
                }
            }.onSuccess(onDone).onFailure { onError(it.message ?: "拼接失败") }
        }
    }
    private fun decode(imageUri: String): Bitmap? {
        File(imageUri).takeIf { it.exists() }?.let { return BitmapUtils.decode(it) }
        val uri = Uri.parse(imageUri)
        return if (uri.scheme == "file" || uri.scheme == null) BitmapUtils.decode(File(uri.path ?: imageUri))
        else BitmapUtils.decode(context.contentResolver, uri)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StitchScreen(onBack: () -> Unit, viewModel: StitchViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var uris by remember { mutableStateOf<List<String>>(emptyList()) }
    var mode by remember { mutableStateOf(StitchMode.VERTICAL) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        uris = list.map { it.toString() }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("图片拼接") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
        })
    }, snackbarHost = { SnackbarHost(snack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { pick.launch(arrayOf("image/*")) }) { Text("选择多张图片") }
            Text("已选 ${uris.size} 张")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StitchMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.displayName) })
                }
            }
            Button(onClick = {
                viewModel.stitch(uris, mode,
                    onDone = { scope.launch { snack.showSnackbar("已保存 $it") } },
                    onError = { scope.launch { snack.showSnackbar(it) } })
            }, enabled = uris.size >= 2, modifier = Modifier.fillMaxWidth()) { Text("开始拼接") }
        }
    }
}
