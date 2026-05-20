package com.scanner.offline.engine.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * 把纯文本渲染为 A4 比例位图（与 [DocumentExporter.textsToPdf] 排版一致）。
 */
internal object TextPageRenderer {

    const val PAGE_WIDTH = 1240
    const val PAGE_HEIGHT = 1754
    private const val TEXT_SIZE = 28f
    private const val LINE_HEIGHT = 42f
    private const val PADDING = 72f
    private const val MAX_LINES_PER_PAGE = 36

    fun renderPages(texts: List<String>): List<Bitmap> {
        if (texts.isEmpty()) return listOf(renderSinglePage(""))
        return texts.flatMap { text -> paginate(text).map { renderSinglePage(it) } }
    }

    fun renderSinglePage(text: String): Bitmap {
        val bmp = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = TEXT_SIZE
            isAntiAlias = true
        }
        val maxWidth = PAGE_WIDTH - PADDING * 2
        var y = PADDING + TEXT_SIZE
        text.split('\n').forEach { line ->
            wrapText(line, paint, maxWidth).forEach { piece ->
                if (y <= PAGE_HEIGHT - PADDING) {
                    canvas.drawText(piece, PADDING, y, paint)
                    y += LINE_HEIGHT
                }
            }
        }
        return bmp
    }

    private fun paginate(text: String): List<String> {
        val lines = text.split('\n')
        if (lines.size <= MAX_LINES_PER_PAGE) return listOf(text)
        val pages = mutableListOf<String>()
        var start = 0
        while (start < lines.size) {
            val chunk = lines.subList(start, minOf(start + MAX_LINES_PER_PAGE, lines.size))
            pages += chunk.joinToString("\n")
            start += MAX_LINES_PER_PAGE
        }
        return pages
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
}
