package com.scanner.offline.engine.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.scanner.offline.domain.model.StitchMode
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 将多张位图拼接为一张图。
 */
object BitmapStitcher {

    const val PAGE_GAP_PX = 12
    private const val MAX_LONG_EDGE = 32_000

    fun stitchVertical(pages: List<Bitmap>, gapPx: Int = PAGE_GAP_PX): Bitmap {
        require(pages.isNotEmpty()) { "没有可拼接的页面" }
        if (pages.size == 1) return pages.first()

        val targetWidth = pages.maxOf { it.width }
        var totalHeight = pages.sumOf { it.height } + gapPx * (pages.size - 1)
        val scale = scaleFor(targetWidth, totalHeight)
        val outW = max(1, (targetWidth * scale).toInt())
        val outH = max(1, (totalHeight * scale).toInt())
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        var y = 0f
        pages.forEachIndexed { index, page ->
            val drawW = page.width * scale
            val drawH = page.height * scale
            val left = (outW - drawW) / 2f
            canvas.drawBitmap(page, null, android.graphics.RectF(left, y, left + drawW, y + drawH), null)
            y += drawH
            if (index < pages.lastIndex) y += gapPx * scale
        }
        return result
    }

    fun stitchHorizontal(pages: List<Bitmap>, gapPx: Int = PAGE_GAP_PX): Bitmap {
        require(pages.isNotEmpty()) { "没有可拼接的页面" }
        if (pages.size == 1) return pages.first()
        val targetHeight = pages.maxOf { it.height }
        val totalWidth = pages.sumOf { it.width } + gapPx * (pages.size - 1)
        val scale = scaleFor(totalWidth, targetHeight)
        val outW = max(1, (totalWidth * scale).toInt())
        val outH = max(1, (targetHeight * scale).toInt())
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        var x = 0f
        pages.forEachIndexed { index, page ->
            val drawW = page.width * scale
            val drawH = page.height * scale
            val top = (outH - drawH) / 2f
            canvas.drawBitmap(page, null, android.graphics.RectF(x, top, x + drawW, top + drawH), null)
            x += drawW
            if (index < pages.lastIndex) x += gapPx * scale
        }
        return result
    }

    fun stitchGrid(pages: List<Bitmap>, gapPx: Int = PAGE_GAP_PX): Bitmap {
        require(pages.isNotEmpty()) { "没有可拼接的页面" }
        if (pages.size == 1) return pages.first()
        val cols = ceil(sqrt(pages.size.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(pages.size / cols.toDouble()).toInt()
        val cellW = pages.maxOf { it.width }
        val cellH = pages.maxOf { it.height }
        val totalW = cols * cellW + gapPx * (cols - 1)
        val totalH = rows * cellH + gapPx * (rows - 1)
        val scale = scaleFor(totalW, totalH)
        val outW = max(1, (totalW * scale).toInt())
        val outH = max(1, (totalH * scale).toInt())
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        pages.forEachIndexed { i, page ->
            val c = i % cols
            val r = i / cols
            val left = (c * (cellW + gapPx)) * scale
            val top = (r * (cellH + gapPx)) * scale
            val drawW = page.width * scale
            val drawH = page.height * scale
            canvas.drawBitmap(page, null, android.graphics.RectF(left, top, left + drawW, top + drawH), null)
        }
        return result
    }

    fun stitch(pages: List<Bitmap>, mode: StitchMode, gapPx: Int = PAGE_GAP_PX): Bitmap = when (mode) {
        StitchMode.VERTICAL -> stitchVertical(pages, gapPx)
        StitchMode.HORIZONTAL -> stitchHorizontal(pages, gapPx)
        StitchMode.GRID -> stitchGrid(pages, gapPx)
    }

    private fun scaleFor(width: Int, height: Int): Float {
        var scale = 1f
        if (height > MAX_LONG_EDGE || width > MAX_LONG_EDGE) {
            scale = min(
                MAX_LONG_EDGE.toFloat() / height,
                MAX_LONG_EDGE.toFloat() / width
            )
        }
        return scale
    }
}
