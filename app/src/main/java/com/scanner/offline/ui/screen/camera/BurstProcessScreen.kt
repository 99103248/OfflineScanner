package com.scanner.offline.ui.screen.camera

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scanner.offline.domain.model.ScanSession
import com.scanner.offline.domain.usecase.SaveBurstPagesUseCase
import com.scanner.offline.util.Time
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BurstProcessViewModel @Inject constructor(
    private val session: ScanSession,
    private val saveBurst: SaveBurstPagesUseCase
) : ViewModel() {
    data class Ui(val running: Boolean = true, val error: String? = null, val docId: Long? = null)
    private val _state = MutableStateFlow(Ui())
    val state = _state.asStateFlow()

    fun start() {
        viewModelScope.launch {
            val uris = session.burstUris.value
            if (uris.isEmpty()) {
                _state.value = Ui(running = false, error = "没有连拍图片")
                return@launch
            }
            runCatching { saveBurst(uris, Time.nowDocName("连拍")) }
                .onSuccess {
                    session.clearBurst()
                    _state.value = Ui(running = false, docId = it)
                }
                .onFailure { _state.value = Ui(running = false, error = it.message ?: "保存失败") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BurstProcessScreen(
    onDone: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: BurstProcessViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.start() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.docId) { state.docId?.let(onDone) }
    Scaffold(topBar = { TopAppBar(title = { Text("连拍保存") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.running) {
                CircularProgressIndicator()
                Text("正在自动矫正并保存多页...", modifier = Modifier.padding(top = 16.dp))
            } else if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
