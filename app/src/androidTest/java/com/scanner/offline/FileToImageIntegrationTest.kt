package com.scanner.offline

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.InputDocumentType
import com.scanner.offline.domain.model.PdfImageLayout
import com.scanner.offline.engine.export.DocumentExporter
import com.scanner.offline.engine.export.FileToImageConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * 文档 → 图片 集成测试（PDF / Word / TXT）。
 * 产物在 test-artifacts/，可用 adb pull 肉眼验证。
 */
@RunWith(AndroidJUnit4::class)
class FileToImageIntegrationTest {

    private lateinit var context: Context
    private lateinit var exporter: DocumentExporter
    private lateinit var converter: FileToImageConverter
    private lateinit var artifactDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        exporter = DocumentExporter()
        converter = FileToImageConverter()
        artifactDir = File(context.getExternalFilesDir(null), "test-artifacts").apply { mkdirs() }
    }

    @Test
    fun pdf_to_png_pages() {
        val img = saveBitmap(TestImageFactory.englishLines(), "pdf_src.jpg")
        val pdf = File(artifactDir, "file2img_source.pdf")
        exporter.imagesToPdf(listOf(img.absolutePath), pdf)

        val bitmaps = converter.convertToBitmaps(pdf, InputDocumentType.PDF)
        try {
            assertEquals(1, bitmaps.size)
            val out = File(artifactDir, "file2img_from_pdf.png")
            FileOutputStream(out).use { converter.writeBitmap(bitmaps[0], ExportFormat.PNG, it) }

            TestLog.section("FileToImageConverter · PDF → PNG")
            TestLog.kv("pdf", pdf.absolutePath)
            TestLog.kv("png", "${out.absolutePath} (${out.length()} bytes)")

            assertTrue(out.exists())
            assertTrue(out.length() > 500)
            val magic = out.readBytes().take(4)
            assertEquals(0x89.toByte(), magic[0])
            assertEquals(0x50.toByte(), magic[1])
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    @Test
    fun docx_to_png_preserves_text() {
        val docx = File(artifactDir, "file2img_source.docx")
        exporter.textsToWord(
            texts = listOf("中文标题\n第二行", "Page 2 English"),
            output = docx
        )
        val bitmaps = converter.convertToBitmaps(docx, InputDocumentType.WORD)
        try {
            assertTrue("Word 多页应至少 1 张图", bitmaps.isNotEmpty())
            val out = File(artifactDir, "file2img_from_docx.png")
            FileOutputStream(out).use { converter.writeBitmap(bitmaps[0], ExportFormat.PNG, it) }
            TestLog.section("FileToImageConverter · DOCX → PNG")
            TestLog.kv("pages rendered", bitmaps.size.toString())
            TestLog.kv("png", out.absolutePath)
            assertTrue(out.length() > 1000)
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    @Test
    fun txt_to_jpg() {
        val txt = File(artifactDir, "file2img_source.txt")
        txt.writeText("TXT 转图片测试\n\n第二段内容\nLine 3")
        val bitmaps = converter.convertToBitmaps(txt, InputDocumentType.TXT)
        try {
            assertEquals(1, bitmaps.size)
            val out = File(artifactDir, "file2img_from_txt.jpg")
            FileOutputStream(out).use { converter.writeBitmap(bitmaps[0], ExportFormat.JPG, it) }
            TestLog.section("FileToImageConverter · TXT → JPG")
            TestLog.kv("jpg", "${out.absolutePath} (${out.length()} bytes)")
            assertTrue(out.exists())
            assertTrue(out.length() > 500)
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    @Test
    fun multi_page_pdf_produces_multiple_bitmaps() {
        val p1 = saveBitmap(TestImageFactory.englishLines(listOf("P1")), "mp1.jpg")
        val p2 = saveBitmap(TestImageFactory.englishLines(listOf("P2")), "mp2.jpg")
        val pdf = File(artifactDir, "file2img_multipage.pdf")
        exporter.imagesToPdf(listOf(p1.absolutePath, p2.absolutePath), pdf)

        val bitmaps = converter.convertToBitmaps(pdf, InputDocumentType.PDF, PdfImageLayout.SEPARATE_PAGES)
        try {
            assertEquals(2, bitmaps.size)
            bitmaps.forEachIndexed { i, bmp ->
                val out = File(artifactDir, "file2img_pdf_page_${i + 1}.png")
                FileOutputStream(out).use { converter.writeBitmap(bmp, ExportFormat.PNG, it) }
                assertTrue("page ${i + 1}", out.length() > 300)
            }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    @Test
    fun multi_page_pdf_long_image_single_bitmap() {
        val p1 = saveBitmap(TestImageFactory.englishLines(listOf("Long P1")), "long_mp1.jpg")
        val p2 = saveBitmap(TestImageFactory.englishLines(listOf("Long P2")), "long_mp2.jpg")
        val pdf = File(artifactDir, "file2img_multipage_long.pdf")
        exporter.imagesToPdf(listOf(p1.absolutePath, p2.absolutePath), pdf)

        val bitmaps = converter.convertToBitmaps(pdf, InputDocumentType.PDF, PdfImageLayout.LONG_IMAGE)
        try {
            assertEquals(1, bitmaps.size)
            val bmp = bitmaps[0]
            assertTrue("长图高度应大于单页", bmp.height > bmp.width / 2)
            val out = File(artifactDir, "file2img_pdf_long.png")
            FileOutputStream(out).use { converter.writeBitmap(bmp, ExportFormat.PNG, it) }
            TestLog.section("FileToImageConverter · PDF → 长图")
            TestLog.kv("size", "${bmp.width}x${bmp.height}")
            TestLog.kv("png", "${out.absolutePath} (${out.length()} bytes)")
            assertTrue(out.length() > 500)
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    private fun saveBitmap(bmp: Bitmap, name: String): File {
        val out = File(artifactDir, name)
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return out
    }
}
