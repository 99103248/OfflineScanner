package com.scanner.offline.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object UriUtils {

    /**
     * 将 Content URI 立即流式写入应用内部临时 cache 文件。
     * 使用 8KB 缓冲区分块复制，不占额外堆内存，并彻底规避 Content URI 跨进程 / 跨生命周期权限丢失问题。
     */
    fun copyUriToCache(context: Context, uri: Uri, prefix: String = "pick"): String? = runCatching {
        val cacheDir = File(context.cacheDir, "tmp").apply { mkdirs() }
        val ext = when (context.contentResolver.getType(uri)?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "application/pdf" -> "pdf"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/msword" -> "doc"
            "text/plain" -> "txt"
            else -> "jpg"
        }
        val target = File(cacheDir, "${prefix}_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        } ?: return@runCatching null
        target.absolutePath
    }.getOrNull()
}
