package com.scanner.offline.engine.ocr

import android.graphics.Bitmap
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.OcrResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OCR 引擎路由器。
 *
 * 路由策略：
 *  - PaddleOCR 已启用 → 中文使用 PaddleOCR；否则 MLKit 中文模型
 *  - 英文 / 拉丁文 → MLKit (速度快)
 *  - AUTO → 启发式：粗略统计像素特征不可靠，所以默认走 MLKit Latin，
 *    若结果文本主要为非 ASCII，再回退到中文识别一次（在 UI 层调用一次）
 */
@Singleton
class OcrEngineFactory @Inject constructor(
    private val mlKit: MlKitOcrEngine,
    private val paddle: PaddleOcrEngine
) {

    fun engineFor(language: Language): OcrEngine = when (language) {
        Language.CHINESE -> if (paddle.isEnabled) paddle else mlKit
        Language.ENGLISH, Language.LATIN -> mlKit
        Language.JAPANESE, Language.KOREAN -> mlKit  // 后续可替换为对应模型
        Language.AUTO -> mlKit
    }

    /**
     * 单次识别 + 自动语言切换。
     *
     * AUTO 策略：先用拉丁识别一次（速度快），通过启发式判断是否需要再用中文引擎跑一遍：
     *  1. 拉丁识别结果太短（<10 字符）—— 可能是漏识别中文
     *  2. 拉丁识别结果中非 ASCII 字符占比 > 50% —— 显然是中文/日文
     *
     * 满足任一条件就再跑一次中文识别，最终取文本更长的那个（更可能是正确结果）。
     */
    suspend fun recognize(bitmap: Bitmap, language: Language): OcrResult {
        if (language != Language.AUTO) {
            return engineFor(language).recognize(bitmap, language)
        }
        val latin = engineFor(Language.ENGLISH).recognize(bitmap, Language.ENGLISH)
        val text = latin.text
        val nonAsciiRatio = if (text.isEmpty()) 0f
        else text.count { it.code !in 32..126 } / text.length.toFloat()

        val likelyChinese = text.length < MIN_LATIN_LEN || nonAsciiRatio > 0.5f
        return if (likelyChinese) {
            val chinese = engineFor(Language.CHINESE).recognize(bitmap, Language.CHINESE)
            if (chinese.text.length >= latin.text.length) chinese else latin
        } else latin
    }

    private companion object {
        /** 拉丁识别结果短于此阈值时，触发中文兜底 */
        const val MIN_LATIN_LEN = 10
    }
}
