package com.scanner.offline.domain.model

/**
 * 业务层使用的纯领域模型，与数据库实体解耦
 */

/** 一份完整的扫描文档（可包含多页） */
data class Document(
    val id: Long = 0L,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: List<Page> = emptyList()
)

/** 文档中的一页：原图 + 处理后图 + 缩略图 + 已识别文字 */
data class Page(
    val id: Long = 0L,
    val docId: Long,
    val index: Int,
    val originalPath: String,
    val processedPath: String,
    val thumbnailPath: String,
    val ocrText: String? = null,
    val ocrLanguage: Language? = null
)

/** 支持的 OCR 语言 —— 后续扩展只需新增枚举值并实现对应 OcrEngine */
enum class Language(val code: String, val displayName: String) {
    AUTO("auto", "自动"),
    CHINESE("zh", "中文"),
    ENGLISH("en", "英文"),
    JAPANESE("ja", "日语"),     // 预留
    KOREAN("ko", "韩语"),       // 预留
    LATIN("latin", "拉丁文")    // 预留
}

/** OCR 识别结果 */
data class OcrResult(
    val text: String,
    val language: Language,
    val blocks: List<TextBlock> = emptyList(),
    val processingTimeMs: Long = 0L
)

data class TextBlock(
    val text: String,
    val confidence: Float = 1f,
    val boundingBox: BoundingBox? = null
)

data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** 滤镜模式 */
enum class FilterMode(val displayName: String) {
    ORIGINAL("原图"),
    ENHANCE("增强"),
    GRAYSCALE("灰度"),
    BLACK_WHITE("黑白"),
    REMOVE_SHADOW("去阴影")
}

/** 导出格式 */
enum class ExportFormat(val extension: String, val mimeType: String, val displayName: String) {
    PDF("pdf", "application/pdf", "PDF 文档"),
    WORD("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word 文档"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel 表格"),
    TXT("txt", "text/plain", "纯文本"),
    JPG("jpg", "image/jpeg", "JPG 图片"),
    PNG("png", "image/png", "PNG 图片"),
    WEBP("webp", "image/webp", "WebP 图片")
}

/** 表格识别结果（用于 Excel 导出） */
data class TableData(
    val rows: List<List<String>>
) {
    val rowCount: Int get() = rows.size
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0
}
