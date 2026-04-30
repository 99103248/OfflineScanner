package com.scanner.offline.ui.screen.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.data.storage.ExportResult
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.usecase.ConvertImageFormatUseCase
import com.scanner.offline.domain.usecase.ExportDocumentUseCase
import com.scanner.offline.util.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportDocument: ExportDocumentUseCase,
    private val convertImage: ConvertImageFormatUseCase,
    private val storage: StorageManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun export(docId: Long, format: ExportFormat) {
        _state.value = _state.value.copy(running = true, error = null, lastResult = null)
        viewModelScope.launch {
            runCatching { exportDocument(docId = docId, format = format, ocrLanguage = Language.AUTO) }
                .onSuccess { r ->
                    _state.value = _state.value.copy(running = false, lastResult = r, lastFormat = format)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(running = false, error = e.message ?: "导出失败")
                }
        }
    }

    /** 用于 [FormatConvertScreen]：把任意来源的图片转换成指定格式 */
    fun convertImageFromUri(uriString: String, format: ExportFormat, baseName: String) {
        _state.value = _state.value.copy(running = true, error = null, lastResult = null)
        viewModelScope.launch {
            val sourcePath = withContext(Dispatchers.IO) {
                resolveLocalPath(uriString)
            }
            if (sourcePath == null) {
                _state.value = _state.value.copy(running = false, error = "无法读取图片")
                return@launch
            }
            runCatching { convertImage(sourcePath, format, baseName) }
                .onSuccess { r ->
                    _state.value = _state.value.copy(running = false, lastResult = r, lastFormat = format)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(running = false, error = e.message ?: "转换失败")
                }
        }
    }

    private fun resolveLocalPath(raw: String): String? {
        File(raw).takeIf { it.exists() }?.let { return it.absolutePath }
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (uri.scheme == "file") return uri.path?.let { p -> File(p).takeIf { it.exists() }?.absolutePath }
        val bmp = BitmapUtils.decode(context.contentResolver, uri) ?: return null
        val out = storage.newCacheImage("conv")
        return storage.saveBitmap(bmp, out).absolutePath.also { bmp.recycle() }
    }
}

data class ExportUiState(
    val running: Boolean = false,
    val error: String? = null,
    val lastResult: ExportResult? = null,
    val lastFormat: ExportFormat? = null
)
