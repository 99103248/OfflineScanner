package com.scanner.offline.ui.screen.me

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.data.backup.BackupManager
import com.scanner.offline.data.storage.ExportPreferences
import com.scanner.offline.data.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * "我的"页面用的 ViewModel：导出目录设置 + 本地备份/恢复。
 */
@HiltViewModel
class MeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportPrefs: ExportPreferences,
    private val storage: StorageManager,
    private val backupManager: BackupManager
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

    fun backup(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, progress = 0f, lastActionMessage = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        backupManager.backupTo(os) { done, total ->
                            _state.value = _state.value.copy(
                                progress = done.toFloat() / total.coerceAtLeast(1),
                                progressText = "正在备份 $done/$total"
                            )
                        }
                    } ?: error("无法写入备份文件")
                }
            }.onSuccess { summary ->
                _state.value = _state.value.copy(
                    busy = false, progressText = null,
                    lastActionMessage = "已备份 ${summary.documentCount} 个文档、${summary.pageCount} 页"
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    busy = false, progressText = null,
                    lastActionMessage = "备份失败：${e.message ?: ""}"
                )
            }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, progress = 0f, lastActionMessage = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        backupManager.restoreFrom(input) { done, total ->
                            _state.value = _state.value.copy(
                                progress = done.toFloat() / total.coerceAtLeast(1),
                                progressText = "正在恢复 $done/$total"
                            )
                        }
                    } ?: error("无法读取备份文件")
                }
            }.onSuccess { summary ->
                _state.value = _state.value.copy(
                    busy = false, progressText = null,
                    lastActionMessage = "已恢复 ${summary.documentCount} 个文档、${summary.pageCount} 页"
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    busy = false, progressText = null,
                    lastActionMessage = "恢复失败：${e.message ?: ""}"
                )
            }
        }
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
    val busy: Boolean = false,
    val progress: Float = 0f,
    val progressText: String? = null,
    val lastActionMessage: String? = null
)
