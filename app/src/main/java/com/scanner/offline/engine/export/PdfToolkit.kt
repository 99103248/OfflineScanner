package com.scanner.offline.engine.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 离线 PDF 合并 / 拆分 / 抽页。
 *
 * 采用逐页流式渲染与写入（渲染一页 → 写入 PdfDocument → 立即 recycle），
 * 避免多页 PDF 一次性解码全部 Bitmap 造成 OOM；同时以 2x 分辨率（上限 2200px）
 * 渲染并预填白底，保证合并/拆页后的清晰度与背景正确。
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
                    renderer.openPage(i).use { page -> renderSinglePage(page) }
                }
            }
        }
    }

    /** 流式拆分：每渲染一页立即交给回调写出并回收，峰值内存仅占一页。 */
    fun splitStreaming(file: File, onPage: (pageIndex: Int, bitmap: Bitmap) -> Unit): Int {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = renderer.pageCount
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        val bmp = renderSinglePage(page)
                        try {
                            onPage(i, bmp)
                        } finally {
                            bmp.recycle()
                        }
                    }
                }
                return count
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
                page.canvas.drawBitmap(bmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                pdf.finishPage(page)
            }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    fun merge(files: List<File>, output: OutputStream) {
        require(files.isNotEmpty()) { "没有可合并的 PDF" }
        val pdf = PdfDocument()
        var outPageNumber = 1
        try {
            files.forEach { file ->
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { srcPage ->
                                val bmp = renderSinglePage(srcPage)
                                try {
                                    val pageW = srcPage.width.coerceAtLeast(1)
                                    val pageH = srcPage.height.coerceAtLeast(1)
                                    val info = PdfDocument.PageInfo.Builder(pageW, pageH, outPageNumber++).create()
                                    val dstPage = pdf.startPage(info)
                                    dstPage.canvas.drawBitmap(
                                        bmp,
                                        null,
                                        RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()),
                                        Paint(Paint.FILTER_BITMAP_FLAG)
                                    )
                                    pdf.finishPage(dstPage)
                                } finally {
                                    bmp.recycle()
                                }
                            }
                        }
                    }
                }
            }
            require(outPageNumber > 1) { "所选 PDF 没有可合并的页面" }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    fun extract(file: File, pageIndices0: List<Int>, output: OutputStream) {
        require(pageIndices0.isNotEmpty()) { "没有选中的页" }
        val pdf = PdfDocument()
        var outPageNumber = 1
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (idx in pageIndices0) {
                        if (idx !in 0 until renderer.pageCount) continue
                        renderer.openPage(idx).use { srcPage ->
                            val bmp = renderSinglePage(srcPage)
                            try {
                                val pageW = srcPage.width.coerceAtLeast(1)
                                val pageH = srcPage.height.coerceAtLeast(1)
                                val info = PdfDocument.PageInfo.Builder(pageW, pageH, outPageNumber++).create()
                                val dstPage = pdf.startPage(info)
                                dstPage.canvas.drawBitmap(
                                    bmp,
                                    null,
                                    RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()),
                                    Paint(Paint.FILTER_BITMAP_FLAG)
                                )
                                pdf.finishPage(dstPage)
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
            require(outPageNumber > 1) { "没有选中的有效页码" }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    private fun renderSinglePage(page: PdfRenderer.Page): Bitmap {
        val baseMax = max(page.width, page.height).coerceAtLeast(1)
        val scale = min(2.5f, MAX_RENDER_EDGE.toFloat() / baseMax).coerceAtLeast(1f)
        val w = (page.width * scale).toInt().coerceAtLeast(1)
        val h = (page.height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bmp
    }

    private companion object {
        const val MAX_RENDER_EDGE = 2200
    }
}
