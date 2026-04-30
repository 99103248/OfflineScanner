package com.scanner.offline.ui.screen.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val repo: DocumentRepository
) : ViewModel() {

    private val _doc = MutableStateFlow<Document?>(null)
    val doc: StateFlow<Document?> = _doc.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch {
            _doc.value = repo.get(id)
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch {
            repo.rename(id, name)
            _doc.value = repo.get(id)
        }
    }

    fun deletePage(pageId: Long, docId: Long) {
        viewModelScope.launch {
            repo.deletePage(pageId)
            _doc.value = repo.get(docId)
        }
    }
}
