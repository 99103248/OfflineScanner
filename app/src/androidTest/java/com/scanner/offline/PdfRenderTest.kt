package com.scanner.offline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * 用 Android 系统自带的 PdfRenderer 把 exported.pdf 渲染成 PNG，
 * 这样 adb pull 出来直接能肉眼看到 PDF 渲染结果。
 *
 * 这是对 imagesToPdf 测试的"端到端可视化"补充。
 */
@RunWith(AndroidJUnit4::class)
class PdfRenderTest {

    @Test
    fun render_exported_pdf_to_png() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val artifactDir = File(context.getExternalFilesDir(null), "test-artifacts")
        val pdf = File(artifactDir, "exported.pdf")

        if (!pdf.exists()) {
            TestLog.section("PDF render skipped — exported.pdf not found")
            return
        }

        TestLog.section("Rendering exported.pdf via Android PdfRenderer")
        val pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(pfd).use { renderer ->
            TestLog.kv("page count", renderer.pageCount.toString())
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val w = page.width
                    val h = page.height
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out = File(artifactDir, "rendered_page_${i + 1}.png")
                    FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    TestLog.kv("page ${i + 1}", "${w}x$h px → $out")
                    assertTrue("page $i rendered", out.length() > 100)
                    bmp.recycle()
                }
            }
        }
        pfd.close()
    }
}
