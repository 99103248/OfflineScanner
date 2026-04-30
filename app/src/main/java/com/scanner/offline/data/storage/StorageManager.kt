package com.scanner.offline.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一管理应用文件的存储位置：
 *
 * - documents/<docId>/<page>_orig.jpg     原图
 * - documents/<docId>/<page>_proc.jpg     处理后
 * - documents/<docId>/<page>_thumb.jpg    缩略图
 * - exports/                              默认导出目录（应用私有外部目录）
 *
 * 用户可通过 SAF 自定义导出目录，由 [ExportPreferences] 持久化，
 * 这里通过 [createExportSink] / [writeExportTo] 屏蔽两种导出目标的差异。
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportPrefs: ExportPreferences
) {
    private val documentsRoot: File =
        File(context.filesDir, "documents").apply { mkdirs() }

    /** 用户可访问到的默认导出目录：/Android/data/<package>/files/exports */
    val exportRoot: File =
        File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }

    /** 临时目录，用于摄像头/裁剪等中间结果 */
    val cacheRoot: File =
        File(context.cacheDir, "tmp").apply { mkdirs() }

    fun documentDir(docId: Long): File =
        File(documentsRoot, docId.toString()).apply { mkdirs() }

    fun newOriginalFile(docId: Long, pageIndex: Int): File =
        File(documentDir(docId), "${pageIndex}_orig.jpg")

    fun newProcessedFile(docId: Long, pageIndex: Int): File =
        File(documentDir(docId), "${pageIndex}_proc.jpg")

    fun newThumbnailFile(docId: Long, pageIndex: Int): File =
        File(documentDir(docId), "${pageIndex}_thumb.jpg")

    fun newCacheImage(prefix: String = "img"): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return File(cacheRoot, "${prefix}_$ts.jpg")
    }

    /** 默认导出目录下创建文件（旧路径，给不支持 SAF 的代码兜底） */
    fun newExportFile(name: String, extension: String): File =
        File(exportRoot, "${sanitize(name)}.$extension")

    /** 把 Bitmap 写到目标 File（JPEG），quality 默认 92 */
    fun saveBitmap(bitmap: Bitmap, target: File, quality: Int = 92): File {
        FileOutputStream(target).use { os ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, os)
        }
        return target
    }

    // ---------------- 导出目录抽象 ----------------

    /**
     * 为本次导出创建一个"输出目标"。
     *
     * 优先使用用户在设置中选择的 SAF 目录；若未配置或目录已失效，回退到默认目录。
     *
     * @param name      期望的文件名（不含后缀，会自动 sanitize 掉路径分隔符等）
     * @param extension 文件后缀（"pdf" / "docx" / ...）
     * @param mimeType  MIME 类型（仅 SAF 模式下使用，决定 ContentResolver 给的实际后缀）
     */
    fun createExportSink(name: String, extension: String, mimeType: String): ExportSink {
        val safeName = sanitize(name)
        val customUri = exportPrefs.getCustomExportDirUri()
        if (customUri != null) {
            val tree = runCatching { DocumentFile.fromTreeUri(context, customUri) }.getOrNull()
            if (tree != null && tree.canWrite()) {
                // SAF 的 createFile 会自动加后缀，所以这里 displayName 不带 ext
                val target = tree.createFile(mimeType, safeName)
                if (target != null) {
                    return ExportSink.Saf(
                        context = context,
                        documentFile = target,
                        displayName = "${safeName}.$extension",
                        humanLocation = describeSafLocation(tree, target)
                    )
                }
            }
        }
        // fallback：默认目录
        val file = File(exportRoot, "$safeName.$extension")
        return ExportSink.LocalFile(
            context = context,
            file = file,
            humanLocation = file.absolutePath
        )
    }

    private fun describeSafLocation(tree: DocumentFile, target: DocumentFile): String {
        val dirName = tree.name ?: "(自定义目录)"
        val fileName = target.name ?: "(未命名)"
        return "$dirName/$fileName"
    }

    private fun sanitize(name: String): String =
        name.ifBlank { "export" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}

