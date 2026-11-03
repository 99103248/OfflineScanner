package com.scanner.offline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scanner.offline.engine.export.DocumentExporter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.InflaterInputStream

/**
 * 可搜索 PDF 集成测试：
 *  1. 导出的 PDF 内容流中真实包含 OCR 文字（可全文检索）
 *  2. 图片页仍然渲染正常
 *
 * Android PdfDocument 会把文字写成字体子集 + 字形 ID（`<2D> Tj`），
 * 同时附带 ToUnicode CMap。测试通过解码 CMap 还原文字，验证可检索性。
 */
@RunWith(AndroidJUnit4::class)
class SearchablePdfTest {

    private fun makeBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.WHITE)
        return bmp
    }

    @Test
    fun searchable_pdf_embeds_invisible_text_layer() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "searchable-test").apply { mkdirs() }

        val img1 = File(dir, "p1.png")
        makeBitmap(200, 300).let { bmp ->
            FileOutputStream(img1).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }

        val buffer = java.io.ByteArrayOutputStream()
        DocumentExporter().imagesToSearchablePdf(
            imagePaths = listOf(img1.absolutePath),
            pageTexts = listOf("HELLOSEARCH quarterly invoice total 42.00"),
            output = buffer
        )
        val bytes = buffer.toByteArray()

        // 落盘一份产物，便于 adb pull 排查
        val artifact = File(
            context.getExternalFilesDir(null) ?: context.cacheDir,
            "searchable-test/searchable.pdf"
        ).apply { parentFile?.mkdirs() }
        artifact.writeBytes(bytes)
        TestLog.kv("searchable.pdf", "${bytes.size} bytes → ${artifact.absolutePath}")

        assertTrue("PDF 应为合法头", bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-")

        val streams = extractFlateStreams(bytes)
        val cmap = streams.firstOrNull { it.contains("beginbfchar") || it.contains("beginbfrange") }
            ?: error("PDF 中未找到 ToUnicode CMap，文字无法被检索")
        val mapping = parseToUnicode(cmap)
        val content = streams.firstOrNull { it.contains("BT") && it.contains("Tj") }
            ?: error("PDF 中未找到文字内容流")

        val decoded = Regex("<([0-9A-Fa-f]+)>\\s*Tj")
            .findAll(content)
            .mapNotNull { match ->
                val code = match.groupValues[1].toIntOrNull(16) ?: return@mapNotNull null
                mapping[code]
            }
            .joinToString("")

        TestLog.kv("decoded text", decoded)
        assertTrue(
            "从 PDF 文字层解码出的内容应包含 HELLOSEARCH，实际：$decoded",
            decoded.contains("HELLOSEARCH")
        )
    }

    @Test
    fun plain_pdf_does_not_embed_text() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "searchable-test").apply { mkdirs() }

        val img = File(dir, "p2.png")
        makeBitmap(120, 160).let { bmp ->
            FileOutputStream(img).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }

        val buffer = java.io.ByteArrayOutputStream()
        DocumentExporter().imagesToPdf(listOf(img.absolutePath), buffer)
        val streams = extractFlateStreams(buffer.toByteArray())
        assertTrue(
            "普通 PDF 不应包含文字内容流",
            streams.none { it.contains("BT") && it.contains("Tj") }
        )
    }

    /** 解压 PDF 里所有 FlateDecode 流。 */
    private fun extractFlateStreams(bytes: ByteArray): List<String> {
        val raw = bytes.toString(Charsets.ISO_8859_1)
        val result = mutableListOf<String>()
        Regex("stream\\r?\\n").findAll(raw).forEach { match ->
            val start = match.range.last + 1
            val end = raw.indexOf("endstream", start)
            if (end < 0) return@forEach
            val chunk = raw.substring(start, end).toByteArray(Charsets.ISO_8859_1)
            runCatching {
                InflaterInputStream(ByteArrayInputStream(chunk)).use { it.readBytes() }
                    .toString(Charsets.ISO_8859_1)
            }.onSuccess { result += it }
        }
        return result
    }

    /** 解析 ToUnicode CMap 中的 bfchar / bfrange。 */
    private fun parseToUnicode(cmap: String): Map<Int, Char> {
        val map = mutableMapOf<Int, Char>()
        var mode = 0 // 1 = bfchar, 2 = bfrange
        cmap.lines().forEach { line ->
            when {
                line.contains("beginbfchar") -> mode = 1
                line.contains("endbfchar") -> mode = 0
                line.contains("beginbfrange") -> mode = 2
                line.contains("endbfrange") -> mode = 0
                mode == 1 -> {
                    val parts = hexValues(line)
                    if (parts.size == 2) map[parts[0]] = parts[1].toChar()
                }
                mode == 2 -> {
                    val parts = hexValues(line)
                    if (parts.size == 3) {
                        for (code in parts[0]..parts[1]) {
                            map[code] = (parts[2] + (code - parts[0])).toChar()
                        }
                    }
                }
            }
        }
        return map
    }

    private fun hexValues(line: String): List<Int> =
        Regex("<([0-9A-Fa-f]+)>").findAll(line)
            .mapNotNull { it.groupValues[1].toIntOrNull(16) }
            .toList()
}
