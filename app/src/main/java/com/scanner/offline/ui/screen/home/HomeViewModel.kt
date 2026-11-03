package com.scanner.offline.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.model.DocumentSort
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.repository.DocumentRepository
import com.scanner.offline.domain.usecase.ExportDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val exportDocument: dagger.Lazy<ExportDocumentUseCase>,
    private val settings: com.scanner.offline.data.settings.SettingsRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(settings.settings.value.sort)
    val sort: StateFlow<DocumentSort> = _sort.asStateFlow()

    /** 设置页配置的默认视图（持续订阅，设置变更后立即生效） */
    val gridViewSetting: StateFlow<Boolean> = settings.settings
        .map { it.gridView }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            settings.settings.value.gridView
        )

    init {
        // 回收站自动清理（按设置的天数）
        val days = settings.settings.value.trashRetentionDays
        if (days > 0) {
            viewModelScope.launch {
                repo.purgeTrashOlderThan(System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L)
            }
        }
    }

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    val documents: StateFlow<List<Document>> = combine(
        _query.debounce(250),
        _sort
    ) { q, s -> q.trim() to s }
        .flatMapLatest { (q, s) -> repo.observeAll(q).let { flow -> sortFlow(flow, s) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setSort(sort: DocumentSort) {
        _sort.value = sort
        settings.update { it.copy(sort = sort) }
    }

    fun setGridView(grid: Boolean) {
        settings.update { it.copy(gridView = grid) }
    }

    fun toggleSelection(id: Long) {
        _selected.update { if (id in it) it - id else it + id }
    }

    fun selectAll(ids: List<Long>) {
        _selected.value = ids.toSet()
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.moveToTrash(id) }
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { repo.setFavorite(id, favorite) }
    }

    fun rename(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.rename(id, name.trim()) }
    }

    fun batchDelete(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repo.moveToTrash(it) }
            _selected.value = emptySet()
            _ui.update { it.copy(message = "已移入回收站 ${ids.size} 个文档") }
        }
    }

    fun batchFavorite(ids: Set<Long>, favorite: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repo.setFavorite(it, favorite) }
            _selected.value = emptySet()
            _ui.update { it.copy(message = if (favorite) "已收藏 ${ids.size} 个文档" else "已取消收藏") }
        }
    }

    /** 批量导出为 PDF（每个文档一个文件）。 */
    fun batchExportPdf(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            var ok = 0
            var failed = 0
            val appSettings = settings.settings.value
            ids.forEach { id ->
                runCatching {
                    exportDocument.get()(
                        docId = id,
                        format = ExportFormat.PDF,
                        searchablePdf = false,
                        pdfPaper = appSettings.pdfPaper,
                        pdfMaxImageDimension = appSettings.pdfMaxImageDimension
                    )
                }.onSuccess { ok++ }.onFailure { failed++ }
            }
            _selected.value = emptySet()
            _ui.update {
                it.copy(
                    busy = false,
                    message = if (failed == 0) "已导出 $ok 个 PDF" else "导出完成：成功 $ok，失败 $failed"
                )
            }
        }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    private fun sortFlow(
        flow: kotlinx.coroutines.flow.Flow<List<Document>>,
        sort: DocumentSort
    ): kotlinx.coroutines.flow.Flow<List<Document>> {
        return flow.map { docs ->
            val comparator: Comparator<Document> = when (sort) {
                DocumentSort.UPDATED -> compareByDescending { it.updatedAt }
                DocumentSort.CREATED -> compareByDescending { it.createdAt }
                DocumentSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            }
            // 收藏始终置顶，其余按用户选择排序
            docs.sortedWith(compareByDescending<Document> { it.isFavorite }.then(comparator))
        }
    }
}

data class HomeUiState(
    val busy: Boolean = false,
    val message: String? = null
)
