package com.scanner.offline.ui.screen.ocr

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.OcrResult
import com.scanner.offline.domain.usecase.RecognizeTextUseCase
import com.scanner.offline.util.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OcrViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recognize: RecognizeTextUseCase,
    private val storage: StorageManager
) : ViewModel() {

    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state.asStateFlow()

    private var imagePath: String = ""
    private var pageId: Long? = null

    /**
     * 输入可能是 file:// 形式的 cache uri、内容 uri，或文件绝对路径。
     * 这里做归一化：把所有非本地路径都先复制到 cache 中。
     */
    fun load(rawPath: String, pageIdToBind: Long?) {
        pageId = pageIdToBind
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) { resolveLocalPath(rawPath) }
            if (resolved == null) {
                _state.value = _state.value.copy(error = "无法读取图片")
                return@launch
            }
            imagePath = resolved
            _state.value = _state.value.copy(imagePath = resolved)
            run(Language.AUTO)
        }
    }

    fun run(language: Language) {
        if (imagePath.isEmpty()) return
        _state.value = _state.value.copy(running = true, error = null, language = language)
        viewModelScope.launch {
            runCatching { recognize(imagePath, language, pageId) }
                .onSuccess { res ->
                    _state.value = _state.value.copy(
                        running = false,
                        result = res
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        running = false,
                        error = e.message ?: "识别失败"
                    )
                }
        }
    }

    private fun resolveLocalPath(raw: String): String? {
        // 已经是本地路径
        java.io.File(raw).takeIf { it.exists() }?.let { return it.absolutePath }
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (uri.scheme == "file") return uri.path?.let { p -> java.io.File(p).takeIf { it.exists() }?.absolutePath }
        // content:// → 复制到 cache
        val bmp = BitmapUtils.decode(context.contentResolver, uri) ?: return null
        val out = storage.newCacheImage("ocr")
        return storage.saveBitmap(bmp, out).absolutePath.also { bmp.recycle() }
    }
}

data class OcrUiState(
    val imagePath: String? = null,
    val running: Boolean = false,
    val language: Language = Language.AUTO,
    val result: OcrResult? = null,
    val error: String? = null
)
