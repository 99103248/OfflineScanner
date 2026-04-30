package com.scanner.offline.ui.screen.filter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.domain.model.FilterMode
import com.scanner.offline.domain.usecase.SaveScannedPageUseCase
import com.scanner.offline.engine.image.ImageFilter
import com.scanner.offline.util.Time
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FilterViewModel @Inject constructor(
    private val filter: ImageFilter,
    private val saveScannedPage: SaveScannedPageUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(FilterUiState())
    val state: StateFlow<FilterUiState> = _state.asStateFlow()

    private var sourceBitmap: Bitmap? = null
    private var imagePath: String = ""

    fun load(path: String) {
        imagePath = path
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(path)
            }
            sourceBitmap = bmp
            applyFilter(FilterMode.ENHANCE)
        }
    }

    fun applyFilter(mode: FilterMode) {
        val src = sourceBitmap ?: return
        viewModelScope.launch {
            val out = withContext(Dispatchers.Default) {
                filter.apply(src, mode)
            }
            _state.value = _state.value.copy(currentMode = mode, preview = out)
        }
    }

    fun save(onDone: (Long) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val mode = _state.value.currentMode
            runCatching {
                saveScannedPage(
                    processedBitmapPath = imagePath,
                    filterMode = mode,
                    docId = null,
                    documentName = Time.nowDocName()
                )
            }.fold(
                onSuccess = onDone,
                onFailure = { onError(it.message ?: "保存失败") }
            )
        }
    }

    override fun onCleared() {
        sourceBitmap?.recycle()
        super.onCleared()
    }
}

data class FilterUiState(
    val currentMode: FilterMode = FilterMode.ENHANCE,
    val preview: Bitmap? = null
)
