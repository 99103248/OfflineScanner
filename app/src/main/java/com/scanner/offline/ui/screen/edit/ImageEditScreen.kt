package com.scanner.offline.ui.screen.edit

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanner.offline.R
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.engine.image.NormalizedCropRect
import com.scanner.offline.util.ShareUtils
import kotlin.math.hypot
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditScreen(
    imageUri: String,
    mode: ImageEditMode,
    onBack: () -> Unit,
    viewModel: ImageEditViewModel = hiltViewModel()
) {
    LaunchedEffect(mode) { viewModel.setMode(mode) }
    LaunchedEffect(imageUri) { viewModel.load(imageUri) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(state.error) {
        state.error?.let { scope.launch { snackbar.showSnackbar(it) } }
    }
    LaunchedEffect(state.lastResult) {
        state.lastResult?.let {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.success_export, it.displayName)) }
        }
    }

    val title = if (mode == ImageEditMode.CROP) {
        stringResource(R.string.tool_crop)
    } else {
        stringResource(R.string.tool_flip)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::reset, enabled = state.working != null && !state.saving) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.action_reset))
                    }
                    IconButton(
                        onClick = viewModel::save,
                        enabled = state.working != null && !state.saving && !state.loading
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = stringResource(R.string.action_save_as))
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
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.loading -> CircularProgressIndicator(color = Color.White)
                    state.working != null -> RectCropEditor(
                        bitmap = state.working!!,
                        cropRect = state.cropRect,
                        showOverlay = state.mode == ImageEditMode.CROP,
                        onCropChanged = viewModel::updateCrop
                    )
                    else -> Text(
                        stringResource(R.string.error_no_image),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            EditToolbar(
                mode = state.mode,
                format = state.format,
                saving = state.saving,
                lastResult = state.lastResult?.let { it.shareUri to it.mimeType },
                onMode = viewModel::setMode,
                onApplyCrop = viewModel::applyCrop,
                onFlipH = viewModel::flipHorizontal,
                onFlipV = viewModel::flipVertical,
                onRotate = viewModel::rotate90,
                onFormat = viewModel::setFormat,
                onShare = { uri, mime -> ShareUtils.share(context, uri, mime) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditToolbar(
    mode: ImageEditMode,
    format: ExportFormat,
    saving: Boolean,
    lastResult: Pair<android.net.Uri, String>?,
    onMode: (ImageEditMode) -> Unit,
    onApplyCrop: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onRotate: () -> Unit,
    onFormat: (ExportFormat) -> Unit,
    onShare: (android.net.Uri, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == ImageEditMode.CROP,
                onClick = { onMode(ImageEditMode.CROP) },
                label = { Text(stringResource(R.string.tool_crop)) },
                leadingIcon = { Icon(Icons.Outlined.Crop, null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = mode == ImageEditMode.FLIP,
                onClick = { onMode(ImageEditMode.FLIP) },
                label = { Text(stringResource(R.string.tool_flip)) },
                leadingIcon = { Icon(Icons.Outlined.Flip, null, modifier = Modifier.size(18.dp)) }
            )
        }

        val scroll = rememberScrollState()
        Row(
            modifier = Modifier.horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (mode == ImageEditMode.CROP) {
                TextButton(onClick = onApplyCrop, enabled = !saving) {
                    Text(stringResource(R.string.action_apply_crop), color = Color.White)
                }
            } else {
                IconButton(onClick = onFlipH, enabled = !saving) {
                    Icon(Icons.Outlined.Flip, stringResource(R.string.action_flip_h), tint = Color.White)
                }
                IconButton(onClick = onFlipV, enabled = !saving) {
                    Icon(Icons.Outlined.SwapVert, stringResource(R.string.action_flip_v), tint = Color.White)
                }
                IconButton(onClick = onRotate, enabled = !saving) {
                    Icon(Icons.Outlined.Rotate90DegreesCw, stringResource(R.string.action_rotate_90), tint = Color.White)
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_save_as), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
            listOf(ExportFormat.JPG, ExportFormat.PNG, ExportFormat.WEBP).forEach { f ->
                FilterChip(
                    selected = format == f,
                    onClick = { onFormat(f) },
                    label = { Text(f.extension.uppercase()) },
                    enabled = !saving
                )
            }
        }

        if (saving) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                Text(stringResource(R.string.saving_image), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }

        lastResult?.let { (uri, mime) ->
            AssistChip(
                onClick = { onShare(uri, mime) },
                label = { Text(stringResource(R.string.action_share)) },
                leadingIcon = { Icon(Icons.Outlined.IosShare, null) }
            )
        }
    }
}

@Composable
private fun RectCropEditor(
    bitmap: android.graphics.Bitmap,
    cropRect: NormalizedCropRect,
    showOverlay: Boolean,
    onCropChanged: (NormalizedCropRect) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handleRadiusPx = with(density) { 12.dp.toPx() }
    val touchSlopPx = with(density) { 28.dp.toPx() }
    val displayRect = remember(canvasSize, bitmap.width, bitmap.height) {
        calcDisplayRect(canvasSize, bitmap.width, bitmap.height)
    }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(displayRect, showOverlay, cropRect) {
                if (!showOverlay) return@pointerInput
                var dragging = -1
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = nearestHandle(offset, cropRect, displayRect, touchSlopPx)
                    },
                    onDragEnd = { dragging = -1 },
                    onDragCancel = { dragging = -1 },
                    onDrag = { change, _ ->
                        change.consume()
                        if (dragging < 0) return@detectDragGestures
                        val rel = displayRectInverse(change.position, displayRect)
                        onCropChanged(cropRect.moveCorner(dragging, rel.x, rel.y))
                    }
                )
            }
    ) {
        drawScaledImage(imageBitmap, bitmap.width, bitmap.height, displayRect)
        if (showOverlay) {
            drawRectCropOverlay(cropRect, displayRect, handleRadiusPx)
        }
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

private fun nearestHandle(
    p: Offset,
    crop: NormalizedCropRect,
    rect: DisplayRect,
    slop: Float
): Int {
    val corners = listOf(
        Offset(rect.left + crop.left * rect.width, rect.top + crop.top * rect.height),
        Offset(rect.left + crop.right * rect.width, rect.top + crop.top * rect.height),
        Offset(rect.left + crop.right * rect.width, rect.top + crop.bottom * rect.height),
        Offset(rect.left + crop.left * rect.width, rect.top + crop.bottom * rect.height)
    )
    var best = -1
    var bestD = Float.MAX_VALUE
    corners.forEachIndexed { i, c ->
        val d = hypot(p.x - c.x, p.y - c.y)
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return if (bestD <= slop) best else -1
}

private fun DrawScope.drawScaledImage(
    image: androidx.compose.ui.graphics.ImageBitmap,
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

private fun DrawScope.drawRectCropOverlay(
    crop: NormalizedCropRect,
    rect: DisplayRect,
    handleRadius: Float
) {
    val l = rect.left + crop.left * rect.width
    val t = rect.top + crop.top * rect.height
    val r = rect.left + crop.right * rect.width
    val b = rect.top + crop.bottom * rect.height
    val path = Path().apply {
        moveTo(l, t)
        lineTo(r, t)
        lineTo(r, b)
        lineTo(l, b)
        close()
    }
    drawRect(Color(0x66000000), topLeft = Offset(rect.left, rect.top), size = androidx.compose.ui.geometry.Size(rect.width, t - rect.top))
    drawRect(Color(0x66000000), topLeft = Offset(rect.left, b), size = androidx.compose.ui.geometry.Size(rect.width, rect.top + rect.height - b))
    drawRect(Color(0x66000000), topLeft = Offset(rect.left, t), size = androidx.compose.ui.geometry.Size(l - rect.left, b - t))
    drawRect(Color(0x66000000), topLeft = Offset(r, t), size = androidx.compose.ui.geometry.Size(rect.left + rect.width - r, b - t))
    drawPath(path, color = Color.White, style = Stroke(width = 3f))
    listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(l, b)).forEach { p ->
        drawCircle(Color.White, radius = handleRadius, center = p)
        drawCircle(Color(0xFF2D6CDF), radius = handleRadius - 4f, center = p)
    }
}
