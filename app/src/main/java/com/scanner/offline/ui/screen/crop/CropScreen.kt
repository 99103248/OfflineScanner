package com.scanner.offline.ui.screen.crop

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
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
                    onCornerMoved = viewModel::updateCorner,
                    onEdgeMoved = viewModel::moveEdge,
                    onTranslate = viewModel::translateCorners
                )
            }
        }
    }
}

@Composable
private fun CropEditor(
    bitmap: android.graphics.Bitmap,
    corners: List<PointF>,
    onCornerMoved: (Int, PointF) -> Unit,
    onEdgeMoved: (Int, Float, Float) -> Unit,
    onTranslate: (Float, Float) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handleRadiusPx = with(density) { 14.dp.toPx() }
    val cornerSlopPx = with(density) { 42.dp.toPx() }
    val edgeSlopPx = with(density) { 26.dp.toPx() }

    // 使用 rememberUpdatedState 避免手势协程捕获旧数据或被错误重启
    val currentCorners by rememberUpdatedState(corners)
    val currentOnCornerMoved by rememberUpdatedState(onCornerMoved)
    val currentOnEdgeMoved by rememberUpdatedState(onEdgeMoved)
    val currentOnTranslate by rememberUpdatedState(onTranslate)

    // 记录当前拖动状态用于高亮视觉反馈
    var activeCornerIndex by remember { mutableStateOf(-1) }
    var activeEdgeIndex by remember { mutableStateOf(-1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        val displayRect = remember(canvasSize, bitmap.width, bitmap.height) {
            calcDisplayRect(canvasSize, bitmap.width, bitmap.height)
        }
        val currentDisplayRect by rememberUpdatedState(displayRect)
        val imageBitmap = remember(bitmap) {
            if (!bitmap.isRecycled) bitmap.asImageBitmap() else null
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val rect = currentDisplayRect
                            if (rect.width <= 0f || rect.height <= 0f) continue

                            val pts = currentCorners
                            val downPos = down.position

                            // 1. 优先判定角点 (触控半径 42dp)
                            val corner = findNearestCorner(downPos, pts, rect, cornerSlopPx)

                            // 2. 其次判定边线 (判定距离 26dp)
                            val edge = if (corner < 0) {
                                findNearestEdge(downPos, pts, rect, edgeSlopPx)
                            } else -1

                            // 3. 再次判定框内整体平移
                            val isInside = if (corner < 0 && edge < 0) {
                                isPointInPolygon(downPos, pts, rect)
                            } else false

                            // 未命中任何有效操作区域
                            if (corner < 0 && edge < 0 && !isInside) {
                                continue
                            }

                            // 立即消费按下事件，消除触控延迟
                            down.consume()
                            activeCornerIndex = corner
                            activeEdgeIndex = edge

                            // 记录手指与角点的初始偏移量，防止按下瞬间位置发生跳动
                            val initialAnchorOffset = if (corner >= 0 && corner < pts.size) {
                                val cp = pts[corner]
                                val pixelCp = Offset(rect.left + cp.x * rect.width, rect.top + cp.y * rect.height)
                                downPos - pixelCp
                            } else {
                                Offset.Zero
                            }

                            var lastPos = downPos

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break // 手指抬起

                                    val curPos = change.position
                                    val delta = curPos - lastPos
                                    lastPos = curPos
                                    change.consume()

                                    val dxNorm = delta.x / rect.width
                                    val dyNorm = delta.y / rect.height

                                    if (corner >= 0) {
                                        // 考虑初始按压偏移，保持角点与手指的相对距离，杜绝跳动
                                        val targetPixelPos = curPos - initialAnchorOffset
                                        val rel = displayRectInverse(targetPixelPos, rect)
                                        currentOnCornerMoved(corner, rel)
                                    } else if (edge >= 0) {
                                        currentOnEdgeMoved(edge, dxNorm, dyNorm)
                                    } else if (isInside) {
                                        currentOnTranslate(dxNorm, dyNorm)
                                    }
                                }
                            } finally {
                                activeCornerIndex = -1
                                activeEdgeIndex = -1
                            }
                        }
                    }
                }
        ) {
            drawScaledImage(imageBitmap, bitmap.width, bitmap.height, displayRect)
            drawCropOverlay(
                normalizedCorners = corners,
                rect = displayRect,
                handleRadius = handleRadiusPx,
                activeCorner = activeCornerIndex,
                activeEdge = activeEdgeIndex
            )
        }
    }
}

