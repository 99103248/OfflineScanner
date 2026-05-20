package com.scanner.offline.engine.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * 将多张位图纵向拼接为一张长图（用于多页 PDF 导出）。
 */
internal object BitmapStitcher {

    const val PAGE_GAP_PX = 12
    private const val MAX_LONG_EDGE = 32_000

    fun stitchVertical(pages: List<Bitmap>, gapPx: Int = PAGE_GAP_PX): Bitmap {
        require(pages.isNotEmpty()) { "没有可拼接的页面" }
        if (pages.size == 1) return pages.first()

        val targetWidth = pages.maxOf { it.width }
        var totalHeight = pages.sumOf { it.height } + gapPx * (pages.size - 1)

        var scale = 1f
        if (totalHeight > MAX_LONG_EDGE || targetWidth > MAX_LONG_EDGE) {
            scale = min(
                MAX_LONG_EDGE.toFloat() / totalHeight,
                MAX_LONG_EDGE.toFloat() / targetWidth
            )
        }

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
            val dst = android.graphics.RectF(left, y, left + drawW, y + drawH)
            canvas.drawBitmap(page, null, dst, null)
            y += drawH
            if (index < pages.lastIndex) {
                y += gapPx * scale
            }
        }
        return result
    }
}
