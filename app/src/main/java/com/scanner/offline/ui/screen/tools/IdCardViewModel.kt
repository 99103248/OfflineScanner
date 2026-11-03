package com.scanner.offline.ui.screen.tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.FilterMode
import com.scanner.offline.domain.usecase.SaveScannedPageUseCase
import com.scanner.offline.engine.image.IdCardComposer
import com.scanner.offline.util.BitmapUtils
import com.scanner.offline.util.Time
import com.scanner.offline.util.UriUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class IdCardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val composer: IdCardComposer,
    private val storage: StorageManager,
    private val savePage: SaveScannedPageUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(IdCardUiState())
    val state: StateFlow<IdCardUiState> = _state.asStateFlow()

    private var composedFull: Bitmap? = null
    private var previewJob: Job? = null

    fun setFront(uriString: String) = setImage(uriString, front = true)
    fun setBack(uriString: String) = setImage(uriString, front = false)

    fun swap() {
        _state.update { it.copy(frontPath = it.backPath, backPath = it.frontPath) }
        rebuildPreview()
    }

    fun save() {
        val full = composedFull
        if (full == null) {
            _state.update { it.copy(error = "请先选择正反面图片") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val tmp = storage.newCacheImage("idcard")
                    storage.saveBitmap(full, tmp, quality = 95)
                    savePage(
                        processedBitmapPath = tmp.absolutePath,
                        filterMode = FilterMode.ORIGINAL,
                        docId = null,
                        documentName = Time.nowDocName("证件扫描")
                    )
                }
            }.onSuccess { docId ->
                _state.update {
                    it.copy(busy = false, message = "已保存到文档库", savedDocId = docId)
                }
            }.onFailure { e ->
                _state.update { it.copy(busy = false, error = e.message ?: "保存失败") }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun setImage(raw: String, front: Boolean) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                if (File(raw).exists()) raw
                else UriUtils.copyUriToCache(context, Uri.parse(raw), "idcard")
            }
            if (path == null) {
                _state.update { it.copy(error = "无法读取图片") }
                return@launch
            }
            _state.update {
                if (front) it.copy(frontPath = path) else it.copy(backPath = path)
            }
            rebuildPreview()
        }
    }

    private fun rebuildPreview() {
        val frontPath = _state.value.frontPath
        val backPath = _state.value.backPath
        if (frontPath == null || backPath == null) return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val front = BitmapUtils.decode(File(frontPath), maxSize = 2200)
                        ?: error("无法读取正面图片")
                    val back = BitmapUtils.decode(File(backPath), maxSize = 2200)
                        ?: error("无法读取反面图片")
                    try {
                        val full = composer.compose(front, back)
                        val previewW = 620
                        val previewH = (full.height * previewW / full.width.toFloat()).toInt()
                        val preview = Bitmap.createScaledBitmap(full, previewW, previewH, true)
                        full to preview
                    } finally {
                        front.recycle()
                        back.recycle()
                    }
                }
            }
            result.onSuccess { (full, preview) ->
                composedFull?.recycle()
                composedFull = full
                _state.update { it.copy(busy = false, preview = preview) }
            }.onFailure { e ->
                _state.update { it.copy(busy = false, error = e.message ?: "合成失败") }
            }
        }
    }

    override fun onCleared() {
        previewJob?.cancel()
        composedFull?.recycle()
        super.onCleared()
    }
}

data class IdCardUiState(
    val frontPath: String? = null,
    val backPath: String? = null,
    val preview: Bitmap? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val savedDocId: Long? = null
)
