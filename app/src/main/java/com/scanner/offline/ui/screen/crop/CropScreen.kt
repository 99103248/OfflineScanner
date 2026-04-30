package com.scanner.offline.ui.screen.crop

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanner.offline.R
import kotlin.math.hypot
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    imageUri: String,
    onCropped: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CropViewModel = hiltViewModel()
) {
    LaunchedEffect(imageUri) { viewModel.load(imageUri) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调整边框") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.confirm(
                                onDone = onCropped,
                                onError = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                    ) {
                        Icon(Icons.Outlined.Check, null)
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
                .background(Color.Black)
        ) {
            when (val s = state) {
                CropViewModel.CropState.Loading ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                is CropViewModel.CropState.Error ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(s.message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                is CropViewModel.CropState.Ready -> CropEditor(
                    bitmap = s.bitmap,
                    corners = s.corners,
                    onCornerMoved = viewModel::updateCorner
                )
            }
        }
    }
}

@Composable
private fun CropEditor(
    bitmap: android.graphics.Bitmap,
    corners: List<PointF>,
    onCornerMoved: (Int, PointF) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handleRadiusPx = with(density) { 12.dp.toPx() }
    val touchSlopPx = with(density) { 24.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        // 计算图片在 Canvas 中的实际显示矩形（保持纵横比，居中）
        val displayRect = remember(canvasSize, bitmap.width, bitmap.height) {
            calcDisplayRect(canvasSize, bitmap.width, bitmap.height)
        }

        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(displayRect) {
                    var draggingIndex = -1
                    detectDragGestures(
                        onDragStart = { offset ->
                            draggingIndex = nearestCorner(
                                offset, corners, displayRect, touchSlopPx
                            )
                        },
                        onDragEnd = { draggingIndex = -1 },
                        onDragCancel = { draggingIndex = -1 },
                        onDrag = { change, _ ->
                            change.consume()
                            if (draggingIndex < 0) return@detectDragGestures
                            val rel = displayRectInverse(change.position, displayRect)
                            onCornerMoved(draggingIndex, PointF(rel.x, rel.y))
                        }
                    )
                }
        ) {
            drawScaledImage(imageBitmap, bitmap.width, bitmap.height, displayRect)
            drawCropOverlay(corners, displayRect, handleRadiusPx)
        }
    }
}

private fun DrawScope.drawScaledImage(
    image: ImageBitmap,
    sourceWidth: Int,
    sourceHeight: Int,
    rect: DisplayRect
) {
    if (sourceWidth <= 0 || sourceHeight <= 0) return
    val sx = rect.width / sourceWidth
    val sy = rect.height / sourceHeight
    translate(left = rect.left, top = rect.top) {
        scale(scaleX = sx, scaleY = sy, pivot = Offset.Zero) {
            drawImage(image)
        }
    }
}

private fun DrawScope.drawCropOverlay(
    normalizedCorners: List<PointF>,
    rect: DisplayRect,
    handleRadius: Float
) {
    val path = Path()
    val pixelCorners = normalizedCorners.map {
        Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height)
    }
    pixelCorners.forEachIndexed { i, p ->
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color = Color(0x4D2D6CDF))
    drawPath(path, color = Color.White, style = Stroke(width = 4f))
    pixelCorners.forEach { p ->
        drawCircle(Color.White, radius = handleRadius, center = p)
        drawCircle(Color(0xFF2D6CDF), radius = handleRadius - 4f, center = p)
    }
}

private data class DisplayRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

private fun calcDisplayRect(canvas: IntSize, bw: Int, bh: Int): DisplayRect {
    if (canvas.width == 0 || canvas.height == 0 || bw == 0 || bh == 0) {
        return DisplayRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
    }
    val cAspect = canvas.width.toFloat() / canvas.height
    val bAspect = bw.toFloat() / bh
    return if (bAspect > cAspect) {
        // 宽图：以宽度铺满
        val w = canvas.width.toFloat()
        val h = w / bAspect
        DisplayRect(0f, (canvas.height - h) / 2f, w, h)
    } else {
        val h = canvas.height.toFloat()
        val w = h * bAspect
        DisplayRect((canvas.width - w) / 2f, 0f, w, h)
    }
}

private fun displayRectInverse(p: Offset, rect: DisplayRect): PointF {
    val x = ((p.x - rect.left) / rect.width).coerceIn(0f, 1f)
    val y = ((p.y - rect.top) / rect.height).coerceIn(0f, 1f)
    return PointF(x, y)
}

private fun nearestCorner(
    p: Offset,
    corners: List<PointF>,
    rect: DisplayRect,
    slop: Float
): Int {
    var best = -1
    var bestD = Float.MAX_VALUE
    corners.forEachIndexed { i, c ->
        val cx = rect.left + c.x * rect.width
        val cy = rect.top + c.y * rect.height
        val d = hypot(p.x - cx, p.y - cy)
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return if (bestD <= slop) best else -1
}

