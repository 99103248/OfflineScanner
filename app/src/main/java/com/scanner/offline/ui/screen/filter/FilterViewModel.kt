package com.scanner.offline.ui.screen.filter

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.domain.model.FilterMode
import com.scanner.offline.domain.usecase.SaveScannedPageUseCase
import com.scanner.offline.engine.image.ImageFilter
import com.scanner.offline.util.BitmapUtils
import com.scanner.offline.util.Time
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FilterViewModel @Inject constructor(
    private val filter: ImageFilter,
    private val saveScannedPage: SaveScannedPageUseCase,
    private val settings: com.scanner.offline.data.settings.SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FilterUiState())
    val state: StateFlow<FilterUiState> = _state.asStateFlow()

    private var sourceBitmap: Bitmap? = null
    private var imagePath: String = ""
    private var filterJob: Job? = null

    fun load(path: String) {
        imagePath = path
        viewModelScope.launch {
            // 预览只需要中等分辨率：既避免 OOM，也让切换滤镜更快。
            // 保存时 SaveScannedPageUseCase 会重新解码原图，不损失输出质量。
            val bmp = withContext(Dispatchers.IO) {
                BitmapUtils.decode(File(path), maxSize = PREVIEW_MAX_SIZE)
            }
            sourceBitmap = bmp
            applyFilter(settings.settings.value.defaultFilter)
        }
    }

    fun applyFilter(mode: FilterMode) {
        val src = sourceBitmap ?: return
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val out = withContext(Dispatchers.Default) {
                filter.apply(src, mode)
            }
            // 旧 preview 不立即 recycle：Compose 可能还在绘制上一帧。
            // 预览图已被限制在 PREVIEW_MAX_SIZE，交给 GC 即可。
            _state.value = _state.value.copy(currentMode = mode, preview = out)
        }
    }

    fun save(docId: Long?, onDone: (Long) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val mode = _state.value.currentMode
            runCatching {
                saveScannedPage(
                    processedBitmapPath = imagePath,
                    filterMode = mode,
                    docId = docId,
                    documentName = Time.nowDocName()
                )
            }.fold(
                onSuccess = onDone,
                onFailure = { onError(it.message ?: "保存失败") }
            )
        }
    }

    override fun onCleared() {
        filterJob?.cancel()
        sourceBitmap?.recycle()
        super.onCleared()
    }

    private companion object {
        const val PREVIEW_MAX_SIZE = 1600
    }
}

data class FilterUiState(
    val currentMode: FilterMode = FilterMode.ENHANCE,
    val preview: Bitmap? = null
)
