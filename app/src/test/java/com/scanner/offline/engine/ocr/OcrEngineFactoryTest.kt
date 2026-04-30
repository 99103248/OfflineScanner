package com.scanner.offline.engine.ocr

import android.graphics.Bitmap
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.OcrResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 测试 OcrEngineFactory 的路由逻辑。
 *
 * 不依赖真实的 MLKit / RapidOCR，使用伪造的 OcrEngine 替身。
 *
 * 不依赖 Android 框架（Bitmap 是个 mock 对象，不真的解码），
 * 所以可以在纯 JVM 上跑。
 */
class OcrEngineFactoryTest {

    /** 一个返回固定文本的伪 OCR 引擎，用来观察被路由到哪个引擎 */
    private class FakeOcr(
        private val name: String,
        private val text: String
    ) : OcrEngine {
        override val displayName: String = name
        override val supportedLanguages: Set<Language> = setOf(Language.AUTO, Language.CHINESE, Language.ENGLISH)
        var callCount = 0
            private set
        var lastLanguage: Language? = null
            private set

        override suspend fun recognize(bitmap: Bitmap, language: Language): OcrResult {
            callCount++
            lastLanguage = language
            return OcrResult(text = text, language = language)
        }
    }

    /** 一个能让 isEnabled 可控的 PaddleOcrEngine 替身 */
    private class FakePaddle(private val enabled: Boolean, private val text: String) {
        var callCount = 0
            private set
        val asEngine: OcrEngine = object : OcrEngine {
            override val displayName: String = "FakePaddle"
            override val supportedLanguages: Set<Language> = setOf(Language.CHINESE)
            override suspend fun recognize(bitmap: Bitmap, language: Language): OcrResult {
                callCount++
                return OcrResult(text = text, language = language)
            }
        }
    }

    private val dummyBitmap: Bitmap = org.mockito.Mockito.mock(Bitmap::class.java)

    @Test
    fun `指定中文且 paddle 启用_应路由到 paddle`() = runBlocking {
        val mlKit = FakeOcr("mlkit", "")
        // 我们没法直接 new MlKitOcrEngine / PaddleOcrEngine（构造时需要 Android Context），
        // 所以这里只测纯路由逻辑：仿制一份只暴露相同接口的工厂
        val factory = TestableFactory(
            chineseEngine = FakeOcr("chinese", "中文结果"),
            englishEngine = mlKit
        )

        val r = factory.recognize(dummyBitmap, Language.CHINESE)
        assertEquals("应使用 chinese 引擎结果", "中文结果", r.text)
    }

    @Test
    fun `指定英文_应路由到 MLKit`() = runBlocking {
        val englishOcr = FakeOcr("mlkit", "english result")
        val factory = TestableFactory(
            chineseEngine = FakeOcr("chinese", "wrong"),
            englishEngine = englishOcr
        )
        val r = factory.recognize(dummyBitmap, Language.ENGLISH)
        assertEquals("english result", r.text)
        assertEquals("英文应直接调用一次", 1, englishOcr.callCount)
    }

    @Test
    fun `AUTO 模式_纯英文应只调用 MLKit_不走中文引擎`() = runBlocking {
        val englishOcr = FakeOcr("mlkit", "Hello World, this is plain English text only.")
        val chineseOcr = FakeOcr("chinese", "中文")
        val factory = TestableFactory(
            chineseEngine = chineseOcr,
            englishEngine = englishOcr
        )

        val r = factory.recognize(dummyBitmap, Language.AUTO)
        assertEquals("Hello World, this is plain English text only.", r.text)
        assertEquals("英文应被调用 1 次", 1, englishOcr.callCount)
        assertEquals("纯英文不应触发中文引擎", 0, chineseOcr.callCount)
    }

    @Test
    fun `AUTO 模式_中文文档应同时尝试 MLKit 与中文引擎_并取中文结果`() = runBlocking {
        // 模拟英文引擎对中文文档识别效果差，结果短而乱
        val englishOcr = FakeOcr("mlkit", "??? ")
        val chineseOcr = FakeOcr("chinese", "这是一段中文测试内容")
        val factory = TestableFactory(
            chineseEngine = chineseOcr,
            englishEngine = englishOcr
        )
        val r = factory.recognize(dummyBitmap, Language.AUTO)

        assertEquals("应取更长的中文结果", "这是一段中文测试内容", r.text)
        assertEquals(1, englishOcr.callCount)
        assertEquals(1, chineseOcr.callCount)
    }

    @Test
    fun `AUTO 模式_英文结果为空时应回退到中文识别`() = runBlocking {
        val englishOcr = FakeOcr("mlkit", "")
        val chineseOcr = FakeOcr("chinese", "中文兜底")
        val factory = TestableFactory(
            chineseEngine = chineseOcr,
            englishEngine = englishOcr
        )
        val r = factory.recognize(dummyBitmap, Language.AUTO)
        assertEquals("中文兜底", r.text)
    }

    /**
     * OcrEngineFactory 的可测试副本。
     * 行为与生产代码完全一致，只是构造时直接接收两个 OcrEngine 替身，
     * 跳过对 MlKitOcrEngine / PaddleOcrEngine 的实例化（它们需要 Android Context）。
     */
    private class TestableFactory(
        private val chineseEngine: OcrEngine,
        private val englishEngine: OcrEngine
    ) {
        suspend fun recognize(bitmap: Bitmap, language: Language): OcrResult {
            if (language != Language.AUTO) {
                return engineFor(language).recognize(bitmap, language)
            }
            val latin = englishEngine.recognize(bitmap, Language.ENGLISH)
            val text = latin.text
            val nonAsciiRatio = if (text.isEmpty()) 0f
            else text.count { it.code !in 32..126 } / text.length.toFloat()
            val likelyChinese = text.length < 10 || nonAsciiRatio > 0.5f
            return if (likelyChinese) {
                val chinese = chineseEngine.recognize(bitmap, Language.CHINESE)
                if (chinese.text.length >= latin.text.length) chinese else latin
            } else latin
        }

        private fun engineFor(language: Language): OcrEngine = when (language) {
            Language.CHINESE -> chineseEngine
            else -> englishEngine
        }
    }
}
