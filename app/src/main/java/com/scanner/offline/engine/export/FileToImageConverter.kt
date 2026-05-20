package com.scanner.offline.engine.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.InputDocumentType
import com.scanner.offline.domain.model.PdfImageLayout
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 将 PDF / Word(.docx) / TXT / 图片 转为位图列表，供导出为 JPG/PNG。
 *
 * 全部在本地完成，不依赖网络。
 */
class FileToImageConverter {

    fun convertToBitmaps(
        file: File,
        type: InputDocumentType,
        pdfLayout: PdfImageLayout = PdfImageLayout.SEPARATE_PAGES
    ): List<Bitmap> =
        when (type) {
            InputDocumentType.PDF -> pdfToBitmaps(file, pdfLayout)
            InputDocumentType.WORD -> TextPageRenderer.renderPages(OoxmlReader.readDocxTexts(file))
            InputDocumentType.TXT -> TextPageRenderer.renderPages(listOf(readTxt(file)))
            InputDocumentType.IMAGE -> {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    ?: error("无法解码图片: ${file.absolutePath}")
                listOf(downscaleIfNeeded(bmp))
            }
        }

    fun writeBitmap(bitmap: Bitmap, format: ExportFormat, output: java.io.OutputStream, quality: Int = 92) {
        val compressFormat = when (format) {
            ExportFormat.JPG -> Bitmap.CompressFormat.JPEG
            ExportFormat.PNG -> Bitmap.CompressFormat.PNG
            ExportFormat.WEBP -> webpCompressFormat()
            else -> error("文档转图片仅支持 JPG / PNG / WebP")
        }
        bitmap.compress(compressFormat, quality, output)
    }

    private fun pdfToBitmaps(file: File, layout: PdfImageLayout): List<Bitmap> {
        val pages = renderPdfPages(file)
        return when (layout) {
            PdfImageLayout.SEPARATE_PAGES -> pages
            PdfImageLayout.LONG_IMAGE -> {
                val stitched = BitmapStitcher.stitchVertical(pages)
                pages.forEach { if (it !== stitched) it.recycle() }
                listOf(stitched)
            }
        }
    }

    private fun renderPdfPages(file: File): List<Bitmap> {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) error("PDF 无页面")
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val scale = min(1f, MAX_EDGE.toFloat() / max(page.width, page.height))
                        val w = max(1, (page.width * scale).toInt())
                        val h = max(1, (page.height * scale).toInt())
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } finally {
            pfd.close()
        }
    }

    private fun readTxt(file: File): String {
        val bytes = file.readBytes()
        val text = bytes.toString(detectCharset(bytes))
        return text.replace('\u000C', '\n')
    }

    private fun detectCharset(bytes: ByteArray): java.nio.charset.Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        return Charsets.UTF_8
    }

    private fun downscaleIfNeeded(bmp: Bitmap): Bitmap {
        val edge = max(bmp.width, bmp.height)
        if (edge <= MAX_EDGE) return bmp
        val scale = MAX_EDGE.toFloat() / edge
        val w = max(1, (bmp.width * scale).toInt())
        val h = max(1, (bmp.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    @Suppress("DEPRECATION")
    private fun webpCompressFormat(): Bitmap.CompressFormat =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    companion object {
        private const val MAX_EDGE = 2400

        fun detectType(file: File, mimeType: String?): InputDocumentType {
            mimeType?.lowercase()?.let { mime ->
                when {
                    mime.contains("pdf") -> return InputDocumentType.PDF
                    mime.contains("word") || mime.contains("docx") -> return InputDocumentType.WORD
                    mime.startsWith("text/") -> return InputDocumentType.TXT
                    mime.startsWith("image/") -> return InputDocumentType.IMAGE
                }
            }
            return when (file.extension.lowercase()) {
                "pdf" -> InputDocumentType.PDF
                "docx" -> InputDocumentType.WORD
                "doc" -> error("暂不支持旧版 .doc，请使用 .docx")
                "txt", "text", "md", "log" -> InputDocumentType.TXT
                "jpg", "jpeg", "png", "webp", "gif", "bmp" -> InputDocumentType.IMAGE
                else -> error("不支持的文件类型：.${file.extension}")
            }
        }
    }
}