private fun DrawScope.drawScaledImage(
    image: ImageBitmap?,
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

private fun DrawScope.drawCropOverlay(
    normalizedCorners: List<PointF>,
    rect: DisplayRect,
    handleRadius: Float,
    activeCorner: Int = -1,
    activeEdge: Int = -1
) {
    if (normalizedCorners.size < 4) return
    val pixelCorners = normalizedCorners.map {
        Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height)
    }

    val polyPath = Path()
    pixelCorners.forEachIndexed { i, p ->
        if (i == 0) polyPath.moveTo(p.x, p.y) else polyPath.lineTo(p.x, p.y)
    }
    polyPath.close()

    // 选区半透明高亮底色
    drawPath(polyPath, color = Color(0x332D6CDF))

    // 辅助九宫格线（3x3 参考线）
    drawCropGrid(pixelCorners)

    // 主边框线条
    drawPath(polyPath, color = Color.White, style = Stroke(width = 4f))

    // 高亮正在拖动的边
    if (activeEdge in 0..3) {
        val p1 = pixelCorners[activeEdge]
        val p2 = pixelCorners[(activeEdge + 1) % 4]
        drawLine(
            color = Color(0xFF64B5F6),
            start = p1,
            end = p2,
            strokeWidth = 6f
        )
    }

    // 绘制 4 条边的中点指示柄
    for (i in 0..3) {
        val p1 = pixelCorners[i]
        val p2 = pixelCorners[(i + 1) % 4]
        val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
        val isEdgeActive = activeEdge == i
        val midR = if (isEdgeActive) 7f else 5.5f
        drawCircle(Color.White, radius = midR + 2f, center = mid)
        drawCircle(if (isEdgeActive) Color(0xFF64B5F6) else Color(0xFF2D6CDF), radius = midR, center = mid)
    }

    // 绘制 4 个角的手柄
    pixelCorners.forEachIndexed { i, p ->
        val isActive = activeCorner == i
        val curRadius = if (isActive) handleRadius * 1.25f else handleRadius

        // 外层白色发光/边框
        drawCircle(Color.White, radius = curRadius, center = p)
        // 内层主色圆心
        drawCircle(
            color = if (isActive) Color(0xFF1E88E5) else Color(0xFF2D6CDF),
            radius = (curRadius - 3.5f).coerceAtLeast(2f),
            center = p
        )
        // 中心小白点增加精度感
        drawCircle(Color.White, radius = 3.5f, center = p)
    }
}

/** 绘制 3x3 辅助网格 */
private fun DrawScope.drawCropGrid(pixelCorners: List<Offset>) {
    if (pixelCorners.size != 4) return
    val tl = pixelCorners[0]
    val tr = pixelCorners[1]
    val br = pixelCorners[2]
    val bl = pixelCorners[3]
    val gridColor = Color.White.copy(alpha = 0.28f)
    val stroke = Stroke(width = 1.5f)

    for (i in 1..2) {
        val fraction = i / 3f
        // 垂直辅助线
        val topP = lerpOffset(tl, tr, fraction)
        val botP = lerpOffset(bl, br, fraction)
        drawLine(gridColor, topP, botP, strokeWidth = stroke.width)

        // 水平辅助线
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

/** 查找最近的角点（判定半径扩大且自适应） */
private fun findNearestCorner(
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

/** 查找最近的边（到线段的最短距离） */
private fun findNearestEdge(
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

/** 计算点到线段的最短距离 */
private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val l2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
    if (l2 < 0.0001f) return hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2).coerceIn(0f, 1f)
    val proj = Offset(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
    return hypot(p.x - proj.x, p.y - proj.y)
}

/** 射线法判断点是否在多边形内部（用于支持框内平移） */
private fun isPointInPolygon(p: Offset, corners: List<PointF>, rect: DisplayRect): Boolean {
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

