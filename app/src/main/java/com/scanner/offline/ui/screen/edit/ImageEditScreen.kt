package com.scanner.offline.ui.screen.edit

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
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
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanner.offline.R
import com.scanner.offline.domain.model.CropAspect
import com.scanner.offline.domain.model.CropStyle
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.RedactStyle
import com.scanner.offline.domain.model.WatermarkPosition
import com.scanner.offline.engine.image.NormalizedCropRect
import com.scanner.offline.util.ShareUtils
import kotlin.math.hypot
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditScreen(
    imageUri: String,
    mode: ImageEditMode,
    pageId: Long? = null,
    onBack: () -> Unit,
    viewModel: ImageEditViewModel = hiltViewModel()
) {
    LaunchedEffect(mode) { viewModel.setMode(mode) }
    LaunchedEffect(pageId) { viewModel.bindPage(pageId) }
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

    val title = when (mode) {
        ImageEditMode.CROP -> stringResource(R.string.tool_crop)
        ImageEditMode.MARK -> "批注与水印"
        ImageEditMode.REDACT -> "打码脱敏"
        else -> stringResource(R.string.tool_flip)
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
                    state.working != null -> UniversalCropEditor(
                        bitmap = state.working!!,
                        mode = state.mode,
                        cropStyle = state.cropStyle,
                        cropRect = state.cropRect,
                        perspectiveCorners = state.perspectiveCorners,
                        freeformPoints = state.freeformPoints,
                        strokes = state.strokes,
                        redactRects = state.redactRects,
                        redactStyle = state.redactStyle,
                        onRectCornerMoved = viewModel::moveCropCorner,
                        onRectEdgeMoved = viewModel::moveCropEdge,
                        onRectTranslate = viewModel::translateCrop,
                        onPerspectiveCornerMoved = viewModel::updatePerspectiveCorner,
                        onPerspectiveEdgeMoved = viewModel::movePerspectiveEdge,
                        onPerspectiveTranslate = viewModel::translatePerspective,
                        onFreeformPointsChanged = viewModel::setFreeformPoints,
                        onStroke = viewModel::addStrokePoint,
                        onRedactRect = viewModel::addRedactRect
                    )
                    else -> Text(
                        stringResource(R.string.error_no_image),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            EditToolbar(
                state = state,
                saving = state.saving,
                onMode = viewModel::setMode,
                onCropStyle = viewModel::setCropStyle,
                onDetectCorners = viewModel::detectPerspectiveCorners,
                onResetPerspectiveCorners = viewModel::resetPerspectiveToFull,
                onClearFreeform = viewModel::clearFreeformPoints,
                onApplyCrop = viewModel::applyCrop,
                onCropAspect = viewModel::setCropAspect,
                onFlipH = viewModel::flipHorizontal,
                onFlipV = viewModel::flipVertical,
                onRotate = viewModel::rotate90,
                onRotateCcw = viewModel::rotate90Ccw,
                onFreeAngle = viewModel::setFreeAngle,
                onApplyAngle = viewModel::applyFreeAngle,
                onFormat = viewModel::setFormat,
                onQuality = viewModel::setQuality,
                onWatermark = viewModel::setWatermark,
                onWatermarkPos = viewModel::setWatermarkPos,
                onApplyWatermark = viewModel::applyWatermark,
                onApplyStrokes = viewModel::applyStrokes,
                onRedactStyle = viewModel::setRedactStyle,
                onUndoRedact = viewModel::undoRedactRect,
                onClearRedact = viewModel::clearRedactRects,
                onApplyRedact = viewModel::applyRedaction,
                onShare = { uri, mime -> ShareUtils.share(context, uri, mime) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditToolbar(
    state: ImageEditUiState,
    saving: Boolean,
    onMode: (ImageEditMode) -> Unit,
    onCropStyle: (CropStyle) -> Unit,
    onDetectCorners: () -> Unit,
    onResetPerspectiveCorners: () -> Unit,
    onClearFreeform: () -> Unit,
    onApplyCrop: () -> Unit,
    onCropAspect: (CropAspect) -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onRotate: () -> Unit,
    onRotateCcw: () -> Unit,
    onFreeAngle: (Float) -> Unit,
    onApplyAngle: () -> Unit,
    onFormat: (ExportFormat) -> Unit,
    onQuality: (Int) -> Unit,
    onWatermark: (String) -> Unit,
    onWatermarkPos: (WatermarkPosition) -> Unit,
    onApplyWatermark: () -> Unit,
    onApplyStrokes: () -> Unit,
    onRedactStyle: (RedactStyle) -> Unit,
    onUndoRedact: () -> Unit,
    onClearRedact: () -> Unit,
    onApplyRedact: () -> Unit,
    onShare: (android.net.Uri, String) -> Unit
) {
    val mode = state.mode
    val format = state.format
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == ImageEditMode.CROP, onClick = { onMode(ImageEditMode.CROP) }, label = { Text("裁剪") })
            FilterChip(selected = mode == ImageEditMode.FLIP, onClick = { onMode(ImageEditMode.FLIP) }, label = { Text("翻转") })
            FilterChip(selected = mode == ImageEditMode.MARK, onClick = { onMode(ImageEditMode.MARK) }, label = { Text("批注") })
            FilterChip(selected = mode == ImageEditMode.REDACT, onClick = { onMode(ImageEditMode.REDACT) }, label = { Text("打码") })
        }

        if (mode == ImageEditMode.CROP) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CropStyle.entries.forEach { s ->
                    FilterChip(
                        selected = state.cropStyle == s,
                        onClick = { onCropStyle(s) },
                        label = { Text(s.displayName) }
                    )
                }
            }
        }

        val scroll = rememberScrollState()
        Row(
            modifier = Modifier.horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (mode) {
                ImageEditMode.CROP -> {
                    when (state.cropStyle) {
                        CropStyle.RECTANGLE -> {
                            CropAspect.entries.forEach { a ->
                                FilterChip(selected = state.cropAspect == a, onClick = { onCropAspect(a) }, label = { Text(a.displayName) })
                            }
                            TextButton(onClick = onApplyCrop, enabled = !saving) {
                                Text("应用裁剪", color = Color.White)
                            }
                        }
                        CropStyle.PERSPECTIVE -> {
                            TextButton(onClick = onDetectCorners, enabled = !saving) {
                                Text("自动识别", color = Color.White)
                            }
                            TextButton(onClick = onResetPerspectiveCorners, enabled = !saving) {
                                Text("铺满", color = Color.White)
                            }
                            TextButton(onClick = onApplyCrop, enabled = !saving && state.perspectiveCorners.size == 4) {
                                Text("应用拉平", color = Color.White)
                            }
                        }
                        CropStyle.FREEFORM -> {
                            TextButton(onClick = onClearFreeform, enabled = !saving && state.freeformPoints.isNotEmpty()) {
                                Text("重画", color = Color.White)
                            }
                            Text(
                                text = if (state.freeformPoints.isEmpty()) "手绘封闭轮廓" else "已形成套索",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            TextButton(onClick = onApplyCrop, enabled = !saving && state.freeformPoints.size >= 3) {
                                Text("应用裁剪", color = Color.White)
                            }
                        }
                    }
                }
                ImageEditMode.FLIP -> {
                    IconButton(onClick = onFlipH, enabled = !saving) {
                        Icon(Icons.Outlined.Flip, "水平翻转", tint = Color.White)
                    }
                    IconButton(onClick = onFlipV, enabled = !saving) {
                        Icon(Icons.Outlined.SwapVert, "垂直翻转", tint = Color.White)
                    }
                    IconButton(onClick = onRotate, enabled = !saving) {
                        Icon(Icons.Outlined.Rotate90DegreesCw, "顺时针", tint = Color.White)
                    }
                    IconButton(onClick = onRotateCcw, enabled = !saving) {
                        Icon(Icons.Outlined.Rotate90DegreesCcw, "逆时针", tint = Color.White)
                    }
                }
                ImageEditMode.MARK -> {
                    TextButton(onClick = onApplyStrokes, enabled = !saving) {
                        Text("应用批注", color = Color.White)
                    }
                    TextButton(onClick = onApplyWatermark, enabled = !saving && state.watermark.isNotBlank()) {
                        Text("加水印", color = Color.White)
                    }
                }
                ImageEditMode.REDACT -> {
                    RedactStyle.entries.forEach { s ->
                        FilterChip(
                            selected = state.redactStyle == s,
                            onClick = { onRedactStyle(s) },
                            label = { Text(s.displayName) }
                        )
                    }
                    TextButton(onClick = onUndoRedact, enabled = !saving && state.redactRects.isNotEmpty()) {
                        Text("撤销", color = Color.White)
                    }
                    TextButton(onClick = onClearRedact, enabled = !saving && state.redactRects.isNotEmpty()) {
                        Text("清空", color = Color.White)
                    }
                    TextButton(onClick = onApplyRedact, enabled = !saving && state.redactRects.isNotEmpty()) {
                        Text("应用打码", color = Color.White)
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Text("另存为", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
            listOf(ExportFormat.JPG, ExportFormat.PNG, ExportFormat.WEBP).forEach { f ->
                FilterChip(selected = format == f, onClick = { onFormat(f) }, label = { Text(f.extension.uppercase()) }, enabled = !saving)
            }
        }

        if (mode == ImageEditMode.FLIP) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("角度 ${state.freeAngle.toInt()}°", color = Color.White, modifier = Modifier.width(88.dp))
                Slider(
                    value = state.freeAngle,
                    onValueChange = onFreeAngle,
                    valueRange = -45f..45f,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onApplyAngle) { Text("旋转", color = Color.White) }
            }
        }
        if (mode == ImageEditMode.MARK) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = state.watermark,
                    onValueChange = onWatermark,
                    textStyle = TextStyle(color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    decorationBox = { inner ->
                        Box {
                            if (state.watermark.isEmpty()) Text("水印文字", color = Color.White.copy(0.5f))
                            inner()
                        }
                    }
                )
                WatermarkPosition.entries.forEach { p ->
                    FilterChip(selected = state.watermarkPos == p, onClick = { onWatermarkPos(p) }, label = { Text(p.displayName) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("质量 ${state.quality}", color = Color.White, style = MaterialTheme.typography.labelLarge)
            Slider(
                value = state.quality.toFloat(),
                onValueChange = { onQuality(it.toInt()) },
                valueRange = 40f..100f,
                modifier = Modifier.weight(1f)
            )
        }
        if (saving) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                Text("正在保存...", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
        state.lastResult?.let { r ->
            AssistChip(
                onClick = { onShare(r.shareUri, r.mimeType) },
                label = { Text(stringResource(R.string.action_share)) },
                leadingIcon = { Icon(Icons.Outlined.IosShare, null) }
            )
        }
    }
}

@Composable
private fun UniversalCropEditor(
    bitmap: android.graphics.Bitmap,
    mode: ImageEditMode,
    cropStyle: CropStyle,
    cropRect: NormalizedCropRect,
    perspectiveCorners: List<PointF>,
    freeformPoints: List<PointF>,
    strokes: List<List<Pair<Float, Float>>>,
    redactRects: List<NormalizedCropRect>,
    redactStyle: RedactStyle,
    onRectCornerMoved: (Int, Float, Float) -> Unit,
    onRectEdgeMoved: (Int, Float) -> Unit,
    onRectTranslate: (Float, Float) -> Unit,
    onPerspectiveCornerMoved: (Int, PointF) -> Unit,
    onPerspectiveEdgeMoved: (Int, Float, Float) -> Unit,
    onPerspectiveTranslate: (Float, Float) -> Unit,
    onFreeformPointsChanged: (List<PointF>) -> Unit,
    onStroke: (Float, Float, Boolean) -> Unit,
    onRedactRect: (NormalizedCropRect) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handleRadiusPx = with(density) { 14.dp.toPx() }
    val cornerSlopPx = with(density) { 40.dp.toPx() }
    val edgeSlopPx = with(density) { 26.dp.toPx() }

    val displayRect = remember(canvasSize, bitmap.width, bitmap.height) {
        calcDisplayRect(canvasSize, bitmap.width, bitmap.height)
    }
    val currentDisplayRect by rememberUpdatedState(displayRect)
    val imageBitmap = remember(bitmap) {
        if (!bitmap.isRecycled) bitmap.asImageBitmap() else null
    }

    var localFreeformPoints by remember { mutableStateOf(freeformPoints) }
    LaunchedEffect(freeformPoints) {
        localFreeformPoints = freeformPoints
    }
    var localRedactRect by remember { mutableStateOf<NormalizedCropRect?>(null) }

    val currentCropRect by rememberUpdatedState(cropRect)
    val currentPerspectiveCorners by rememberUpdatedState(perspectiveCorners)
    val currentOnRectCornerMoved by rememberUpdatedState(onRectCornerMoved)
    val currentOnRectEdgeMoved by rememberUpdatedState(onRectEdgeMoved)
    val currentOnRectTranslate by rememberUpdatedState(onRectTranslate)
    val currentOnPerspectiveCornerMoved by rememberUpdatedState(onPerspectiveCornerMoved)
    val currentOnPerspectiveEdgeMoved by rememberUpdatedState(onPerspectiveEdgeMoved)
    val currentOnPerspectiveTranslate by rememberUpdatedState(onPerspectiveTranslate)
    val currentOnFreeformChanged by rememberUpdatedState(onFreeformPointsChanged)
    val currentOnStroke by rememberUpdatedState(onStroke)
    val currentOnRedactRect by rememberUpdatedState(onRedactRect)

    var activeCornerIndex by remember { mutableStateOf(-1) }
    var activeEdgeIndex by remember { mutableStateOf(-1) }

    val isCropMode = mode == ImageEditMode.CROP
    val isMarkMode = mode == ImageEditMode.MARK
    val isRedactMode = mode == ImageEditMode.REDACT

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(isCropMode, isMarkMode, isRedactMode, cropStyle) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val rect = currentDisplayRect
                            if (rect.width <= 0f || rect.height <= 0f) continue
                            val downPos = down.position

                            if (isMarkMode) {
                                down.consume()
                                val rel = displayRectInverse(downPos, rect)
                                currentOnStroke(rel.x, rel.y, true)

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    val curRel = displayRectInverse(change.position, rect)
                                    currentOnStroke(curRel.x, curRel.y, false)
                                }
                            } else if (isRedactMode) {
                                down.consume()
                                val start = displayRectInverse(downPos, rect)
                                var current = NormalizedCropRect(start.x, start.y, start.x, start.y)
                                localRedactRect = current
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        change.consume()
                                        val p = displayRectInverse(change.position, rect)
                                        current = NormalizedCropRect(
                                            left = minOf(start.x, p.x),
                                            top = minOf(start.y, p.y),
                                            right = maxOf(start.x, p.x),
                                            bottom = maxOf(start.y, p.y)
                                        )
                                        localRedactRect = current
                                    }
                                } finally {
                                    if (current.width >= 0.02f && current.height >= 0.02f) {
                                        currentOnRedactRect(current)
                                    }
                                    localRedactRect = null
                                }
                            } else if (isCropMode) {
                                when (cropStyle) {
                                    CropStyle.RECTANGLE -> {
                                        val crop = currentCropRect
                                        val corner = findNearestRectHandle(downPos, crop, rect, cornerSlopPx)
                                        val edge = if (corner < 0) findNearestRectEdge(downPos, crop, rect, edgeSlopPx) else -1
                                        val isInside = if (corner < 0 && edge < 0) isInsideCropRect(downPos, crop, rect) else false

                                        if (corner < 0 && edge < 0 && !isInside) continue

                                        down.consume()
                                        activeCornerIndex = corner
                                        activeEdgeIndex = edge

                                        val cornerAnchorOffset = if (corner in 0..3) {
                                            val cp = getRectCornerOffset(corner, crop, rect)
                                            downPos - cp
                                        } else Offset.Zero

                                        var lastPos = downPos

                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (!change.pressed) break

                                                val curPos = change.position
                                                val delta = curPos - lastPos
                                                lastPos = curPos
                                                change.consume()

                                                if (corner >= 0) {
                                                    val targetPixelPos = curPos - cornerAnchorOffset
                                                    val rel = displayRectInverse(targetPixelPos, rect)
                                                    currentOnRectCornerMoved(corner, rel.x, rel.y)
                                                } else if (edge >= 0) {
                                                    val rel = displayRectInverse(curPos, rect)
                                                    val pos = if (edge == 0 || edge == 2) rel.y else rel.x
                                                    currentOnRectEdgeMoved(edge, pos)
                                                } else if (isInside) {
                                                    val dxNorm = delta.x / rect.width
                                                    val dyNorm = delta.y / rect.height
                                                    currentOnRectTranslate(dxNorm, dyNorm)
                                                }
                                            }
                                        } finally {
                                            activeCornerIndex = -1
                                            activeEdgeIndex = -1
                                        }
                                    }
                                    CropStyle.PERSPECTIVE -> {
                                        val pts = currentPerspectiveCorners
                                        val corner = findNearestPerspectiveCorner(downPos, pts, rect, cornerSlopPx)
                                        val edge = if (corner < 0) findNearestPerspectiveEdge(downPos, pts, rect, edgeSlopPx) else -1
                                        val isInside = if (corner < 0 && edge < 0) isPointInPerspectivePolygon(downPos, pts, rect) else false

                                        if (corner < 0 && edge < 0 && !isInside) continue

                                        down.consume()
                                        activeCornerIndex = corner
                                        activeEdgeIndex = edge

                                        val initialAnchorOffset = if (corner in pts.indices) {
                                            val cp = pts[corner]
                                            val pixelCp = Offset(rect.left + cp.x * rect.width, rect.top + cp.y * rect.height)
                                            downPos - pixelCp
                                        } else Offset.Zero

                                        var lastPos = downPos

                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (!change.pressed) break

                                                val curPos = change.position
                                                val delta = curPos - lastPos
                                                lastPos = curPos
                                                change.consume()

                                                val dxNorm = delta.x / rect.width
                                                val dyNorm = delta.y / rect.height

                                                if (corner >= 0) {
                                                    val targetPixelPos = curPos - initialAnchorOffset
                                                    val rel = displayRectInverse(targetPixelPos, rect)
                                                    currentOnPerspectiveCornerMoved(corner, rel)
                                                } else if (edge >= 0) {
                                                    currentOnPerspectiveEdgeMoved(edge, dxNorm, dyNorm)
                                                } else if (isInside) {
                                                    currentOnPerspectiveTranslate(dxNorm, dyNorm)
                                                }
                                            }
                                        } finally {
                                            activeCornerIndex = -1
                                            activeEdgeIndex = -1
                                        }
                                    }
                                    CropStyle.FREEFORM -> {
                                        down.consume()
                                        val pts = mutableListOf(displayRectInverse(downPos, rect))
                                        localFreeformPoints = pts.toList()
                                        var lastPxPos = downPos

                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (!change.pressed) break
                                                change.consume()
                                                val curPos = change.position
                                                if (hypot(curPos.x - lastPxPos.x, curPos.y - lastPxPos.y) >= 6f) {
                                                    lastPxPos = curPos
                                                    pts.add(displayRectInverse(curPos, rect))
                                                    localFreeformPoints = pts.toList()
                                                }
                                            }
                                        } finally {
                                            if (pts.size >= 3) {
                                                currentOnFreeformChanged(pts.toList())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            drawScaledImage(imageBitmap, bitmap.width, bitmap.height, displayRect)

            if (isCropMode) {
                when (cropStyle) {
                    CropStyle.RECTANGLE -> drawRectCropOverlay(
                        crop = cropRect,
                        rect = displayRect,
                        handleRadius = handleRadiusPx,
                        activeCorner = activeCornerIndex,
                        activeEdge = activeEdgeIndex
                    )
                    CropStyle.PERSPECTIVE -> drawPerspectiveOverlay(
                        corners = perspectiveCorners,
                        rect = displayRect,
                        handleRadius = handleRadiusPx,
                        activeCorner = activeCornerIndex,
                        activeEdge = activeEdgeIndex
                    )
                    CropStyle.FREEFORM -> drawFreeformOverlay(
                        points = localFreeformPoints,
                        rect = displayRect
                    )
                }
            }

            strokes.forEach { pts ->
                if (pts.size < 2) return@forEach
                val p = Path()
                p.moveTo(displayRect.left + pts.first().first * displayRect.width, displayRect.top + pts.first().second * displayRect.height)
                pts.drop(1).forEach { (x, y) ->
                    p.lineTo(displayRect.left + x * displayRect.width, displayRect.top + y * displayRect.height)
                }
                drawPath(p, color = Color.Red, style = Stroke(width = 6f))
            }

            if (isRedactMode) {
                redactRects.forEach { rect ->
                    drawRedactRect(rect, displayRect, redactStyle, active = false)
                }
                localRedactRect?.let { rect ->
                    drawRedactRect(rect, displayRect, redactStyle, active = true)
                }
            }
        }

        if (isCropMode && cropStyle == CropStyle.FREEFORM && localFreeformPoints.isEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    "手指在画面上滑动绘制任意封闭形状",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private fun DrawScope.drawRedactRect(
    rect: NormalizedCropRect,
    display: DisplayRect,
    style: RedactStyle,
    active: Boolean
) {
    val left = display.left + rect.left * display.width
    val top = display.top + rect.top * display.height
    val w = rect.width * display.width
    val h = rect.height * display.height
    if (w <= 0f || h <= 0f) return
    val fill = if (style == RedactStyle.BLACK) {
        Color.Black.copy(alpha = 0.78f)
    } else {
        Color(0xFF9C27B0).copy(alpha = 0.35f)
    }
    drawRect(color = fill, topLeft = Offset(left, top), size = Size(w, h))
    drawRect(
        color = Color.White.copy(alpha = if (active) 0.95f else 0.7f),
        topLeft = Offset(left, top),
        size = Size(w, h),
        style = Stroke(width = if (active) 3f else 2f)
    )
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

private fun getRectCornerOffset(index: Int, crop: NormalizedCropRect, rect: DisplayRect): Offset {
    val l = rect.left + crop.left * rect.width
    val t = rect.top + crop.top * rect.height
    val r = rect.left + crop.right * rect.width
    val b = rect.top + crop.bottom * rect.height
    return when (index) {
        0 -> Offset(l, t)
        1 -> Offset(r, t)
        2 -> Offset(r, b)
        3 -> Offset(l, b)
        else -> Offset(l, t)
    }
}

private fun findNearestRectHandle(
    p: Offset,
    crop: NormalizedCropRect,
    rect: DisplayRect,
    slop: Float
): Int {
    val corners = listOf(
        getRectCornerOffset(0, crop, rect),
        getRectCornerOffset(1, crop, rect),
        getRectCornerOffset(2, crop, rect),
        getRectCornerOffset(3, crop, rect)
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

private fun findNearestRectEdge(
    p: Offset,
    crop: NormalizedCropRect,
    rect: DisplayRect,
    slop: Float
): Int {
    val l = rect.left + crop.left * rect.width
    val t = rect.top + crop.top * rect.height
    val r = rect.left + crop.right * rect.width
    val b = rect.top + crop.bottom * rect.height

    val distTop = if (p.x in (l - slop)..(r + slop)) kotlin.math.abs(p.y - t) else Float.MAX_VALUE
    val distRight = if (p.y in (t - slop)..(b + slop)) kotlin.math.abs(p.x - r) else Float.MAX_VALUE
    val distBottom = if (p.x in (l - slop)..(r + slop)) kotlin.math.abs(p.y - b) else Float.MAX_VALUE
    val distLeft = if (p.y in (t - slop)..(b + slop)) kotlin.math.abs(p.x - l) else Float.MAX_VALUE

    val dists = listOf(distTop, distRight, distBottom, distLeft)
    var bestEdge = -1
    var minD = Float.MAX_VALUE
    dists.forEachIndexed { i, d ->
        if (d < minD) {
            minD = d
            bestEdge = i
        }
    }
    return if (minD <= slop) bestEdge else -1
}

private fun isInsideCropRect(p: Offset, crop: NormalizedCropRect, rect: DisplayRect): Boolean {
    val l = rect.left + crop.left * rect.width
    val t = rect.top + crop.top * rect.height
    val r = rect.left + crop.right * rect.width
    val b = rect.top + crop.bottom * rect.height
    return p.x in l..r && p.y in t..b
}

private fun findNearestPerspectiveCorner(
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

private fun findNearestPerspectiveEdge(
    p: Offset,
    corners: List<PointF>,
    rect: DisplayRect,
    slop: Float
): Int {
    if (corners.size != 4) return -1
    var bestEdge = -1
    var bestD = Float.MAX_VALUE
    for (i in 0..3) {
        val c1 = corners[i]
        val c2 = corners[(i + 1) % 4]
        val p1 = Offset(rect.left + c1.x * rect.width, rect.top + c1.y * rect.height)
        val p2 = Offset(rect.left + c2.x * rect.width, rect.top + c2.y * rect.height)
        val d = distanceToSegment(p, p1, p2)
        if (d < bestD) {
            bestD = d
            bestEdge = i
        }
    }
    return if (bestD <= slop) bestEdge else -1
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val l2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
    if (l2 < 0.0001f) return hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2).coerceIn(0f, 1f)
    val proj = Offset(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
    return hypot(p.x - proj.x, p.y - proj.y)
}

private fun isPointInPerspectivePolygon(p: Offset, corners: List<PointF>, rect: DisplayRect): Boolean {
    if (corners.size < 3) return false
    val pts = corners.map { Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }
    var inside = false
    var j = pts.size - 1
    for (i in pts.indices) {
        val pi = pts[i]
        val pj = pts[j]
        if ((pi.y > p.y) != (pj.y > p.y) &&
            p.x < (pj.x - pi.x) * (p.y - pi.y) / (pj.y - pi.y) + pi.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

private fun DrawScope.drawScaledImage(
    image: androidx.compose.ui.graphics.ImageBitmap?,
    sourceWidth: Int,
    sourceHeight: Int,
    rect: DisplayRect
) {
    if (image == null || sourceWidth <= 0 || sourceHeight <= 0) return
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
    handleRadius: Float,
    activeCorner: Int = -1,
    activeEdge: Int = -1
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

    drawRect(Color(0x66000000), topLeft = Offset(rect.left, rect.top), size = androidx.compose.ui.geometry.Size(rect.width, (t - rect.top).coerceAtLeast(0f)))
    drawRect(Color(0x66000000), topLeft = Offset(rect.left, b), size = androidx.compose.ui.geometry.Size(rect.width, (rect.top + rect.height - b).coerceAtLeast(0f)))
    drawRect(Color(0x66000000), topLeft = Offset(rect.left, t), size = androidx.compose.ui.geometry.Size((l - rect.left).coerceAtLeast(0f), (b - t).coerceAtLeast(0f)))
    drawRect(Color(0x66000000), topLeft = Offset(r, t), size = androidx.compose.ui.geometry.Size((rect.left + rect.width - r).coerceAtLeast(0f), (b - t).coerceAtLeast(0f)))

    val w = r - l
    val h = b - t
    val gridColor = Color.White.copy(alpha = 0.25f)
    if (w > 20f && h > 20f) {
        drawLine(gridColor, Offset(l + w / 3f, t), Offset(l + w / 3f, b), strokeWidth = 1.5f)
        drawLine(gridColor, Offset(l + w * 2f / 3f, t), Offset(l + w * 2f / 3f, b), strokeWidth = 1.5f)
        drawLine(gridColor, Offset(l, t + h / 3f), Offset(r, t + h / 3f), strokeWidth = 1.5f)
        drawLine(gridColor, Offset(l, t + h * 2f / 3f), Offset(r, t + h * 2f / 3f), strokeWidth = 1.5f)
    }

    drawPath(path, color = Color.White, style = Stroke(width = 3.5f))

    val midTop = Offset((l + r) / 2f, t)
    val midRight = Offset(r, (t + b) / 2f)
    val midBottom = Offset((l + r) / 2f, b)
    val midLeft = Offset(l, (t + b) / 2f)

    drawEdgeBar(midTop, isHorizontal = true, active = activeEdge == 0)
    drawEdgeBar(midRight, isHorizontal = false, active = activeEdge == 1)
    drawEdgeBar(midBottom, isHorizontal = true, active = activeEdge == 2)
    drawEdgeBar(midLeft, isHorizontal = false, active = activeEdge == 3)

    val corners = listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(l, b))
    corners.forEachIndexed { i, p ->
        val isActive = activeCorner == i
        val curR = if (isActive) handleRadius * 1.25f else handleRadius
        drawCircle(Color.White, radius = curR, center = p)
        drawCircle(if (isActive) Color(0xFF1E88E5) else Color(0xFF2D6CDF), radius = (curR - 3.5f).coerceAtLeast(2f), center = p)
        drawCircle(Color.White, radius = 3.5f, center = p)
    }
}

private fun DrawScope.drawEdgeBar(center: Offset, isHorizontal: Boolean, active: Boolean) {
    val barColor = if (active) Color(0xFF64B5F6) else Color.White
    val length = 24f
    val thickness = 4.5f
    val halfL = length / 2f
    val halfT = thickness / 2f

    val topLeft = if (isHorizontal) {
        Offset(center.x - halfL, center.y - halfT)
    } else {
        Offset(center.x - halfT, center.y - halfL)
    }
    val size = if (isHorizontal) {
        androidx.compose.ui.geometry.Size(length, thickness)
    } else {
        androidx.compose.ui.geometry.Size(thickness, length)
    }

    drawRoundRect(
        color = barColor,
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(halfT, halfT)
    )
}

private fun DrawScope.drawPerspectiveOverlay(
    corners: List<PointF>,
    rect: DisplayRect,
    handleRadius: Float,
    activeCorner: Int = -1,
    activeEdge: Int = -1
) {
    if (corners.size < 4) return
    val pixelCorners = corners.map {
        Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height)
    }

    val polyPath = Path()
    pixelCorners.forEachIndexed { i, p ->
        if (i == 0) polyPath.moveTo(p.x, p.y) else polyPath.lineTo(p.x, p.y)
    }
    polyPath.close()

    runCatching {
        clipPath(polyPath, clipOp = ClipOp.Difference) {
            drawRect(
                Color(0x66000000),
                topLeft = Offset(rect.left, rect.top),
                size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
            )
        }
    }

    drawPath(polyPath, color = Color(0x332D6CDF))
    drawPerspectiveGrid(pixelCorners)
    drawPath(polyPath, color = Color.White, style = Stroke(width = 3.5f))

    if (activeEdge in 0..3) {
        val p1 = pixelCorners[activeEdge]
        val p2 = pixelCorners[(activeEdge + 1) % 4]
        drawLine(
            color = Color(0xFF64B5F6),
            start = p1,
            end = p2,
            strokeWidth = 5.5f
        )
    }

    for (i in 0..3) {
        val p1 = pixelCorners[i]
        val p2 = pixelCorners[(i + 1) % 4]
        val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
        val isEdgeActive = activeEdge == i
        val midR = if (isEdgeActive) 7f else 5.5f
        drawCircle(Color.White, radius = midR + 2f, center = mid)
        drawCircle(if (isEdgeActive) Color(0xFF64B5F6) else Color(0xFF2D6CDF), radius = midR, center = mid)
    }

    pixelCorners.forEachIndexed { i, p ->
        val isActive = activeCorner == i
        val curRadius = if (isActive) handleRadius * 1.25f else handleRadius
        drawCircle(Color.White, radius = curRadius, center = p)
        drawCircle(
            color = if (isActive) Color(0xFF1E88E5) else Color(0xFF2D6CDF),
            radius = (curRadius - 3.5f).coerceAtLeast(2f),
            center = p
        )
        drawCircle(Color.White, radius = 3.5f, center = p)
    }
}

private fun DrawScope.drawPerspectiveGrid(pixelCorners: List<Offset>) {
    if (pixelCorners.size != 4) return
    val tl = pixelCorners[0]
    val tr = pixelCorners[1]
    val br = pixelCorners[2]
    val bl = pixelCorners[3]
    val gridColor = Color.White.copy(alpha = 0.28f)
    val stroke = Stroke(width = 1.5f)

    for (i in 1..2) {
        val fraction = i / 3f
        val topP = lerpOffset(tl, tr, fraction)
        val botP = lerpOffset(bl, br, fraction)
        drawLine(gridColor, topP, botP, strokeWidth = stroke.width)

        val leftP = lerpOffset(tl, bl, fraction)
        val rightP = lerpOffset(tr, br, fraction)
        drawLine(gridColor, leftP, rightP, strokeWidth = stroke.width)
    }
}

private fun lerpOffset(start: Offset, end: Offset, fraction: Float): Offset {
    return Offset(
        start.x + (end.x - start.x) * fraction,
        start.y + (end.y - start.y) * fraction
    )
}

private fun DrawScope.drawFreeformOverlay(
    points: List<PointF>,
    rect: DisplayRect
) {
    if (points.size < 2) return
    val pixelPts = points.map { Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }
    val path = Path().apply {
        moveTo(pixelPts.first().x, pixelPts.first().y)
        for (i in 1 until pixelPts.size) {
            lineTo(pixelPts[i].x, pixelPts[i].y)
        }
        close()
    }

    if (points.size >= 3) {
        runCatching {
            clipPath(path, clipOp = ClipOp.Difference) {
                drawRect(
                    Color(0x66000000),
                    topLeft = Offset(rect.left, rect.top),
                    size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
                )
            }
        }
        drawPath(path, color = Color(0x3300E5FF))
    }

    drawPath(path, color = Color(0xFF00E5FF), style = Stroke(width = 3.5f))

    val startPt = pixelPts.first()
    drawCircle(Color.White, radius = 7f, center = startPt)
    drawCircle(Color(0xFF00E676), radius = 5f, center = startPt)
}
