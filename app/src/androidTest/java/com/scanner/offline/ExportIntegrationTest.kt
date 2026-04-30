package com.scanner.offline

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.TableData
import com.scanner.offline.engine.export.DocumentExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * 真实导出引擎集成测试。
 *
 * 把测试图片 → PDF / Word / Excel 全跑一遍，
 * 产物落到 `getExternalFilesDir(null)/test-artifacts/`，
 * 测试结束后可以 `adb pull` 出来用 Word/Acrobat/Excel 验证。
 */
@RunWith(AndroidJUnit4::class)
class ExportIntegrationTest {

    private lateinit var context: Context
    private lateinit var exporter: DocumentExporter
    private lateinit var artifactDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        exporter = DocumentExporter()
        artifactDir = File(context.getExternalFilesDir(null), "test-artifacts").apply { mkdirs() }
    }

    @Test
    fun images_to_pdf_produces_valid_pdf() {
        // 1. 准备 2 页图片，写到磁盘
        val page1 = TestImageFactory.englishLines(
            lines = listOf("Page 1 - Hello World", "OfflineScanner Demo")
        )
        val page2 = TestImageFactory.chineseLines(
            lines = listOf("第二页", "中文文档", "二零二六")
        )
        val img1 = saveBitmap(page1, "img_page1.jpg")
        val img2 = saveBitmap(page2, "img_page2.jpg")

        // 2. 调用真实导出器
        val pdfOut = File(artifactDir, "exported.pdf")
        exporter.imagesToPdf(listOf(img1.absolutePath, img2.absolutePath), pdfOut)

        // 3. 验证产物
        TestLog.section("DocumentExporter · imagesToPdf")
        TestLog.kv("input pages", "2")
        TestLog.kv("output path", pdfOut.absolutePath)
        TestLog.kv("output size", "${pdfOut.length()} bytes")

        assertTrue("PDF 文件应该已生成", pdfOut.exists())
        assertTrue("PDF 应有内容（>1KB）", pdfOut.length() > 1024)

        // PDF 文件以 "%PDF" 开头
        val header = pdfOut.readBytes().take(4).toByteArray().toString(Charsets.US_ASCII)
        assertEquals("PDF 文件头应为 %PDF, 实际: '$header'", "%PDF", header)
    }

    @Test
    fun texts_to_word_produces_valid_docx() {
        val docxOut = File(artifactDir, "exported.docx")
        exporter.textsToWord(
            texts = listOf(
                "第一页文字\n这是一段中文\n2026 年 4 月 29 日",
                "Page Two\nEnglish content here\nLine 3"
            ),
            output = docxOut
        )

        TestLog.section("DocumentExporter · textsToWord")
        TestLog.kv("output", docxOut.absolutePath)
        TestLog.kv("size", "${docxOut.length()} bytes")

        assertTrue("docx 应该已生成", docxOut.exists())
        assertTrue("docx 不应为空", docxOut.length() > 1024)

        // docx 是 zip，应该包含 word/document.xml 且能用 DOM 解析
        ZipFile(docxOut).use { zip ->
            val names = zip.entries().toList().map { it.name }
            TestLog.kv("docx parts", names.joinToString())
            assertTrue("应包含 word/document.xml", "word/document.xml" in names)

            val xml = zip.getInputStream(zip.getEntry("word/document.xml"))
                .bufferedReader().readText()
            assertTrue("应包含中文内容", xml.contains("第一页文字"))
            assertTrue("应包含英文内容", xml.contains("English content"))

            // DOM 解析校验合法性
            javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }.newDocumentBuilder().parse(xml.byteInputStream())
        }
    }

    @Test
    fun table_to_excel_produces_valid_xlsx() {
        val xlsxOut = File(artifactDir, "exported.xlsx")
        val tables = listOf(
            TableData(
                rows = listOf(
                    listOf("姓名", "年龄", "城市"),
                    listOf("张三", "28", "上海"),
                    listOf("李四", "32", "北京"),
                    listOf("Wang", "25", "Shanghai")
                )
            )
        )
        exporter.tableToExcel(tables, xlsxOut)

        TestLog.section("DocumentExporter · tableToExcel")
        TestLog.kv("output", xlsxOut.absolutePath)
        TestLog.kv("size", "${xlsxOut.length()} bytes")
        TestLog.kv("rows", "4")
        TestLog.kv("cols", "3")

        assertTrue("xlsx 应该已生成", xlsxOut.exists())
        assertTrue("xlsx 不应为空", xlsxOut.length() > 1024)

        ZipFile(xlsxOut).use { zip ->
            val names = zip.entries().toList().map { it.name }
            TestLog.kv("xlsx parts", names.joinToString())
            assertTrue("应有 xl/workbook.xml", "xl/workbook.xml" in names)
            assertTrue("应有 xl/worksheets/sheet1.xml", "xl/worksheets/sheet1.xml" in names)
            assertTrue("应有 xl/sharedStrings.xml", "xl/sharedStrings.xml" in names)

            // 验证中文内容已写入 sharedStrings
            val ss = zip.getInputStream(zip.getEntry("xl/sharedStrings.xml"))
                .bufferedReader().readText()
            assertTrue("sharedStrings 应包含'张三'", ss.contains("张三"))
            assertTrue("sharedStrings 应包含'Shanghai'", ss.contains("Shanghai"))

            // 验证主要 XML parts 都能 DOM 解析
            listOf("xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/sharedStrings.xml",
                "xl/styles.xml", "[Content_Types].xml", "_rels/.rels", "xl/_rels/workbook.xml.rels"
            ).forEach { part ->
                val xml = zip.getInputStream(zip.getEntry(part)).bufferedReader().readText()
                javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                }.newDocumentBuilder().parse(xml.byteInputStream())
            }
        }
    }

    @Test
    fun convert_image_jpeg_to_png() {
        val src = saveBitmap(
            TestImageFactory.englishLines(),
            "convert_src.jpg",
            format = Bitmap.CompressFormat.JPEG
        )
        val pngOut = File(artifactDir, "converted.png")
        exporter.convertImage(src.absolutePath, ExportFormat.PNG, pngOut)

        TestLog.section("DocumentExporter · convertImage JPG -> PNG")
        TestLog.kv("source", "${src.length()} bytes (JPG)")
        TestLog.kv("target", "${pngOut.length()} bytes (PNG)")

        assertTrue("PNG 输出应存在", pngOut.exists())
        assertTrue("PNG 应有内容", pngOut.length() > 1024)
        // PNG magic bytes: 89 50 4E 47
        val firstBytes = pngOut.readBytes().take(4).toByteArray()
        assertEquals(0x89.toByte(), firstBytes[0])
        assertEquals(0x50.toByte(), firstBytes[1])
        assertEquals(0x4E.toByte(), firstBytes[2])
        assertEquals(0x47.toByte(), firstBytes[3])
    }

    @Test
    fun texts_to_pdf_a4_render() {
        val pdfOut = File(artifactDir, "exported_text.pdf")
        exporter.textsToPdf(
            texts = listOf(
                "OCR 测试结果\n\n这是一段示例中文文字。\nLine 2 with mixed 中英 content.",
                "Second page\nMore content here."
            ),
            output = pdfOut
        )

        TestLog.section("DocumentExporter · textsToPdf (A4)")
        TestLog.kv("output", pdfOut.absolutePath)
        TestLog.kv("size", "${pdfOut.length()} bytes")

        assertTrue(pdfOut.exists())
        assertTrue(pdfOut.length() > 512)
        val header = pdfOut.readBytes().take(4).toByteArray().toString(Charsets.US_ASCII)
        assertEquals("%PDF", header)
    }

    private fun saveBitmap(
        bmp: Bitmap,
        name: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): File {
        val out = File(artifactDir, name)
        FileOutputStream(out).use { bmp.compress(format, 92, it) }
        return out
    }
}
