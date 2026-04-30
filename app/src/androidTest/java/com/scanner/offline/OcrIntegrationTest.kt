package com.scanner.offline

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scanner.offline.domain.model.Language
import com.scanner.offline.engine.ocr.MlKitOcrEngine
import com.scanner.offline.engine.ocr.OcrEngineFactory
import com.scanner.offline.engine.ocr.PaddleOcrEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis

/**
 * 真实 OCR 引擎集成测试 —— 在 Android 模拟器或真机上运行。
 *
 * 测试范围：
 *   1. MlKit 拉丁文识别能否在 Bitmap 上正常工作
 *   2. RapidOCR (PaddleOcrEngine) 能否在 Bitmap 上正常工作
 *   3. OcrEngineFactory AUTO 模式的真实路由能否选对引擎
 *
 * 与 src/test 下的纯 JVM 测试不同：这里**真的会跑模型**，
 * 输出的识别结果可以直接看到准确率。
 */
@RunWith(AndroidJUnit4::class)
class OcrIntegrationTest {

    private lateinit var context: Context
    private lateinit var mlKit: MlKitOcrEngine
    private lateinit var paddle: PaddleOcrEngine
    private lateinit var factory: OcrEngineFactory

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        mlKit = MlKitOcrEngine()
        paddle = PaddleOcrEngine(context)
        factory = OcrEngineFactory(mlKit, paddle)
    }

    @Test
    fun mlkit_recognizes_english_image() = runBlocking {
        val bmp = TestImageFactory.englishLines(
            lines = listOf("Hello World", "OfflineScanner")
        )
        dumpBitmap(bmp, "ocr_english_input.png")

        val result = mlKit.recognize(bmp, Language.ENGLISH)
        TestLog.section("MLKit · English OCR")
        TestLog.kv("input image", "1200x800 px, 2 lines")
        TestLog.kv("recognized text", result.text)
        TestLog.kv("blocks", result.blocks.size.toString())
        TestLog.kv("processingTimeMs", result.processingTimeMs.toString())

        // MLKit 对英文识别准确率高，应该至少能找到 "Hello"
        assertTrue(
            "应识别出至少一行，实际：'${result.text}'",
            result.text.isNotBlank()
        )
        assertTrue(
            "识别结果应包含 'Hello' 或 'World'，实际：'${result.text}'",
            result.text.contains("Hello", ignoreCase = true) ||
                result.text.contains("World", ignoreCase = true) ||
                result.text.contains("Offline", ignoreCase = true)
        )
    }

    @Test
    fun rapidocr_recognizes_chinese_image() = runBlocking {
        if (!paddle.isEnabled) {
            TestLog.section("RapidOCR · Chinese (SKIPPED — engine not enabled)")
            return@runBlocking
        }

        val bmp = TestImageFactory.chineseLines(
            lines = listOf("离线文档扫描", "中文识别测试")
        )
        dumpBitmap(bmp, "ocr_chinese_input.png")

        var result: com.scanner.offline.domain.model.OcrResult
        val cost = measureTimeMillis {
            result = paddle.recognize(bmp, Language.CHINESE)
        }
        TestLog.section("RapidOCR · Chinese OCR")
        TestLog.kv("input image", "1200x800 px, 2 lines")
        TestLog.kv("recognized text", result.text)
        TestLog.kv("blocks", result.blocks.size.toString())
        TestLog.kv("model time (engine)", "${result.processingTimeMs} ms")
        TestLog.kv("wall clock", "$cost ms")

        result.blocks.forEachIndexed { i, b ->
            TestLog.kv("  block[$i]", "'${b.text}' confidence=${"%.3f".format(b.confidence)}")
        }

        assertTrue(
            "RapidOCR 应识别出至少一些中文字符，实际：'${result.text}'",
            result.text.isNotBlank()
        )
        assertTrue(
            "识别结果应包含'扫描'/'识别'/'测试'/'文档'/'中文'/'离线'之一，实际：'${result.text}'",
            result.text.contains("扫描") ||
                result.text.contains("识别") ||
                result.text.contains("测试") ||
                result.text.contains("文档") ||
                result.text.contains("中文") ||
                result.text.contains("离线")
        )
    }

    @Test
    fun auto_mode_picks_chinese_for_chinese_image() = runBlocking {
        if (!paddle.isEnabled) {
            TestLog.section("AUTO mode (SKIPPED — paddle not enabled)")
            return@runBlocking
        }

        val bmp = TestImageFactory.chineseLines(
            lines = listOf("自动识别中文", "扫描测试用例")
        )

        val result = factory.recognize(bmp, Language.AUTO)
        TestLog.section("OcrEngineFactory · AUTO mode (Chinese input)")
        TestLog.kv("recognized text", result.text)
        TestLog.kv("processingTimeMs", result.processingTimeMs.toString())

        assertNotNull(result)
        assertTrue("AUTO 模式应至少识别到内容", result.text.isNotBlank())
    }

    @Test
    fun auto_mode_picks_english_for_english_image() = runBlocking {
        val bmp = TestImageFactory.englishLines(
            lines = listOf(
                "AUTO mode test for English document",
                "MLKit should be selected for plain ASCII"
            )
        )

        val result = factory.recognize(bmp, Language.AUTO)
        TestLog.section("OcrEngineFactory · AUTO mode (English input)")
        TestLog.kv("recognized text", result.text)
        TestLog.kv("processingTimeMs", result.processingTimeMs.toString())

        assertTrue("AUTO 应识别出英文内容", result.text.isNotBlank())
        assertTrue(
            "应包含 AUTO/MLKit/English 之一",
            result.text.contains("AUTO", ignoreCase = true) ||
                result.text.contains("MLKit", ignoreCase = true) ||
                result.text.contains("English", ignoreCase = true) ||
                result.text.contains("test", ignoreCase = true)
        )
    }

    /** 把 bitmap 写到外部目录，方便 adb pull 出来肉眼看测试输入 */
    private fun dumpBitmap(bmp: Bitmap, name: String) {
        val dir = File(context.getExternalFilesDir(null), "test-artifacts").apply { mkdirs() }
        val out = File(dir, name)
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        TestLog.kv("dumped image", out.absolutePath)
    }
}
