package com.scanner.offline.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repo: DocumentRepository
) : ViewModel() {

    val documents: StateFlow<List<Document>> = repo.observeTrashed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(id: Long) {
        viewModelScope.launch { repo.restoreFromTrash(id) }
    }

    fun deleteForever(id: Long) {
        viewModelScope.launch { repo.deleteForever(id) }
    }

    fun emptyTrash(onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repo.emptyTrash()) }
    }
}
