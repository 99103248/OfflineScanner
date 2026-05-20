package com.scanner.offline.ui.screen.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.data.storage.ExportResult
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.InputDocumentType
import com.scanner.offline.domain.model.PdfImageLayout
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.usecase.ConvertFileToImagesUseCase
import com.scanner.offline.domain.usecase.ConvertImageFormatUseCase
import com.scanner.offline.domain.usecase.ExportDocumentUseCase
import com.scanner.offline.engine.export.FileToImageConverter
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
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportDocument: ExportDocumentUseCase,
    private val convertImage: ConvertImageFormatUseCase,
    private val convertFileToImages: ConvertFileToImagesUseCase,
    private val storage: StorageManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun export(docId: Long, format: ExportFormat) {
        _state.value = _state.value.copy(running = true, error = null, lastResult = null, lastResults = emptyList())
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

    /** 图片格式互转（JPG / PNG / WebP） */
    fun convertImageFromUri(uriString: String, format: ExportFormat, baseName: String) {
        _state.value = _state.value.copy(
            running = true, error = null, lastResult = null, lastResults = emptyList(),
            inputType = InputDocumentType.IMAGE
        )
        viewModelScope.launch {
            val sourcePath = withContext(Dispatchers.IO) {
                resolveLocalPath(uriString, asImage = true)
            }
            if (sourcePath == null) {
                _state.value = _state.value.copy(running = false, error = "无法读取图片")
                return@launch
            }
            runCatching { convertImage(sourcePath, format, baseName) }
                .onSuccess { r ->
                    _state.value = _state.value.copy(
                        running = false, lastResult = r, lastResults = listOf(r), lastFormat = format
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(running = false, error = e.message ?: "转换失败")
                }
        }
    }

    /** PDF / Word / TXT / 图片 → 图片 */
    fun convertDocumentFromUri(
        uriString: String,
        format: ExportFormat,
        baseName: String,
        pdfLayout: PdfImageLayout = PdfImageLayout.SEPARATE_PAGES
    ) {
        _state.value = _state.value.copy(
            running = true, error = null, lastResult = null, lastResults = emptyList()
        )
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) { resolveDocument(uriString) }
            if (resolved == null) {
                _state.value = _state.value.copy(running = false, error = "无法读取文件")
                return@launch
            }
            val (path, type) = resolved
            _state.value = _state.value.copy(inputType = type)
            val effectiveFormat = if (type == InputDocumentType.IMAGE) format else format
            runCatching {
                if (type == InputDocumentType.IMAGE) {
                    listOf(convertImage(path, effectiveFormat, baseName))
                } else {
                    convertFileToImages(path, type, effectiveFormat, baseName, pdfLayout)
                }
            }.onSuccess { results ->
                _state.value = _state.value.copy(
                    running = false,
                    lastResult = results.firstOrNull(),
                    lastResults = results,
                    lastFormat = effectiveFormat
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(running = false, error = e.message ?: "转换失败")
            }
        }
    }

    private fun resolveDocument(uriString: String): Pair<String, InputDocumentType>? {
        File(uriString).takeIf { it.exists() }?.let { file ->
            return runCatching {
                file.absolutePath to FileToImageConverter.detectType(file, null)
            }.getOrNull()
        }
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (uri.scheme == "file") {
            val file = uri.path?.let { File(it) }?.takeIf { it.exists() } ?: return null
            return runCatching {
                file.absolutePath to FileToImageConverter.detectType(file, null)
            }.getOrNull()
        }
        val mime = context.contentResolver.getType(uri)
        val ext = guessExtension(uri, mime)
        val temp = File(storage.cacheRoot, "import_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).use { output -> input.copyTo(output) }
        } ?: return null
        return runCatching {
            temp.absolutePath to FileToImageConverter.detectType(temp, mime)
        }.getOrNull()
    }

    private fun guessExtension(uri: Uri, mime: String?): String {
        mime?.let {
            when {
                it.contains("pdf") -> return "pdf"
                it.contains("word") || it.contains("docx") -> return "docx"
                it.startsWith("text/") -> return "txt"
                it.contains("jpeg") -> return "jpg"
                it.contains("png") -> return "png"
                it.contains("webp") -> return "webp"
            }
        }
        val name = uri.lastPathSegment.orEmpty().lowercase()
        return name.substringAfterLast('.', "bin")
    }

    private fun resolveLocalPath(raw: String, asImage: Boolean): String? {
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
    val lastResults: List<ExportResult> = emptyList(),
    val lastFormat: ExportFormat? = null,
    val inputType: InputDocumentType? = null
)
