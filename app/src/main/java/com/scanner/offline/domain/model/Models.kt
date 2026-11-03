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
    val isFavorite: Boolean = false,
    /** 非 null 表示已进回收站（软删除时间戳） */
    val deletedAt: Long? = null,
    val pages: List<Page> = emptyList(),
    /** 列表页用的页数；完整加载时为 pages.size */
    val pageCount: Int = pages.size,
    /** 列表页用的封面缩略图；完整加载时取第一页缩略图 */
    val coverPath: String? = pages.firstOrNull()?.thumbnailPath
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

/** 裁剪模式：标准矩形、四角透视拉平、自由手绘套索 */
enum class CropStyle(val displayName: String) {
    RECTANGLE("矩形裁剪"),
    PERSPECTIVE("四角透视"),
    FREEFORM("手绘套索")
}

/** 矩形裁剪比例（像素宽/高） */
enum class CropAspect(val displayName: String, val widthOverHeight: Float?) {
    FREE("自由", null),
    SQUARE("1:1", 1f),
    R43("4:3", 4f / 3f),
    R169("16:9", 16f / 9f),
    A4("A4", 210f / 297f)
}

enum class WatermarkPosition(val displayName: String) {
    TOP_LEFT("左上"),
    TOP_RIGHT("右上"),
    BOTTOM_LEFT("左下"),
    BOTTOM_RIGHT("右下"),
    CENTER("居中")
}

enum class StitchMode(val displayName: String) {
    VERTICAL("纵向"),
    HORIZONTAL("横向"),
    GRID("网格")
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

/** 导出 PDF 时的纸张尺寸。ORIGINAL 表示页面尺寸等于图片像素尺寸（旧行为）。 */
enum class PdfPaperSize(val displayName: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
    ORIGINAL("按原图", 0, 0)
}

/** 文档库排序方式（收藏始终置顶） */
enum class DocumentSort(val displayName: String) {
    UPDATED("最近修改"),
    CREATED("创建时间"),
    NAME("名称")
}

/** 打码方式 */
enum class RedactStyle(val displayName: String) {
    BLACK("黑块"),
    MOSAIC("马赛克")
}

/** 表格识别结果（用于 Excel 导出） */
data class TableData(
    val rows: List<List<String>>
) {
    val rowCount: Int get() = rows.size
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0
}
