package com.scanner.offline.ui.screen.edit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.data.storage.ExportResult
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.usecase.SaveEditedImageUseCase
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

enum class ImageEditMode { CROP, FLIP }

@HiltViewModel
class ImageEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transform: ImageTransform,
    private val saveEdited: SaveEditedImageUseCase
) : ViewModel() {

    private var original: Bitmap? = null

    private val _state = MutableStateFlow(ImageEditUiState())
    val state: StateFlow<ImageEditUiState> = _state.asStateFlow()

    fun setMode(mode: ImageEditMode) {
        _state.update { it.copy(mode = mode) }
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
            _state.update {
                it.copy(
                    loading = false,
                    working = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false),
                    cropRect = NormalizedCropRect.inset(),
                    dirty = false,
                    lastResult = null
                )
            }
        }
    }

    fun updateCrop(rect: NormalizedCropRect) {
        _state.update { it.copy(cropRect = rect.coerced()) }
    }

    fun applyCrop() {
        val cur = _state.value
        val src = cur.working ?: return
        if (cur.cropRect.isFull) return
        viewModelScope.launch {
            val cropped = withContext(Dispatchers.Default) { transform.crop(src, cur.cropRect) }
            if (src !== original && src !== cropped) src.recycle()
            _state.update {
                it.copy(
                    working = cropped,
                    cropRect = NormalizedCropRect.full(),
                    dirty = true
                )
            }
        }
    }

    fun flipHorizontal() = mutate { transform.flipHorizontal(it) }

    fun flipVertical() = mutate { transform.flipVertical(it) }

    fun rotate90() = mutate { transform.rotate90Clockwise(it) }

    fun reset() {
        val src = original ?: return
        val cur = _state.value.working
        if (cur != null && cur !== src && cur !== original) cur.recycle()
        _state.update {
            it.copy(
                working = src.copy(src.config ?: Bitmap.Config.ARGB_8888, false),
                cropRect = NormalizedCropRect.inset(),
                dirty = false,
                lastResult = null,
                error = null
            )
        }
    }

    fun setFormat(format: ExportFormat) {
        _state.update { it.copy(format = format) }
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
                    saveEdited(bmp, cur.format, Time.nowDocName("edited"))
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        saving = false,
                        working = bmp,
                        cropRect = NormalizedCropRect.full(),
                        dirty = true,
                        lastResult = result
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
            _state.update { it.copy(working = out, dirty = true) }
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
    val cropRect: NormalizedCropRect = NormalizedCropRect.inset(),
    val format: ExportFormat = ExportFormat.JPG,
    val mode: ImageEditMode = ImageEditMode.CROP,
    val dirty: Boolean = false,
    val lastResult: ExportResult? = null,
    val error: String? = null
)
