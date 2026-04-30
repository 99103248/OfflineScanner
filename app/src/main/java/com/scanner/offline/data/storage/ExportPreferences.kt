package com.scanner.offline.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导出相关的用户偏好（持久化在 SharedPreferences）。
 *
 * 目前只承担一项职责：保存用户通过 SAF 选择的"自定义导出目录" tree URI。
 *
 * 设计要点：
 *  - 不存路径字符串，而是存 SAF 给的 tree URI（content://...）。这是 Android 10+ 唯一
 *    能跨应用持久访问到非应用私有目录的方式。
 *  - 写入时同时调用 `ContentResolver.takePersistableUriPermission`，这样
 *    重启 / 重装 APP 后仍然有权限读写。
 *  - 当返回的 [DocumentFile] 不再可用（如目录被用户删除、权限被撤销），调用方
 *    应该用 [clearCustomExportDir] 重置回默认目录。
 */
@Singleton
class ExportPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 用户当前选择的自定义导出目录 URI，若未设置 / 已失效则返回 null。
     */
    fun getCustomExportDirUri(): Uri? {
        val raw = prefs.getString(KEY_EXPORT_DIR_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    /**
     * 把用户刚通过 ACTION_OPEN_DOCUMENT_TREE 选中的 URI 持久化下来，
     * 同时申请持久权限。
     *
     * @return 是否成功持久化
     */
    fun setCustomExportDirUri(uri: Uri): Boolean {
        return runCatching {
            // 第一步：取得跨进程持久权限
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            // 第二步：保存到 SharedPreferences
            prefs.edit().putString(KEY_EXPORT_DIR_URI, uri.toString()).apply()
            true
        }.getOrDefault(false)
    }

    /**
     * 清除自定义导出目录设置，回退到默认（应用私有外部目录）。
     * 同时尝试释放持久权限（释放失败不影响清除动作）。
     */
    fun clearCustomExportDir() {
        getCustomExportDirUri()?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        prefs.edit().remove(KEY_EXPORT_DIR_URI).apply()
    }

    /**
     * 把当前的 URI 渲染为给用户看的"友好路径"。
     *
     * SAF 的 tree URI 形如 `content://com.android.externalstorage.documents/tree/primary%3ADownload%2Foffline-scanner`，
     * 不直观，用 [DocumentFile] 拿到名字后展示。
     */
    fun describeCurrentDir(): String {
        val uri = getCustomExportDirUri() ?: return DEFAULT_DIR_HINT
        val df = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
        return when {
            df == null || !df.canWrite() -> "$DEFAULT_DIR_HINT（自定义目录已失效）"
            else -> {
                val name = df.name ?: uri.lastPathSegment.orEmpty()
                "自定义目录：$name"
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "export_preferences"
        const val KEY_EXPORT_DIR_URI = "export_dir_uri"
        const val DEFAULT_DIR_HINT = "默认目录（应用私有外部存储）"
    }
}
