package com.scanner.offline.engine.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.PdfPaperSize
import com.scanner.offline.domain.model.TableData
import com.scanner.offline.domain.model.TextBlock
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * 文档导出统一入口（零三方重型依赖版）。
 *
 * - PDF：Android 系统自带 [PdfDocument]
 * - Word(.docx) / Excel(.xlsx)：自行拼 OOXML（本质就是带 XML 的 zip）
 * - TXT / 图片格式：标准 IO + Bitmap.compress
 *
 * 选择不依赖 Apache POI 的原因：
 *   POI 在 Android 上需要专门的 fork（如 SUPERCILEX/poi-android，已停止维护，
 *   且对 Word（XWPF）支持不完整、需要额外配置 aalto-xml）。
 *   我们的导出场景文本结构简单，自己生成 OOXML 反而更可控、APK 更小。
 *
 * 所有方法都是阻塞 IO，调用方需要在 IO 调度器上执行。
 *
 * ─── 双重重载约定 ───────────────────────────
 * 每个导出方法都有两个版本：
 *   * `(... output: File)`         —— 历史版本，给测试与默认目录用，写完返回该 File
 *   * `(... output: OutputStream)` —— SAF 版本，写完不关闭 OS（由调用方 .use 包裹）
 * File 版本的实现一律是 `FileOutputStream(output).use { stream ->  实际方法(..., stream) }`
 */
class DocumentExporter {

    // ---------- PDF ----------

    /** 多张图片合并为一份 PDF。每张图片独占一页。 */
    fun imagesToPdf(
        imagePaths: List<String>,
        output: File,
        paperSize: PdfPaperSize = PdfPaperSize.A4,
        maxImageDimension: Int = 3000
    ): File {
        FileOutputStream(output).use { os -> imagesToPdf(imagePaths, os, paperSize, maxImageDimension) }
        return output
    }

    fun imagesToPdf(
        imagePaths: List<String>,
        output: OutputStream,
        paperSize: PdfPaperSize = PdfPaperSize.A4,
        maxImageDimension: Int = 3000
    ) {
        imagesToPdfInternal(imagePaths, pageTexts = null, pageBlocks = null, output, paperSize, maxImageDimension)
    }

    /**
     * 可搜索 PDF：每页在图片上叠加一层"隐形"OCR 文字（alpha=1 绘制）。
     * PDF 阅读器中可直接选中和搜索这些文字，而视觉上仍是扫描图。
     *
     * [pageTexts] 与 imagePaths 一一对应；若提供 [pageBlocks] 则按真实文本框坐标原位对齐绘制。
     */
    fun imagesToSearchablePdf(
        imagePaths: List<String>,
        pageTexts: List<String>,
        output: OutputStream,
        paperSize: PdfPaperSize = PdfPaperSize.A4,
        maxImageDimension: Int = 3000,
        pageBlocks: List<List<TextBlock>>? = null
    ) {
        require(imagePaths.size == pageTexts.size) { "图片与文本数量不一致" }
        imagesToPdfInternal(imagePaths, pageTexts, pageBlocks, output, paperSize, maxImageDimension)
    }

