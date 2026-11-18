package com.scanner.offline.ui.screen.camera

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.scanner.offline.R
import com.scanner.offline.engine.image.EdgeDetector
import com.scanner.offline.util.UriUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val LIGHT_MODE_OFF = 0
private const val LIGHT_MODE_AUTO = 1
private const val LIGHT_MODE_FLASH = 2
private const val LIGHT_MODE_TORCH = 3

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onCaptured: (String) -> Unit,
    onBurstDone: (List<String>) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: Executor = remember { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val edgeDetector = remember { EdgeDetector() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { analysisExecutor.shutdown() }
        }
    }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) { if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest() }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val path = UriUtils.copyUriToCache(context, it) ?: it.toString()
            onCaptured(path)
        }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var burstMode by remember { mutableStateOf(false) }
    var burstUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var lightMode by remember { mutableIntStateOf(LIGHT_MODE_OFF) }
    var showGrid by remember { mutableStateOf(true) }
    var liveCorners by remember { mutableStateOf<List<PointF>?>(null) }

    LaunchedEffect(imageCapture, camera, lightMode) {
        when (lightMode) {
            LIGHT_MODE_OFF -> {
                runCatching { camera?.cameraControl?.enableTorch(false) }
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
            }
            LIGHT_MODE_AUTO -> {
                runCatching { camera?.cameraControl?.enableTorch(false) }
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO
            }
            LIGHT_MODE_FLASH -> {
                runCatching { camera?.cameraControl?.enableTorch(false) }
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
            }
            LIGHT_MODE_TORCH -> {
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                runCatching { camera?.cameraControl?.enableTorch(true) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (burstMode) "连拍 ${burstUris.size}" else stringResource(R.string.action_take_photo)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        lightMode = (lightMode + 1) % 4
                    }) {
                        Icon(
                            when (lightMode) {
                                LIGHT_MODE_FLASH -> Icons.Outlined.FlashOn
                                LIGHT_MODE_AUTO -> Icons.Outlined.FlashAuto
                                LIGHT_MODE_TORCH -> Icons.Outlined.FlashlightOn
                                else -> Icons.Outlined.FlashOff
                            },
                            contentDescription = "补光/闪光灯",
                            tint = if (lightMode == LIGHT_MODE_TORCH) Color(0xFFFFD54F) else Color.White
                        )
                    }
                    IconButton(onClick = { showGrid = !showGrid }) {
                        Icon(
                            Icons.Outlined.GridOn,
                            contentDescription = "网格",
                            tint = if (showGrid) Color.White else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    TextButton(onClick = { burstMode = !burstMode }) {
                        Text(if (burstMode) "单拍" else "连拍", color = Color.White)
                    }
                    if (burstMode && burstUris.isNotEmpty()) {
                        TextButton(onClick = { onBurstDone(burstUris) }) {
                            Text("完成(${burstUris.size})", color = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (cameraPermission.status.isGranted) {
                key(lensFacing) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(camera, previewView) {
                                detectTapGestures { offset ->
                                    val cam = camera ?: return@detectTapGestures
                                    val pv = previewView ?: return@detectTapGestures
                                    val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                                    runCatching {
                                        cam.cameraControl.startFocusAndMetering(
                                            FocusMeteringAction.Builder(point).build()
                                        )
                                    }
                                }
                            },
                        factory = { ctx ->
                            val view = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            previewView = view
                            bindCamera(
                                context = ctx,
                                previewView = view,
                                lifecycleOwner = lifecycleOwner,
                                lensFacing = lensFacing,
                                analysisExecutor = analysisExecutor,
                                edgeDetector = edgeDetector,
                                onCornersDetected = { corners ->
                                    executor.execute { liveCorners = corners }
                                },
                                onReady = { cam, capture ->
                                    camera = cam
                                    imageCapture = capture
                                }
                            )
                            view
                        }
                    )
                }
                liveCorners?.let { quad ->
                    LiveEdgeOverlay(corners = quad, modifier = Modifier.fillMaxSize())
                }
                if (showGrid) {
                    CameraGrid(modifier = Modifier.fillMaxSize())
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.error_camera_permission),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                if (burstMode && burstUris.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(vertical = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(burstUris) { idx, uriStr ->
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = Uri.parse(uriStr),
                                    contentDescription = "第 ${idx + 1} 张",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                        .clickable {
                                            val removed = burstUris[idx]
                                            burstUris = burstUris.toMutableList().also { it.removeAt(idx) }
                                            runCatching { Uri.parse(removed).path?.let { File(it).delete() } }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "移除此页",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, top = 12.dp, start = 24.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { pickImage.launch("image/*") }) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    ShutterButton(enabled = imageCapture != null && cameraPermission.status.isGranted) {
                        val capture = imageCapture ?: return@ShutterButton
                        val outFile = File(
                            context.cacheDir,
                            "tmp/cap_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
                        ).also { it.parentFile?.mkdirs() }
                        val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                        capture.takePicture(
                            opts,
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                                    val uri = Uri.fromFile(outFile).toString()
                                    if (burstMode) burstUris = burstUris + uri
                                    else onCaptured(uri)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("拍摄失败：${exception.message ?: ""}")
                                    }
                                }
                            }
                        )
                    }

                    IconButton(onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Cameraswitch,
                            contentDescription = "切换摄像头",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveEdgeOverlay(corners: List<PointF>, modifier: Modifier = Modifier) {
    if (corners.size != 4) return
    Canvas(modifier = modifier) {
        val pts = corners.map { Offset(it.x * size.width, it.y * size.height) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            lineTo(pts[1].x, pts[1].y)
            lineTo(pts[2].x, pts[2].y)
            lineTo(pts[3].x, pts[3].y)
            close()
        }
        drawPath(path, color = Color(0x382D6CDF))
        drawPath(path, color = Color(0xFF64B5F6), style = Stroke(width = 3.dp.toPx()))
        pts.forEach { p ->
            drawCircle(color = Color.White, radius = 6.dp.toPx(), center = p)
            drawCircle(color = Color(0xFF2D6CDF), radius = 4.dp.toPx(), center = p)
        }
    }
}

@Composable
private fun CameraGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val color = Color.White.copy(alpha = 0.35f)
        val stroke = 1.dp.toPx()
        for (i in 1..2) {
            val x = size.width * i / 3f
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
            val y = size.height * i / 3f
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

private fun bindCamera(
    context: Context,
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    lensFacing: Int,
    analysisExecutor: Executor,
    edgeDetector: EdgeDetector,
    onCornersDetected: (List<PointF>?) -> Unit,
    onReady: (Camera, ImageCapture) -> Unit
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = providerFuture.get()
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        var lastAnalyzedAt = 0L
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(analysisExecutor) { imageProxy ->
                    val now = System.currentTimeMillis()
                    if (now - lastAnalyzedAt < 220L) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    lastAnalyzedAt = now
                    try {
                        val rawBmp = imageProxy.toBitmap()
                        val deg = imageProxy.imageInfo.rotationDegrees
                        val oriented = if (deg != 0) {
                            val m = Matrix().apply { postRotate(deg.toFloat()) }
                            Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, m, false)
                                .also { if (it !== rawBmp) rawBmp.recycle() }
                        } else rawBmp
                        val quad = edgeDetector.detectQuadOrNull(oriented)
                        oriented.recycle()
                        onCornersDetected(quad)
                    } catch (_: Throwable) {
                        // 忽略单帧异常
                    } finally {
                        imageProxy.close()
                    }
                }
            }
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        runCatching {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
            onReady(camera, capture)
        }
    }, ContextCompat.getMainExecutor(context))
}
