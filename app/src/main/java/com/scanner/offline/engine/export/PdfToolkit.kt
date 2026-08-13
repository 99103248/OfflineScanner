package com.scanner.offline.engine.export

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.OutputStream

/**
 * 离线 PDF 合并 / 拆分 / 抽页。
 * 通过系统 [PdfRenderer] 渲染为位图再写入 [PdfDocument]，不引入 iText。
 */
class PdfToolkit {

    fun pageCount(file: File): Int {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { return it.pageCount }
        }
    }

    fun renderPages(file: File): List<Bitmap> {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                return (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(
                            page.width.coerceAtLeast(1),
                            page.height.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }
    }

    fun writeBitmapsAsPdf(pages: List<Bitmap>, output: OutputStream) {
        require(pages.isNotEmpty()) { "没有可写入的页面" }
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, bmp ->
                val info = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, index + 1).create()
                val page = pdf.startPage(info)
                page.canvas.drawBitmap(bmp, 0f, 0f, null)
                pdf.finishPage(page)
            }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    fun merge(files: List<File>, output: OutputStream) {
        val pages = mutableListOf<Bitmap>()
        try {
            files.forEach { pages += renderPages(it) }
            writeBitmapsAsPdf(pages, output)
        } finally {
            pages.forEach { it.recycle() }
        }
    }

    fun extract(file: File, pageIndices0: List<Int>, output: OutputStream) {
        val all = renderPages(file)
        try {
            val picked = pageIndices0.mapNotNull { i -> all.getOrNull(i) }
            require(picked.isNotEmpty()) { "没有选中的页" }
            writeBitmapsAsPdf(picked, output)
        } finally {
            all.forEach { it.recycle() }
        }
    }
}