    private fun imagesToPdfInternal(
        imagePaths: List<String>,
        pageTexts: List<String>?,
        pageBlocks: List<List<TextBlock>>?,
        output: OutputStream,
        paperSize: PdfPaperSize,
        maxImageDimension: Int
    ) {
        val pdf = PdfDocument()
        try {
            imagePaths.forEachIndexed { index, path ->
                val bmp = decodeForPdf(path, maxImageDimension) ?: error("无法解码图片: $path")
                val (pageWidth, pageHeight) = pageSizeFor(paperSize, bmp.width, bmp.height)
                val info = PdfDocument.PageInfo
                    .Builder(pageWidth, pageHeight, index + 1)
                    .create()
                val page = pdf.startPage(info)
                val margin = if (paperSize == PdfPaperSize.ORIGINAL) 0f else PDF_MARGIN_PT
                val dstRect = drawFittedBitmap(page.canvas, bmp, pageWidth, pageHeight, margin)
                val text = pageTexts?.getOrNull(index)
                val blocks = pageBlocks?.getOrNull(index).orEmpty()
                if (blocks.any { it.text.isNotBlank() && it.boundingBox != null }) {
                    drawAlignedBlocksTextLayer(page.canvas, blocks, dstRect, bmp.width, bmp.height)
                } else if (!text.isNullOrBlank()) {
                    drawInvisibleTextLayer(page.canvas, text, pageWidth, pageHeight, margin)
                }
                pdf.finishPage(page)
                bmp.recycle()
            }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    /**
     * 页面尺寸（单位 pt）。
     * ORIGINAL 保留旧行为（页面 = 图片像素尺寸）；A4/Letter 按图片横竖自动切方向。
     */
    private fun pageSizeFor(paper: PdfPaperSize, imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
        if (paper == PdfPaperSize.ORIGINAL) {
            return imageWidth.coerceAtLeast(1) to imageHeight.coerceAtLeast(1)
        }
        val landscape = imageWidth > imageHeight
        val w = if (landscape) paper.heightPt else paper.widthPt
        val h = if (landscape) paper.widthPt else paper.heightPt
        return w to h
    }

    /** 等比缩放并居中绘制到页面内，四周留白，返回实际图片绘制区域。 */
    private fun drawFittedBitmap(
        canvas: Canvas,
        bmp: Bitmap,
        pageWidth: Int,
        pageHeight: Int,
        margin: Float
    ): RectF {
        val availW = (pageWidth - margin * 2).coerceAtLeast(1f)
        val availH = (pageHeight - margin * 2).coerceAtLeast(1f)
        val scale = minOf(availW / bmp.width, availH / bmp.height)
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        val left = (pageWidth - drawW) / 2f
        val top = (pageHeight - drawH) / 2f
        val dst = RectF(left, top, left + drawW, top + drawH)
        canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        return dst
    }

    /**
     * 按 OCR [TextBlock] 的真实坐标在图片对应位置绘制隐形文字层，实现 PDF 阅读器内原位框选复制。
     */
    private fun drawAlignedBlocksTextLayer(
        canvas: Canvas,
        blocks: List<TextBlock>,
        imageRect: RectF,
        bmpWidth: Int,
        bmpHeight: Int
    ) {
        val paint = Paint().apply {
            color = Color.argb(1, 0, 0, 0)
            isAntiAlias = false
        }
        val refW = maxOf(bmpWidth, blocks.maxOfOrNull { it.boundingBox?.right ?: 0 } ?: bmpWidth)
            .coerceAtLeast(1).toFloat()
        val refH = maxOf(bmpHeight, blocks.maxOfOrNull { it.boundingBox?.bottom ?: 0 } ?: bmpHeight)
            .coerceAtLeast(1).toFloat()

        blocks.forEach { block ->
            val box = block.boundingBox ?: return@forEach
            val lines = block.text.split('\n').filter { it.isNotBlank() }
            if (lines.isEmpty()) return@forEach

            val nl = if (box.hasNormalized) box.normLeft else (box.left / refW).coerceIn(0f, 1f)
            val nt = if (box.hasNormalized) box.normTop else (box.top / refH).coerceIn(0f, 1f)
            val nr = if (box.hasNormalized) box.normRight else (box.right / refW).coerceIn(nl + 0.01f, 1f)
            val nb = if (box.hasNormalized) box.normBottom else (box.bottom / refH).coerceIn(nt + 0.01f, 1f)

            val x = imageRect.left + nl * imageRect.width()
            val top = imageRect.top + nt * imageRect.height()
            val boxW = ((nr - nl) * imageRect.width()).coerceAtLeast(8f)
            val boxH = ((nb - nt) * imageRect.height()).coerceAtLeast(6f)
            val lineH = (boxH / lines.size).coerceAtLeast(5f)
            paint.textSize = (lineH * 0.85f).coerceIn(4f, 24f)

            lines.forEachIndexed { idx, line ->
                val measured = paint.measureText(line)
                paint.textScaleX = if (measured > 1f) (boxW / measured).coerceIn(0.5f, 2.0f) else 1f
                val y = top + (idx + 0.85f) * lineH
                canvas.drawText(line, x, y, paint)
            }
            paint.textScaleX = 1f
        }
    }

    /** 按最大边采样解码，避免超大图把内存打爆。 */
    private fun decodeForPdf(path: String, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    /**
     * 隐形文字层：透明画笔逐行绘制，行高按页高等比分布。
     * 文字对象会真实写入 PDF 内容流，因此可被全文检索与复制。
     */
    private fun drawInvisibleTextLayer(
        canvas: Canvas,
        text: String,
        pageWidth: Int,
        pageHeight: Int,
        margin: Float
    ) {
        val paint = Paint().apply {
            // 完全透明的文字会被 Skia 直接优化掉，导致 PDF 里没有文字对象。
            // 用 alpha=1 的黑色：视觉上不可见，但能真实写入内容流供检索。
            color = Color.argb(1, 0, 0, 0)
            textSize = (pageHeight * 0.018f).coerceIn(4f, 18f)
            isAntiAlias = false
        }
        val lines = text.split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        // 行距略大于字号，保证相邻行文字不会互相覆盖导致提取混乱
        val lineSpacing = (paint.textSize * 1.4f).coerceAtLeast(6f)
        val usableHeight = (pageHeight - margin * 2).coerceAtLeast(lineSpacing)
        val maxLines = (usableHeight / lineSpacing).toInt().coerceAtLeast(1)
        var y = margin + paint.textSize
        for (line in lines.take(maxLines)) {
            canvas.drawText(line, margin, y, paint)
            y += lineSpacing
        }
    }

    /** 把若干段已识别文本合并为一份 PDF（每段独立一页，A4 尺寸）。 */
    fun textsToPdf(texts: List<String>, output: File): File {
        FileOutputStream(output).use { os -> textsToPdf(texts, os) }
        return output
    }

    fun textsToPdf(texts: List<String>, output: OutputStream) {
        val pdf = PdfDocument()
        try {
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                isAntiAlias = true
            }
            val padding = 36f
            val maxWidth = 595f - padding * 2
            val lineHeight = 18f
            val bottomLimit = 842f - padding
            var pageNumber = 1

            texts.forEach { text ->
                var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber++).create())
                var y = padding + 16f
                text.split('\n').forEach { line ->
                    val wrapped = if (line.isEmpty()) listOf("") else wrapText(line, paint, maxWidth)
                    wrapped.forEach { piece ->
                        if (y > bottomLimit) {
                            pdf.finishPage(page)
                            page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber++).create())
                            y = padding + 16f
                        }
                        if (piece.isNotEmpty()) {
                            page.canvas.drawText(piece, padding, y, paint)
                        }
                        y += lineHeight
                    }
                }
                pdf.finishPage(page)
            }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    private fun wrapText(line: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(line) <= maxWidth) return listOf(line)
        val list = mutableListOf<String>()
        var offset = 0
        while (offset < line.length) {
            val count = paint.breakText(line, offset, line.length, true, maxWidth, null)
            if (count <= 0) break // 安全兜底，避免死循环
            list += line.substring(offset, offset + count)
            offset += count
        }
        return list
    }

    // ---------- Word (.docx) ----------

    fun textsToWord(texts: List<String>, output: File): File =
        OoxmlWriter.writeDocx(texts, output)

    fun textsToWord(texts: List<String>, output: OutputStream) =
        OoxmlWriter.writeDocx(texts, output)

    // ---------- Excel (.xlsx) ----------

    fun tableToExcel(tables: List<TableData>, output: File): File =
        OoxmlWriter.writeXlsx(tables, output)

    fun tableToExcel(tables: List<TableData>, output: OutputStream) =
        OoxmlWriter.writeXlsx(tables, output)

    // ---------- TXT ----------

    fun textsToTxt(texts: List<String>, output: File): File {
        FileOutputStream(output).use { os -> textsToTxt(texts, os) }
        return output
    }

    fun textsToTxt(texts: List<String>, output: OutputStream) {
        val joined = texts.mapIndexed { i, t -> "===== 第 ${i + 1} 页 =====\n\n$t" }
            .joinToString("\n\n")
        output.write(joined.toByteArray(Charsets.UTF_8))
    }

    // ---------- 图片格式 ----------

    fun convertImage(sourcePath: String, format: ExportFormat, output: File, quality: Int = 92): File {
        FileOutputStream(output).use { os -> convertImage(sourcePath, format, os, quality) }
        return output
    }

    fun convertImage(sourcePath: String, format: ExportFormat, output: OutputStream, quality: Int = 92) {
        val bmp = BitmapFactory.decodeFile(sourcePath) ?: error("无法解码图片: $sourcePath")
        val compressFormat = when (format) {
            ExportFormat.JPG -> Bitmap.CompressFormat.JPEG
            ExportFormat.PNG -> Bitmap.CompressFormat.PNG
            ExportFormat.WEBP -> webpCompressFormat()
            else -> error("不支持的图片格式：$format")
        }
        bmp.compress(compressFormat, quality, output)
        bmp.recycle()
    }

    /**
     * minSdk 为 29，但 [Bitmap.CompressFormat.WEBP_LOSSY] 自 API 30 才有；
     * 同时旧的 [Bitmap.CompressFormat.WEBP] 在 API 30+ 标记为 deprecated。
     * 这里按运行时 SDK 分支选择，避免 lint NewApi 报错与运行期 NoSuchFieldError。
     */
    @Suppress("DEPRECATION")
    private fun webpCompressFormat(): Bitmap.CompressFormat =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private companion object {
        /** A4 / Letter 页面的四周留白（pt） */
        const val PDF_MARGIN_PT = 18f
    }
}
