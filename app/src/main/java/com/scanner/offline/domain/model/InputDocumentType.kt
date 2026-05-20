package com.scanner.offline.domain.model

/** 文件转图片时支持的输入类型 */
enum class InputDocumentType(val displayName: String) {
    PDF("PDF"),
    WORD("Word"),
    TXT("文本"),
    IMAGE("图片")
}
