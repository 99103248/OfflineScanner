package com.scanner.offline.engine.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.scanner.offline.domain.model.BoundingBox
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.OcrResult
import com.scanner.offline.domain.model.TextBlock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hzkitty.RapidOCR
import io.github.hzkitty.entity.RecResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis

/**
 * 基于 RapidOCR (PaddleOCR PP-OCRv4 模型 + ONNX Runtime) 的离线中文 OCR 引擎。
 *
 * 优势：
 *  - 完全离线，模型已经打包在 aar 内（无需用户下载）
 *  - 中文识别效果显著优于 MLKit
 *  - 同时支持中英文混排
 *  - 提供检测框 + 置信度 + 完整文本
 *
 * 注意：
 *  - 引擎首次创建较慢（要加载 ~15MB 模型 + ONNX Runtime），所以做成 Singleton + 懒加载
 *  - run() 不是线程安全的，用 Mutex 串行化
 */
@Singleton
class PaddleOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : OcrEngine {

    override val displayName: String = "RapidOCR (PaddleOCR PP-OCRv4)"

    override val supportedLanguages: Set<Language> = setOf(
        Language.CHINESE,
        Language.ENGLISH,
        Language.AUTO
    )

    /** 总开关：构造失败（极端情况）时降级 */
    val isEnabled: Boolean get() = ocr != null

    private var ocr: RapidOCR? = null

    /** 串行化推理调用 */
    private val mutex = Mutex()

    init {
        ocr = runCatching {
            RapidOCR.create(context)
        }.onFailure {
            Log.e(TAG, "RapidOCR 初始化失败：${it.message}", it)
        }.getOrNull()
        Log.i(TAG, "RapidOCR 初始化 ${if (ocr != null) "成功" else "失败"}")
    }

    override suspend fun recognize(bitmap: Bitmap, language: Language): OcrResult =
        withContext(Dispatchers.Default) {
            val engine = ocr ?: throw IllegalStateException(
                "RapidOCR 未初始化成功，请检查日志或降级到 MLKit"
            )

            mutex.withLock {
                var raw: io.github.hzkitty.entity.OcrResult
                val cost = measureTimeMillis {
                    raw = engine.run(bitmap)
                }
                val text = raw.strRes.orEmpty().trim()
                val blocks: List<TextBlock> = raw.recRes.orEmpty().map { it.toTextBlock() }

                OcrResult(
                    text = text,
                    language = language,
                    blocks = blocks,
                    processingTimeMs = cost
                )
            }
        }

    private fun RecResult.toTextBlock(): TextBlock = TextBlock(
        text = text.orEmpty(),
        confidence = confidence,
        boundingBox = dtBoxes?.takeIf { it.isNotEmpty() }?.let { pts ->
            // dtBoxes 是 4 个角点（左上、右上、右下、左下），取外接矩形
            val xs = pts.map { it.x }
            val ys = pts.map { it.y }
            BoundingBox(
                left = xs.min().toInt(),
                top = ys.min().toInt(),
                right = xs.max().toInt(),
                bottom = ys.max().toInt()
            )
        }
    )

    @Suppress("unused")
    private fun unused() {
        max(0, 0); min(0, 0)
    }

    companion object {
        private const val TAG = "PaddleOcrEngine"
    }
}
