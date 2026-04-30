package com.scanner.offline.engine.ocr

import android.graphics.Bitmap
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.OcrResult

/**
 * OCR 引擎抽象接口。
 *
 * 扩展新语言只需要新建一个实现类（或扩展现有实现的支持集），
 * 然后在 [OcrEngineFactory] 中决定路由策略，UI 完全不用改。
 */
interface OcrEngine {

    /** 该引擎能识别的语言 */
    val supportedLanguages: Set<Language>

    /** 引擎名称（仅用于调试 / 设置页展示） */
    val displayName: String

    /**
     * 对一张图片做识别。
     *
     * @param bitmap 待识别图
     * @param language 识别语言；当传 [Language.AUTO] 时，由实现自行判定
     */
    suspend fun recognize(bitmap: Bitmap, language: Language): OcrResult
}
