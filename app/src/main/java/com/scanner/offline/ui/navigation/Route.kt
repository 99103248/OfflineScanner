package com.scanner.offline.ui.navigation

/**
 * 顶层 Tab 路由（带底部导航的）
 */
sealed class TopRoute(val path: String) {
    data object Home : TopRoute("home")
    data object Tools : TopRoute("tools")
    data object Me : TopRoute("me")
}

/**
 * 二级路由（不带底部导航）
 *
 * 路径中的 {imageUri} 等是占位参数，跳转时拼上实际值。
 */
sealed class AppRoute(val path: String) {
    /** 拍照；docId >= 0 表示把拍到的页面追加到已有文档 */
    data object Camera : AppRoute("camera?docId={docId}") {
        fun create(docId: Long? = null) = "camera?docId=${docId ?: -1}"
    }

    /** 裁剪 / 边缘检测，传入图片 URI；docId >= 0 表示追加到已有文档 */
    data object Crop : AppRoute("crop?imageUri={imageUri}&docId={docId}") {
        fun create(imageUri: String, docId: Long? = null) =
            "crop?imageUri=${java.net.URLEncoder.encode(imageUri, "UTF-8")}&docId=${docId ?: -1}"
    }

    /** 滤镜，传入裁剪后图片路径；docId >= 0 表示追加到已有文档 */
    data object Filter : AppRoute("filter?imagePath={imagePath}&docId={docId}") {
        fun create(imagePath: String, docId: Long? = null) =
            "filter?imagePath=${java.net.URLEncoder.encode(imagePath, "UTF-8")}&docId=${docId ?: -1}"
    }

    /** OCR 识别 */
    data object Ocr : AppRoute("ocr?imagePath={imagePath}&docId={docId}&pageId={pageId}") {
        fun create(imagePath: String, docId: Long? = null, pageId: Long? = null) =
            "ocr?imagePath=${java.net.URLEncoder.encode(imagePath, "UTF-8")}&docId=${docId ?: -1}&pageId=${pageId ?: -1}"
    }

    /** 文档详情 */
    data object DocumentDetail : AppRoute("document/{docId}") {
        fun create(docId: Long) = "document/$docId"
    }

    /** 导出选择 */
    data object Export : AppRoute("export/{docId}") {
        fun create(docId: Long) = "export/$docId"
    }

    /** 图片格式转换 */
    data object FormatConvert : AppRoute("format")

    /** 矩形裁剪 / 翻转另存为 */
    data object ImageEdit : AppRoute("imageEdit?imageUri={imageUri}&mode={mode}&pageId={pageId}") {
        fun create(imageUri: String, mode: String, pageId: Long? = null) =
            "imageEdit?imageUri=${java.net.URLEncoder.encode(imageUri, "UTF-8")}&mode=$mode&pageId=${pageId ?: -1}"
    }

    data object Burst : AppRoute("burst")
    data object Batch : AppRoute("batch")
    data object Stitch : AppRoute("stitch")
    data object PdfTools : AppRoute("pdfTools")
    data object Barcode : AppRoute("barcode")
    data object IdCard : AppRoute("idCard")
    data object Settings : AppRoute("settings")

    /** 回收站 */
    data object Trash : AppRoute("trash")
}
