package com.scanner.offline.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ShareUtils {

    /** 通过系统分享面板分享应用私有目录里的文件（自动包装成 FileProvider URI） */
    fun share(context: Context, file: File, mimeType: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        share(context, uri, mimeType)
    }

    /**
     * 通过系统分享面板分享一个已经能跨进程访问的 URI（如 SAF 给的 content:// URI、
     * 或 FileProvider 已经包装好的 URI）。
     */
    fun share(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
