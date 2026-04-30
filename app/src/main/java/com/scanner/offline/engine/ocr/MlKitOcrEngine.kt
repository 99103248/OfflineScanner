package com.scanner.offline.engine.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.scanner.offline.domain.model.BoundingBox
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.OcrResult
import com.scanner.offline.domain.model.TextBlock
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.system.measureTimeMillis

/**
 * Google MLKit 离线 OCR 实现。
 *
 * 优势：
 *  - 完全离线，模型由 Google Play 服务静态下发
 *  - 体积小（~10MB）
 *  - 英文 / 拉丁文识别速度快、准确率高
 *
 * 不足：
 *  - 中文识别准确率不如 PaddleOCR，所以中文优先走 [PaddleOcrEngine]
 *
 * 注意：`com.google.mlkit:text-recognition-chinese` 提供中文模型，
 *      可以作为没有 PaddleOCR 时的兜底中文实现。
 */
@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {

    override val displayName: String = "MLKit"

    override val supportedLanguages: Set<Language> = setOf(
        Language.AUTO,
        Language.ENGLISH,
        Language.CHINESE,
        Language.LATIN
    )

    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override suspend fun recognize(bitmap: Bitmap, language: Language): OcrResult {
        val recognizer = when (language) {
            Language.CHINESE -> chineseRecognizer
            Language.ENGLISH, Language.LATIN -> latinRecognizer
            else -> latinRecognizer
        }

        var result: OcrResult
        val cost = measureTimeMillis {
            result = doRecognize(recognizer, bitmap, language)
        }
        return result.copy(processingTimeMs = cost)
    }

    private suspend fun doRecognize(
        recognizer: TextRecognizer,
        bitmap: Bitmap,
        language: Language
    ): OcrResult = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val blocks = mutableListOf<TextBlock>()
                visionText.textBlocks.forEach { block ->
                    val rect = block.boundingBox
                    blocks += TextBlock(
                        text = block.text,
                        confidence = 1f,
                        boundingBox = rect?.let {
                            BoundingBox(it.left, it.top, it.right, it.bottom)
                        }
                    )
                }
                cont.resume(
                    OcrResult(
                        text = visionText.text,
                        language = language,
                        blocks = blocks
                    )
                )
            }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }
}
