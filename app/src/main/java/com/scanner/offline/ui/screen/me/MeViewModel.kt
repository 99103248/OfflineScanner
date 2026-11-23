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

    init {
        refreshStorageStats()
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val (docBytes, cacheBytes) = withContext(Dispatchers.IO) {
                storage.calculateDocumentsBytes() to storage.calculateCacheBytes()
            }
            _state.value = _state.value.copy(
                documentsSizeText = formatBytes(docBytes),
                cacheSizeText = formatBytes(cacheBytes)
            )
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { storage.clearAllCache() }
            refreshStorageStats()
            _state.value = _state.value.copy(
                lastActionMessage = if (count > 0) "已清理 $count 个临时缓存文件" else "临时缓存已很干净"
            )
        }
    }

    /** 用户在 SAF 选择器里选完目录之后回调这里 */
    fun onDirectoryChosen(uri: Uri) {
        val ok = exportPrefs.setCustomExportDirUri(uri)
        _state.value = _state.value.copy(
            exportDirSummary = exportPrefs.describeCurrentDir(),
            isCustomDirSet = exportPrefs.getCustomExportDirUri() != null,
            lastActionMessage = if (ok) "导出目录已更新" else "保存导出目录失败，请重试"
        )
    }

    /** 用户点了"恢复默认"按钮 */
    fun onResetToDefault() {
        exportPrefs.clearCustomExportDir()
        _state.value = _state.value.copy(
            exportDirSummary = exportPrefs.describeCurrentDir(),
            isCustomDirSet = false,
            lastActionMessage = "已恢复为默认导出目录"
        )
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

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(java.util.Locale.US, "%.1f MB", mb)
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
    }
}

data class MeUiState(
    val exportDirSummary: String = "",
    val defaultExportPath: String = "",
    val isCustomDirSet: Boolean = false,
    val documentsSizeText: String = "计算中...",
    val cacheSizeText: String = "计算中...",
    val busy: Boolean = false,
    val progress: Float = 0f,
    val progressText: String? = null,
    val lastActionMessage: String? = null
)
