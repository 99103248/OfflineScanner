package com.scanner.offline.ui.screen.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.data.storage.ExportResult
import com.scanner.offline.domain.model.CropAspect
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.WatermarkPosition
import com.scanner.offline.domain.usecase.SaveEditedImageUseCase
import com.scanner.offline.domain.usecase.UpdatePageImageUseCase
import com.scanner.offline.engine.image.ImageTransform
import com.scanner.offline.engine.image.NormalizedCropRect
import com.scanner.offline.util.BitmapUtils
import com.scanner.offline.util.Time
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

import android.graphics.PointF
import com.scanner.offline.domain.model.CropStyle
import com.scanner.offline.domain.model.RedactStyle
import com.scanner.offline.engine.image.EdgeDetector

enum class ImageEditMode { CROP, FLIP, MARK, REDACT }

@HiltViewModel
class ImageEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transform: ImageTransform,
    private val edgeDetector: EdgeDetector,
    private val saveEdited: SaveEditedImageUseCase,
    private val updatePage: UpdatePageImageUseCase
) : ViewModel() {

    private var original: Bitmap? = null
    private var pageId: Long? = null

    private val _state = MutableStateFlow(ImageEditUiState())
    val state: StateFlow<ImageEditUiState> = _state.asStateFlow()

    fun setMode(mode: ImageEditMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun bindPage(id: Long?) {
        pageId = id?.takeIf { it > 0 }
    }

    fun load(imageUri: String) {
        if (imageUri.isBlank()) {
            _state.value = ImageEditUiState(error = "请先选择图片")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, lastResult = null) }
            val bmp = withContext(Dispatchers.IO) { decode(imageUri) }
            if (bmp == null) {
                _state.update { it.copy(loading = false, error = "无法读取图片") }
                return@launch
            }
            original = bmp
            val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
            val workingBmp = runCatching { bmp.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: bmp
            val corners = withContext(Dispatchers.Default) { edgeDetector.detect(workingBmp) }
            _state.update {
                it.copy(
                    loading = false,
                    working = workingBmp,
                    imageAspect = aspect,
                    cropRect = NormalizedCropRect.inset(),
                    cropStyle = CropStyle.RECTANGLE,
                    perspectiveCorners = corners,
                    freeformPoints = emptyList(),
                    dirty = false,
                    lastResult = null,
                    strokes = emptyList()
                )
            }
        }
    }

    fun setCropStyle(style: CropStyle) {
        val cur = _state.value
        if (style == CropStyle.PERSPECTIVE && cur.perspectiveCorners.size != 4) {
            val bmp = cur.working
            val corners = if (bmp != null) edgeDetector.detect(bmp) else defaultCorners()
            _state.update { it.copy(cropStyle = style, perspectiveCorners = corners) }
        } else {
            _state.update { it.copy(cropStyle = style) }
        }
    }

    fun detectPerspectiveCorners() {
        val bmp = _state.value.working ?: return
        viewModelScope.launch {
            val corners = withContext(Dispatchers.Default) { edgeDetector.detect(bmp) }
            _state.update { it.copy(perspectiveCorners = corners) }
        }
    }

    fun resetPerspectiveToFull() {
        _state.update {
            it.copy(
                perspectiveCorners = listOf(
                    PointF(0f, 0f),
                    PointF(1f, 0f),
                    PointF(1f, 1f),
                    PointF(0f, 1f)
                )
            )
        }
    }

    fun updatePerspectiveCorner(index: Int, point: PointF) {
        val cur = _state.value
        if (index !in cur.perspectiveCorners.indices) return
        val newPts = cur.perspectiveCorners.toMutableList()
        newPts[index] = PointF(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        _state.update { it.copy(perspectiveCorners = newPts) }
    }

    fun movePerspectiveEdge(edgeIndex: Int, dx: Float, dy: Float) {
        val cur = _state.value
        if (cur.perspectiveCorners.size != 4) return
        val i1 = edgeIndex
        val i2 = (edgeIndex + 1) % 4
        val p1 = cur.perspectiveCorners[i1]
        val p2 = cur.perspectiveCorners[i2]
        val newPts = cur.perspectiveCorners.toMutableList()
        newPts[i1] = PointF((p1.x + dx).coerceIn(0f, 1f), (p1.y + dy).coerceIn(0f, 1f))
        newPts[i2] = PointF((p2.x + dx).coerceIn(0f, 1f), (p2.y + dy).coerceIn(0f, 1f))
        _state.update { it.copy(perspectiveCorners = newPts) }
    }

    fun translatePerspective(dx: Float, dy: Float) {
        val cur = _state.value
        if (cur.perspectiveCorners.size != 4) return
        val minX = cur.perspectiveCorners.minOf { it.x }
        val maxX = cur.perspectiveCorners.maxOf { it.x }
        val minY = cur.perspectiveCorners.minOf { it.y }
        val maxY = cur.perspectiveCorners.maxOf { it.y }
        val clampedDx = dx.coerceIn(-minX, 1f - maxX)
        val clampedDy = dy.coerceIn(-minY, 1f - maxY)
        val newPts = cur.perspectiveCorners.map {
            PointF(it.x + clampedDx, it.y + clampedDy)
        }
        _state.update { it.copy(perspectiveCorners = newPts) }
    }

    fun setFreeformPoints(points: List<PointF>) {
        _state.update { it.copy(freeformPoints = points) }
    }

    fun clearFreeformPoints() {
        _state.update { it.copy(freeformPoints = emptyList()) }
    }

    fun setCropAspect(aspect: CropAspect) {
        val cur = _state.value
        val rect = NormalizedCropRect.centered(aspect, cur.imageAspect)
        _state.update { it.copy(cropAspect = aspect, cropRect = rect) }
    }

    fun updateCrop(rect: NormalizedCropRect) {
        val cur = _state.value
        _state.update {
            it.copy(
                cropRect = rect.moveCorner(
                    index = 0,
                    x = rect.left,
                    y = rect.top,
                    imageAspect = cur.imageAspect,
                    cropAspect = CropAspect.FREE
                ).let { moved ->
                    if (cur.cropAspect == CropAspect.FREE) rect.coerced()
                    else rect.withAspect(cur.cropAspect, cur.imageAspect)
                }
            )
        }
    }

    fun moveCropCorner(index: Int, x: Float, y: Float) {
        val cur = _state.value
        _state.update {
            it.copy(
                cropRect = cur.cropRect.moveCorner(index, x, y, cur.imageAspect, cur.cropAspect)
            )
        }
    }

    fun moveCropEdge(edgeIndex: Int, pos: Float) {
        val cur = _state.value
        _state.update {
            it.copy(
                cropRect = cur.cropRect.moveEdge(edgeIndex, pos, cur.imageAspect, cur.cropAspect)
            )
        }
    }

    fun translateCrop(deltaX: Float, deltaY: Float) {
        _state.update {
            it.copy(cropRect = it.cropRect.translate(deltaX, deltaY))
        }
    }

    fun applyCrop() {
        val cur = _state.value
        val src = cur.working ?: return
        viewModelScope.launch {
            val cropped = withContext(Dispatchers.Default) {
                when (cur.cropStyle) {
                    CropStyle.RECTANGLE -> {
                        if (cur.cropRect.isFull) src else transform.crop(src, cur.cropRect)
                    }
                    CropStyle.PERSPECTIVE -> {
                        if (cur.perspectiveCorners.size == 4) {
                            transform.perspectiveCrop(src, cur.perspectiveCorners)
                        } else src
                    }
                    CropStyle.FREEFORM -> {
                        if (cur.freeformPoints.size >= 3) {
                            val transparent = cur.format == ExportFormat.PNG
                            transform.freeformCrop(src, cur.freeformPoints, transparent)
                        } else src
                    }
                }
            }
            if (cropped === src) return@launch
            val aspect = cropped.width.toFloat() / cropped.height.coerceAtLeast(1)
            val newCorners = withContext(Dispatchers.Default) { edgeDetector.detect(cropped) }
            _state.update {
                it.copy(
                    working = cropped,
                    imageAspect = aspect,
                    cropRect = NormalizedCropRect.full(),
                    perspectiveCorners = newCorners,
                    freeformPoints = emptyList(),
                    redactRects = emptyList(),
                    strokes = emptyList(),
                    dirty = true
                )
            }
        }
    }

    fun flipHorizontal() = mutate { transform.flipHorizontal(it) }
    fun flipVertical() = mutate { transform.flipVertical(it) }
    fun rotate90() = mutate { transform.rotate90Clockwise(it) }
    fun rotate90Ccw() = mutate { transform.rotate90CounterClockwise(it) }

    fun setFreeAngle(deg: Float) {
        _state.update { it.copy(freeAngle = deg) }
    }

    fun applyFreeAngle() {
        val deg = _state.value.freeAngle
        if (kotlin.math.abs(deg) < 0.2f) return
        mutate { src ->
            val fill = if (_state.value.format == ExportFormat.PNG) Color.TRANSPARENT else Color.WHITE
            transform.rotateDegrees(src, deg, fill)
        }
        _state.update { it.copy(freeAngle = 0f) }
    }

    fun setFormat(format: ExportFormat) {
        _state.update { it.copy(format = format) }
    }

    fun setQuality(q: Int) {
        _state.update { it.copy(quality = q.coerceIn(40, 100)) }
    }

    fun setWatermark(text: String) {
        _state.update { it.copy(watermark = text) }
    }

    fun setWatermarkPos(pos: WatermarkPosition) {
        _state.update { it.copy(watermarkPos = pos) }
    }

    fun applyWatermark() {
        val text = _state.value.watermark
        if (text.isBlank()) return
        val pos = _state.value.watermarkPos
        mutate { transform.applyWatermark(it, text, pos) }
    }

    fun addStrokePoint(x: Float, y: Float, newStroke: Boolean) {
        _state.update { cur ->
            val strokes = cur.strokes.toMutableList()
            if (newStroke || strokes.isEmpty()) {
                strokes += listOf(x to y)
            } else {
                val last = strokes.last().toMutableList()
                last += x to y
                strokes[strokes.lastIndex] = last
            }
            cur.copy(strokes = strokes)
        }
    }

    fun applyStrokes() {
        val strokes = _state.value.strokes
        if (strokes.isEmpty()) return
        mutate { transform.bakeStrokes(it, strokes) }
        _state.update { it.copy(strokes = emptyList()) }
    }

    fun setRedactStyle(style: RedactStyle) {
        _state.update { it.copy(redactStyle = style) }
    }

    fun addRedactRect(rect: NormalizedCropRect) {
        if (rect.width < 0.01f || rect.height < 0.01f) return
        _state.update { it.copy(redactRects = it.redactRects + rect.coerced(0.02f)) }
    }

    fun undoRedactRect() {
        _state.update {
            if (it.redactRects.isEmpty()) it else it.copy(redactRects = it.redactRects.dropLast(1))
        }
    }

    fun clearRedactRects() {
        _state.update { it.copy(redactRects = emptyList()) }
    }

    fun applyRedaction() {
        val cur = _state.value
        if (cur.redactRects.isEmpty()) return
        mutate { transform.redact(it, cur.redactRects, cur.redactStyle) }
        _state.update { it.copy(redactRects = emptyList()) }
    }

    fun reset() {
        val src = original ?: return
        val aspect = src.width.toFloat() / src.height.coerceAtLeast(1)
        val workingBmp = runCatching { src.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: src
        viewModelScope.launch {
            val corners = withContext(Dispatchers.Default) { edgeDetector.detect(workingBmp) }
            _state.update {
                it.copy(
                    working = workingBmp,
                    imageAspect = aspect,
                    cropRect = NormalizedCropRect.inset(),
                    cropStyle = CropStyle.RECTANGLE,
                    perspectiveCorners = corners,
                    freeformPoints = emptyList(),
                    dirty = false,
                    lastResult = null,
                    error = null,
                    strokes = emptyList(),
                    redactRects = emptyList(),
                    freeAngle = 0f
                )
            }
        }
    }

    fun save() {
        val cur = _state.value
        var bmp = cur.working ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                withContext(Dispatchers.Default) {
                    if (cur.mode == ImageEditMode.CROP) {
                        when (cur.cropStyle) {
                            CropStyle.RECTANGLE -> {
                                if (!cur.cropRect.isFull) {
                                    bmp = transform.crop(bmp, cur.cropRect)
                                }
                            }
                            CropStyle.PERSPECTIVE -> {
                                if (cur.perspectiveCorners.size == 4) {
                                    bmp = transform.perspectiveCrop(bmp, cur.perspectiveCorners)
                                }
                            }
                            CropStyle.FREEFORM -> {
                                if (cur.freeformPoints.size >= 3) {
                                    val transparent = cur.format == ExportFormat.PNG
                                    bmp = transform.freeformCrop(bmp, cur.freeformPoints, transparent)
                                }
                            }
                        }
                    }
                    if (cur.strokes.isNotEmpty()) {
                        bmp = transform.bakeStrokes(bmp, cur.strokes)
                    }
                    if (cur.redactRects.isNotEmpty()) {
                        bmp = transform.redact(bmp, cur.redactRects, cur.redactStyle)
                    }
                    val pid = pageId
                    if (pid != null) {
                        updatePage(pid, bmp)
                    }
                    saveEdited(bmp, cur.format, Time.nowDocName("edited"), cur.quality)
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        saving = false,
                        working = bmp,
                        cropRect = NormalizedCropRect.full(),
                        freeformPoints = emptyList(),
                        dirty = true,
                        lastResult = result,
                        strokes = emptyList(),
                        redactRects = emptyList()
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
            }
        }
    }

    private fun defaultCorners(): List<PointF> {
        val inset = 0.05f
        return listOf(
            PointF(inset, inset),
            PointF(1f - inset, inset),
            PointF(1f - inset, 1f - inset),
            PointF(inset, 1f - inset)
        )
    }

    private fun mutate(block: (Bitmap) -> Bitmap) {
        val src = _state.value.working ?: return
        viewModelScope.launch {
            val out = withContext(Dispatchers.Default) { block(src) }
            val aspect = out.width.toFloat() / out.height.coerceAtLeast(1)
            _state.update {
                it.copy(
                    working = out,
                    dirty = true,
                    imageAspect = aspect,
                    // 图像几何变化后，旧的打码框/批注坐标已失效，清空避免误用
                    redactRects = emptyList(),
                    strokes = emptyList()
                )
            }
        }
    }

    private fun decode(imageUri: String): Bitmap? {
        File(imageUri).takeIf { it.exists() }?.let { return BitmapUtils.decode(it) }
        val uri = Uri.parse(imageUri)
        return if (uri.scheme == "file" || uri.scheme == null) {
            BitmapUtils.decode(File(uri.path ?: imageUri))
        } else {
            BitmapUtils.decode(context.contentResolver, uri)
        }
    }
}

data class ImageEditUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val working: Bitmap? = null,
    val imageAspect: Float = 1f,
    val cropRect: NormalizedCropRect = NormalizedCropRect.inset(),
    val cropAspect: CropAspect = CropAspect.FREE,
    val cropStyle: CropStyle = CropStyle.RECTANGLE,
    val perspectiveCorners: List<PointF> = emptyList(),
    val freeformPoints: List<PointF> = emptyList(),
    val format: ExportFormat = ExportFormat.JPG,
    val quality: Int = 92,
    val mode: ImageEditMode = ImageEditMode.CROP,
    val freeAngle: Float = 0f,
    val watermark: String = "",
    val watermarkPos: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val strokes: List<List<Pair<Float, Float>>> = emptyList(),
    val redactRects: List<NormalizedCropRect> = emptyList(),
    val redactStyle: RedactStyle = RedactStyle.BLACK,
    val dirty: Boolean = false,
    val lastResult: ExportResult? = null,
    val error: String? = null
)
