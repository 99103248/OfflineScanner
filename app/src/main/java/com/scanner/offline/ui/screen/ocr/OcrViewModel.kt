package com.scanner.offline.ui.screen.ocr

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.OcrResult
import com.scanner.offline.domain.usecase.RecognizeTextUseCase
import com.scanner.offline.engine.ocr.TableParser
import com.scanner.offline.util.BitmapUtils
import com.scanner.offline.util.Time
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
class OcrViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recognize: RecognizeTextUseCase,
    private val storage: StorageManager,
    private val repo: com.scanner.offline.domain.repository.DocumentRepository,
    private val exporter: com.scanner.offline.engine.export.DocumentExporter
) : ViewModel() {

    private var imagePath: String = ""
    private var pageId: Long? = null
    private var initializedKey: String? = null
    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state.asStateFlow()

    /**
     * 输入可能是 file:// 形式的 cache uri、内容 uri，或文件绝对路径。
     * 若绑定了已有页面且该页面已有识别/校对文本，则优先直接展示，避免覆盖用户手工校对内容。
     */
    fun load(rawPath: String, pageIdToBind: Long?) {
        val key = "$rawPath#$pageIdToBind"
        if (initializedKey == key && _state.value.imagePath != null) return
        initializedKey = key
        pageId = pageIdToBind
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) { resolveLocalPath(rawPath) }
            if (resolved == null) {
                _state.value = _state.value.copy(error = "无法读取图片", isBoundToPage = pageIdToBind != null)
                return@launch
            }
            imagePath = resolved
            val existingPage = pageIdToBind?.let { repo.getPage(it) }
            val existingText = existingPage?.ocrText
            if (!existingText.isNullOrBlank()) {
                val lang = existingPage.ocrLanguage ?: Language.AUTO
                _state.value = OcrUiState(
                    imagePath = resolved,
                    running = false,
                    language = lang,
                    result = OcrResult(
                        text = existingText,
                        language = lang,
                        blocks = existingPage.ocrBlocks
                    ),
                    editedText = existingText,
                    isBoundToPage = true
                )
            } else {
                _state.value = _state.value.copy(
                    imagePath = resolved,
                    isBoundToPage = pageIdToBind != null
                )
                run(Language.AUTO)
            }
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
                        result = res,
                        editedText = res.text
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

    fun updateEdited(text: String) {
        _state.value = _state.value.copy(editedText = text)
    }

    fun saveEdits(onDone: (String) -> Unit) {
        val text = _state.value.editedText
        val pid = pageId
        viewModelScope.launch {
            if (pid != null) {
                val blocks = if (text == _state.value.result?.text) {
                    _state.value.result?.blocks.orEmpty()
                } else {
                    emptyList()
                }
                repo.updatePageOcr(pid, text, _state.value.language.code, blocks)
                onDone("已保存校对")
            } else {
                val name = withContext(Dispatchers.IO) {
                    val sink = storage.createExportSink(
                        Time.nowDocName("ocr"),
                        "txt",
                        "text/plain"
                    )
                    sink.openOutputStream().use { exporter.textsToTxt(listOf(text), it) }
                    sink.displayName
                }
                onDone("已导出 $name")
            }
        }
    }

    fun exportWord(onDone: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val sink = storage.createExportSink(
                        Time.nowDocName("ocr"),
                        "docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )
                    sink.openOutputStream().use { exporter.textsToWord(listOf(_state.value.editedText), it) }
                    sink.displayName
                }
            }.onSuccess(onDone).onFailure { onError(it.message ?: "导出失败") }
        }
    }

    fun exportExcel(onDone: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val current = _state.value
                    val blocks = if (current.editedText == current.result?.text) {
                        current.result?.blocks.orEmpty()
                    } else {
                        emptyList()
                    }
                    val table = TableParser.fromOcr(current.editedText, blocks)
                    val sink = storage.createExportSink(
                        Time.nowDocName("table"),
                        "xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    )
                    sink.openOutputStream().use { exporter.tableToExcel(listOf(table), it) }
                    sink.displayName
                }
            }.onSuccess(onDone).onFailure { onError(it.message ?: "导出失败") }
        }
    }

    private fun resolveLocalPath(raw: String): String? {
        File(raw).takeIf { it.exists() }?.let { return it.absolutePath }
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (uri.scheme == "file") return uri.path?.let { p -> File(p).takeIf { it.exists() }?.absolutePath }
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
    val editedText: String = "",
    val error: String? = null,
    val isBoundToPage: Boolean = false
)
