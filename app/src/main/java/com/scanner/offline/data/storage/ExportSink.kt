package com.scanner.offline.data.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * 一次导出任务的"输出目标"抽象。
 *
 * 因为我们要同时支持两种存储后端：
 *  1. [LocalFile]：默认情况，写到应用私有外部目录的 java.io.File
 *  2. [Saf]：用户通过 SAF 选了自定义目录，写到 ContentResolver 持有的 OutputStream
 *
 * 不直接暴露目标 File / Uri，而是统一让导出器拿到 [openOutputStream] 写入，
 * 这样上层 (DocumentExporter) 完全不用关心是哪种存储。
 *
 * 写完后，[shareUri] 可以拿到一个能跨进程分享的 URI（FileProvider / SAF 各自处理）。
 */
sealed class ExportSink {

    /** 给用户看的"文件落到哪儿"提示，例如 `/storage/emulated/0/...` 或 `Download/myDir/file.pdf` */
    abstract val humanLocation: String

    /** 给 UI 显示的文件名（含后缀） */
    abstract val displayName: String

    /** 写完后，这个文件 / URI 在文件系统中的"逻辑文件" */
    abstract val outputFile: File?  // 仅本地路径有；SAF 模式为 null

    /** 写完后用于分享的 URI（一定能 grantUriPermission 给其它 APP） */
    abstract val shareUri: Uri

    /**
     * 打开一个 OutputStream，调用方负责（用 `.use { }`）关闭它。
     *
     * 不写成接收 lambda 的高阶函数，是因为导出过程中可能要在 lambda 里调用 suspend 函数
     * （如 OCR），用普通高阶函数会丢失协程上下文，需要 inline；而 inline 函数没法在
     * sealed class 上 override —— 干脆改为返回 OutputStream。
     */
    abstract fun openOutputStream(): OutputStream

    /**
     * 默认导出目录里的本地文件（直接 FileOutputStream）。
     */
    class LocalFile(
        private val context: Context,
        val file: File,
        override val humanLocation: String
    ) : ExportSink() {
        override val displayName: String get() = file.name
        override val outputFile: File get() = file
        override val shareUri: Uri
            get() = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

        override fun openOutputStream(): OutputStream = FileOutputStream(file)
    }

    /**
     * SAF 自定义目录里的文件（通过 ContentResolver 写入）。
     */
    class Saf(
        private val context: Context,
        private val documentFile: DocumentFile,
        override val displayName: String,
        override val humanLocation: String
    ) : ExportSink() {
        override val outputFile: File? get() = null
        override val shareUri: Uri get() = documentFile.uri

        override fun openOutputStream(): OutputStream =
            context.contentResolver.openOutputStream(documentFile.uri, "w")
                ?: error("无法打开 SAF 输出流：${documentFile.uri}")
    }
}

/** 把某个 java.io.File 内容拷贝到最终 sink */
internal fun ExportSink.copyFromFile(src: File) {
    openOutputStream().use { os ->
        FileInputStream(src).use { it.copyTo(os) }
    }
}
