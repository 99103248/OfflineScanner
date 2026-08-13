package com.scanner.offline.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class ScanSessionViewModel @Inject constructor(
    val session: ScanSession
) : ViewModel()

@Singleton
class ScanSession @Inject constructor() {
    private val _burstUris = MutableStateFlow<List<String>>(emptyList())
    val burstUris: StateFlow<List<String>> = _burstUris.asStateFlow()

    fun setBurst(uris: List<String>) {
        _burstUris.value = uris
    }

    fun clearBurst() {
        _burstUris.value = emptyList()
    }
}
