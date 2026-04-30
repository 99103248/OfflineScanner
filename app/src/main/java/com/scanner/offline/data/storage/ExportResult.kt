package com.scanner.offline.data.storage

import android.net.Uri
import java.io.File

/**
 * 一次导出任务的结果，给 UI / ViewModel 使用。
 *
 * 不直接返回 [java.io.File]，因为 SAF 模式下没有 File。
 *
 * @property displayName     给用户看的文件名（含后缀）
 * @property humanLocation   给用户看的位置文字（绝对路径或 "目录名/文件名"）
 * @property shareUri        可以传给 Intent.EXTRA_STREAM 的 URI
 * @property outputFile      若为本地导出（默认目录），返回 File；SAF 模式为 null
 * @property mimeType        MIME 类型（用于分享 Intent）
 */
data class ExportResult(
    val displayName: String,
    val humanLocation: String,
    val shareUri: Uri,
    val outputFile: File?,
    val mimeType: String
)
