package com.scanner.offline.engine.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.TableData
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
    fun imagesToPdf(imagePaths: List<String>, output: File): File {
        FileOutputStream(output).use { os -> imagesToPdf(imagePaths, os) }
        return output
    }

    fun imagesToPdf(imagePaths: List<String>, output: OutputStream) {
        val pdf = PdfDocument()
        try {
            imagePaths.forEachIndexed { index, path ->
                val bmp = BitmapFactory.decodeFile(path) ?: error("无法解码图片: $path")
                val info = PdfDocument.PageInfo
                    .Builder(bmp.width, bmp.height, index + 1)
                    .create()
                val page = pdf.startPage(info)
                page.canvas.drawBitmap(bmp, 0f, 0f, null)
                pdf.finishPage(page)
                bmp.recycle()
            }
            pdf.writeTo(output)
        } finally {
            pdf.close()
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
            texts.forEachIndexed { index, text ->
                val info = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()  // A4 @ 72dpi
                val page = pdf.startPage(info)
                val paint = Paint().apply {
                    color = Color.BLACK
                    textSize = 12f
                    isAntiAlias = true
                }
                val padding = 36f
                val maxWidth = 595f - padding * 2
                var y = padding + 16f
                text.split('\n').forEach { line ->
                    val wrapped = wrapText(line, paint, maxWidth)
                    wrapped.forEach { piece ->
                        if (y <= 842 - padding) {
                            page.canvas.drawText(piece, padding, y, paint)
                            y += 18f
                        }
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
        val sb = StringBuilder()
        line.forEach { ch ->
            sb.append(ch)
            if (paint.measureText(sb.toString()) > maxWidth) {
                sb.deleteCharAt(sb.length - 1)
                list += sb.toString()
                sb.clear()
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) list += sb.toString()
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
}
