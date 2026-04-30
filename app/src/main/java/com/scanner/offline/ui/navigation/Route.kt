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
    /** 拍照 */
    data object Camera : AppRoute("camera")

    /** 裁剪 / 边缘检测，传入图片 URI */
    data object Crop : AppRoute("crop?imageUri={imageUri}") {
        fun create(imageUri: String) =
            "crop?imageUri=${java.net.URLEncoder.encode(imageUri, "UTF-8")}"
    }

    /** 滤镜，传入裁剪后图片路径 */
    data object Filter : AppRoute("filter?imagePath={imagePath}") {
        fun create(imagePath: String) =
            "filter?imagePath=${java.net.URLEncoder.encode(imagePath, "UTF-8")}"
    }

    /** OCR 识别 */
    data object Ocr : AppRoute("ocr?imagePath={imagePath}&docId={docId}") {
        fun create(imagePath: String, docId: Long? = null) =
            "ocr?imagePath=${java.net.URLEncoder.encode(imagePath, "UTF-8")}&docId=${docId ?: -1}"
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
}
