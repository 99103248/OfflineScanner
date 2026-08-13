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

enum class ImageEditMode { CROP, FLIP, MARK }

@HiltViewModel
class ImageEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transform: ImageTransform,
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
            recycleWorkingIfNeeded(keepOriginal = false)
            original = bmp
            val aspect = bmp.width.toFloat() / bmp.height
            _state.update {
                it.copy(
                    loading = false,
                    working = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false),
                    imageAspect = aspect,
                    cropRect = NormalizedCropRect.inset(),
                    dirty = false,
                    lastResult = null,
                    strokes = emptyList()
                )
            }
        }
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

    fun applyCrop() {
        val cur = _state.value
        val src = cur.working ?: return
        if (cur.cropRect.isFull) return
        viewModelScope.launch {
            val cropped = withContext(Dispatchers.Default) { transform.crop(src, cur.cropRect) }
            if (src !== original && src !== cropped) src.recycle()
            val aspect = cropped.width.toFloat() / cropped.height
            _state.update {
                it.copy(
                    working = cropped,
                    imageAspect = aspect,
                    cropRect = NormalizedCropRect.full(),
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

    fun reset() {
        val src = original ?: return
        val cur = _state.value.working
        if (cur != null && cur !== src && cur !== original) cur.recycle()
        val aspect = src.width.toFloat() / src.height
        _state.update {
            it.copy(
                working = src.copy(src.config ?: Bitmap.Config.ARGB_8888, false),
                imageAspect = aspect,
                cropRect = NormalizedCropRect.inset(),
                dirty = false,
                lastResult = null,
                error = null,
                strokes = emptyList(),
                freeAngle = 0f
            )
        }
    }

    fun save() {
        val cur = _state.value
        var bmp = cur.working ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                withContext(Dispatchers.Default) {
                    if (cur.mode == ImageEditMode.CROP && !cur.cropRect.isFull) {
                        val cropped = transform.crop(bmp, cur.cropRect)
                        if (bmp !== original && bmp !== cropped) bmp.recycle()
                        bmp = cropped
                    }
                    if (cur.strokes.isNotEmpty()) {
                        val baked = transform.bakeStrokes(bmp, cur.strokes)
                        if (bmp !== original && bmp !== baked) bmp.recycle()
                        bmp = baked
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
                        dirty = true,
                        lastResult = result,
                        strokes = emptyList()
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
            }
        }
    }

    override fun onCleared() {
        recycleWorkingIfNeeded(keepOriginal = false)
        super.onCleared()
    }

    private fun mutate(block: (Bitmap) -> Bitmap) {
        val src = _state.value.working ?: return
        viewModelScope.launch {
            val out = withContext(Dispatchers.Default) { block(src) }
            if (src !== original && src !== out) src.recycle()
            val aspect = out.width.toFloat() / out.height.coerceAtLeast(1)
            _state.update { it.copy(working = out, dirty = true, imageAspect = aspect) }
        }
    }

    private fun recycleWorkingIfNeeded(keepOriginal: Boolean) {
        val working = _state.value.working
        if (working != null && working !== original) working.recycle()
        if (!keepOriginal) {
            original?.recycle()
            original = null
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
    val format: ExportFormat = ExportFormat.JPG,
    val quality: Int = 92,
    val mode: ImageEditMode = ImageEditMode.CROP,
    val freeAngle: Float = 0f,
    val watermark: String = "",
    val watermarkPos: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val strokes: List<List<Pair<Float, Float>>> = emptyList(),
    val dirty: Boolean = false,
    val lastResult: ExportResult? = null,
    val error: String? = null
)
