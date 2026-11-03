package com.scanner.offline.ui.screen.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.repository.DocumentRepository
import com.scanner.offline.domain.usecase.AppendImagesUseCase
import com.scanner.offline.domain.usecase.RecognizeTextUseCase
import com.scanner.offline.domain.usecase.RotatePageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val appendImages: AppendImagesUseCase,
    private val rotatePage: RotatePageUseCase,
    private val recognize: RecognizeTextUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(DocumentDetailUiState())
    val state: StateFlow<DocumentDetailUiState> = _state.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch {
            _state.update { it.copy(doc = repo.get(id)) }
        }
    }

    fun rename(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.rename(id, name.trim())
            reload(id)
        }
    }

    fun deletePage(docId: Long, pageId: Long) {
        viewModelScope.launch {
            repo.deletePage(pageId)
            reload(docId)
        }
    }

    /** delta = -1 上移，+1 下移 */
    fun movePage(docId: Long, pageId: Long, delta: Int) {
        val pages = _state.value.doc?.pages ?: return
        val index = pages.indexOfFirst { it.id == pageId }
        val target = index + delta
        if (index < 0 || target !in pages.indices) return
        val ids = pages.map { it.id }.toMutableList()
        val moved = ids.removeAt(index)
        ids.add(target, moved)
        viewModelScope.launch {
            repo.reorderPages(docId, ids)
            reload(docId)
        }
    }

    fun setCover(docId: Long, pageId: Long) {
        val pages = _state.value.doc?.pages ?: return
        if (pages.firstOrNull()?.id == pageId) return
        val ids = listOf(pageId) + pages.map { it.id }.filter { it != pageId }
        viewModelScope.launch {
            repo.reorderPages(docId, ids)
            reload(docId)
        }
    }

    fun rotatePage(docId: Long, pageId: Long, clockwise: Boolean = true) {
        viewModelScope.launch {
            runCatching { rotatePage(pageId, clockwise) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "旋转失败") } }
            reload(docId)
        }
    }

    /** 从相册批量追加页面 */
    fun appendImages(docId: Long, uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, progress = 0f, progressText = "正在处理图片...") }
            runCatching {
                appendImages(
                    docId = docId,
                    imageUris = uris,
                    onProgress = { done, total ->
                        _state.update {
                            it.copy(
                                progress = done.toFloat() / total.coerceAtLeast(1),
                                progressText = "正在处理 $done/$total"
                            )
                        }
                    }
                )
            }.onSuccess { added ->
                _state.update {
                    it.copy(busy = false, progressText = null, message = "已追加 $added 页")
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(busy = false, progressText = null, error = e.message ?: "追加页面失败")
                }
            }
            reload(docId)
        }
    }

    /** 整份文档逐页 OCR；已有文字的页面会跳过，避免覆盖用户校对结果。 */
    fun ocrAll(docId: Long) {
        viewModelScope.launch {
            val pages = repo.get(docId)?.pages.orEmpty()
            if (pages.isEmpty()) return@launch
            _state.update { it.copy(busy = true, progress = 0f, progressText = "正在识别 0/${pages.size}") }
            var failed = 0
            pages.forEachIndexed { index, page ->
                if (page.ocrText.isNullOrBlank()) {
                    runCatching {
                        recognize(page.processedPath, Language.AUTO, page.id)
                    }.onFailure { failed++ }
                }
                _state.update {
                    it.copy(
                        progress = (index + 1).toFloat() / pages.size,
                        progressText = "正在识别 ${index + 1}/${pages.size}"
                    )
                }
            }
            _state.update {
                it.copy(
                    busy = false,
                    progressText = null,
                    message = if (failed == 0) "识别完成" else "识别完成，$failed 页失败"
                )
            }
            reload(docId)
        }
    }

    fun moveToTrash(docId: Long) {
        viewModelScope.launch { repo.moveToTrash(docId) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    private suspend fun reload(id: Long) {
        _state.update { it.copy(doc = repo.get(id)) }
    }
}

data class DocumentDetailUiState(
    val doc: Document? = null,
    val busy: Boolean = false,
    val progress: Float = 0f,
    val progressText: String? = null,
    val message: String? = null,
    val error: String? = null
)
