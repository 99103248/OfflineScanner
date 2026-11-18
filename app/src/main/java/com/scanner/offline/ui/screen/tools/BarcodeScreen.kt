package com.scanner.offline.ui.screen.tools

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.engine.ocr.BarcodeHit
import com.scanner.offline.engine.ocr.BarcodeScannerEngine
import com.scanner.offline.util.BitmapUtils
import com.scanner.offline.util.UriUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: BarcodeScannerEngine,
    private val storage: StorageManager
) : ViewModel() {

    fun scan(uriString: String, onDone: (List<BarcodeHit>) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bmp = decode(uriString) ?: error("无法读取图片")
                    try {
                        scanner.scan(bmp)
                    } finally {
                        bmp.recycle()
                        storage.deleteCacheFileIfTemp(uriString)
                    }
                }
            }.onSuccess(onDone).onFailure { onError(it.message ?: "识别失败") }
        }
    }

    fun scanBitmap(bitmap: Bitmap, onFound: (List<BarcodeHit>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    try {
                        scanner.scan(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }.onSuccess { list ->
                if (list.isNotEmpty()) onFound(list)
            }
        }
    }

    private fun decode(imageUri: String) =
        File(imageUri).takeIf { it.exists() }?.let { BitmapUtils.decode(it) }
            ?: run {
                val uri = Uri.parse(imageUri)
                if (uri.scheme == "file" || uri.scheme == null) BitmapUtils.decode(File(uri.path ?: imageUri))
                else BitmapUtils.decode(context.contentResolver, uri)
            }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScreen(onBack: () -> Unit, viewModel: BarcodeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    var hits by remember { mutableStateOf<List<BarcodeHit>>(emptyList()) }
    var liveScan by remember { mutableStateOf(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { runCatching { analysisExecutor.shutdown() } }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u ->
            liveScan = false
            val path = UriUtils.copyUriToCache(context, u) ?: u.toString()
            viewModel.scan(
                path,
                onDone = {
                    hits = it
                    if (it.isEmpty()) scope.launch { snack.showSnackbar("未识别到条码") }
                },
                onError = { scope.launch { snack.showSnackbar(it) } }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫码 / 条码") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (!cameraPermission.status.isGranted) {
                            cameraPermission.launchPermissionRequest()
                        }
                        liveScan = !liveScan
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.PhotoCamera, null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text(if (liveScan) "停止相机扫码" else "相机实时扫码")
                }
                OutlinedButton(
                    onClick = { pick.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text("从相册识别")
                }
            }

            if (liveScan) {
                if (cameraPermission.status.isGranted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val future = ProcessCameraProvider.getInstance(ctx)
                                future.addListener({
                                    val provider = future.get()
                                    val preview = Preview.Builder().build().apply {
                                        setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    var lastTs = 0L
                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .apply {
                                            setAnalyzer(analysisExecutor) { proxy ->
                                                val now = System.currentTimeMillis()
                                                if (now - lastTs < 300L) {
                                                    proxy.close()
                                                    return@setAnalyzer
                                                }
                                                lastTs = now
                                                try {
                                                    val raw = proxy.toBitmap()
                                                    val deg = proxy.imageInfo.rotationDegrees
                                                    val bmp = if (deg != 0) {
                                                        val m = Matrix().apply { postRotate(deg.toFloat()) }
                                                        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, false)
                                                            .also { if (it !== raw) raw.recycle() }
                                                    } else raw
                                                    viewModel.scanBitmap(bmp) { found ->
                                                        hits = found
                                                        liveScan = false
                                                        runCatching { provider.unbindAll() }
                                                    }
                                                } catch (_: Throwable) {
                                                } finally {
                                                    proxy.close()
                                                }
                                            }
                                        }
                                    runCatching {
                                        provider.unbindAll()
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            analysis
                                        )
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            }
                        )
                    }
                } else {
                    Text(
                        "请授予相机权限以使用实时扫码，或点击「从相册识别」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            hits.forEach { h ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(h.format, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(h.rawValue, style = MaterialTheme.typography.bodyLarge)
                        AssistChip(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("barcode", h.rawValue))
                                scope.launch { snack.showSnackbar("已复制") }
                            },
                            label = { Text("复制内容") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }
                        )
                    }
                }
            }
        }
    }
}
