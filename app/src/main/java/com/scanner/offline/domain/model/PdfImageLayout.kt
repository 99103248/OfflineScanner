package com.scanner.offline.domain.model

/** 多页 PDF 转图片时的排版方式 */
enum class PdfImageLayout(val displayName: String) {
    SEPARATE_PAGES("多张图片"),
    LONG_IMAGE("一张长图")
}
