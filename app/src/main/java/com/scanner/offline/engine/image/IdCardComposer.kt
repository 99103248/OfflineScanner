package com.scanner.offline.engine.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import javax.inject.Inject
import kotlin.math.max

/**
 * 证件扫描合成：把身份证 / 护照等正反面按常见排版合成到一张 A4 竖版页面。
 *
 * 输出 1240×1754（A4 @150dpi），两张卡片等比铺满各自的 1000×630 区域并居中裁切。
 */
class IdCardComposer @Inject constructor(
    private val edgeDetector: EdgeDetector,
    private val corrector: PerspectiveCorrector
) {

    fun compose(front: Bitmap, back: Bitmap): Bitmap {
        val page = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)

        val frontCard = prepareCard(front)
        val backCard = prepareCard(back)
        try {
            drawCard(canvas, frontCard, top = 190f)
            drawCard(canvas, backCard, top = 190f + CARD_HEIGHT + 90f)
        } finally {
            if (frontCard !== front) frontCard.recycle()
            if (backCard !== back) backCard.recycle()
        }
        return page
    }

    /** 自动找边 + 透视矫正；失败则用原图。 */
    private fun prepareCard(src: Bitmap): Bitmap {
        return runCatching {
            val corners = edgeDetector.detect(src)
            corrector.correct(src, corners)
        }.getOrElse { src }
    }

    private fun drawCard(canvas: Canvas, src: Bitmap, top: Float) {
        val left = (PAGE_WIDTH - CARD_WIDTH) / 2f
        val scale = max(CARD_WIDTH / src.width.toFloat(), CARD_HEIGHT / src.height.toFloat())
        val drawW = src.width * scale
        val drawH = src.height * scale
        val dst = RectF(
            left + (CARD_WIDTH - drawW) / 2f,
            top + (CARD_HEIGHT - drawH) / 2f,
            left + (CARD_WIDTH + drawW) / 2f,
            top + (CARD_HEIGHT + drawH) / 2f
        )
        canvas.save()
        canvas.clipRect(left, top, left + CARD_WIDTH, top + CARD_HEIGHT)
        canvas.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()

        // 细边框，让复印件更清晰
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(60, 0, 0, 0)
        }
        canvas.drawRect(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, border)
    }

    private companion object {
        const val PAGE_WIDTH = 1240
        const val PAGE_HEIGHT = 1754
        const val CARD_WIDTH = 1000f
        const val CARD_HEIGHT = 631f
    }
}
