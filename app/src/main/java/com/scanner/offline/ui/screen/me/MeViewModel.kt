package com.scanner.offline.ui.screen.me

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.scanner.offline.data.storage.ExportPreferences
import com.scanner.offline.data.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * "我的"页面用的 ViewModel：仅承担"读取/写入导出目录设置"这一职责。
 */
@HiltViewModel
class MeViewModel @Inject constructor(
    private val exportPrefs: ExportPreferences,
    private val storage: StorageManager
) : ViewModel() {

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<MeUiState> = _state.asStateFlow()

    /** 用户在 SAF 选择器里选完目录之后回调这里 */
    fun onDirectoryChosen(uri: Uri) {
        val ok = exportPrefs.setCustomExportDirUri(uri)
        _state.value = buildState().copy(
            lastActionMessage = if (ok) "导出目录已更新" else "保存导出目录失败，请重试"
        )
    }

    /** 用户点了"恢复默认"按钮 */
    fun onResetToDefault() {
        exportPrefs.clearCustomExportDir()
        _state.value = buildState().copy(lastActionMessage = "已恢复为默认导出目录")
    }

    fun onMessageShown() {
        _state.value = _state.value.copy(lastActionMessage = null)
    }

    private fun buildState(): MeUiState {
        val customSet = exportPrefs.getCustomExportDirUri() != null
        return MeUiState(
            exportDirSummary = exportPrefs.describeCurrentDir(),
            defaultExportPath = storage.exportRoot.absolutePath,
            isCustomDirSet = customSet
        )
    }
}

data class MeUiState(
    val exportDirSummary: String = "",
    val defaultExportPath: String = "",
    val isCustomDirSet: Boolean = false,
    val lastActionMessage: String? = null
)
