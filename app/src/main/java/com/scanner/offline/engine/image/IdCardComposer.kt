package com.scanner.offline.engine.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.scanner.offline.domain.model.FilterMode
import com.scanner.offline.domain.model.WatermarkPosition
import javax.inject.Inject
import kotlin.math.max

/**
 * 证件扫描合成：把身份证 / 护照等正反面按常见排版合成到一张 A4 竖版页面。
 *
 * 输出 1240×1754（A4 @150dpi），两张卡片自动校正、竖拍自动扶正并等比铺满各自的 1000×631 区域。
 * 可选叠加滤镜（如黑白复印件/去阴影）与全屏斜向防盗水印。
 */
class IdCardComposer @Inject constructor(
    private val edgeDetector: EdgeDetector,
    private val corrector: PerspectiveCorrector,
    private val imageFilter: ImageFilter,
    private val imageTransform: ImageTransform
) {

    constructor(edgeDetector: EdgeDetector, corrector: PerspectiveCorrector) :
        this(edgeDetector, corrector, ImageFilter(), ImageTransform(corrector))

    fun compose(
        front: Bitmap,
        back: Bitmap,
        filterMode: FilterMode = FilterMode.ORIGINAL,
        watermarkText: String = "",
        watermarkAlpha: Int = 72
    ): Bitmap {
        val page = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)

        val frontCard = prepareCard(front, filterMode)
        val backCard = prepareCard(back, filterMode)
        try {
            drawCard(canvas, frontCard, top = 190f)
            drawCard(canvas, backCard, top = 190f + CARD_HEIGHT + 90f)
        } finally {
            if (frontCard !== front) frontCard.recycle()
            if (backCard !== back) backCard.recycle()
        }

        if (watermarkText.isBlank()) return page

        val watermarked = imageTransform.applyWatermark(
            source = page,
            text = watermarkText.trim(),
            position = WatermarkPosition.TILED,
            alpha = watermarkAlpha.coerceIn(30, 180),
            colorRgb = Color.rgb(60, 72, 88)
        )
        page.recycle()
        return watermarked
    }

    /** 自动找真实证件边框并透视矫正；未检测到边框时保留原图不误裁；竖拍证件自动扶正为横版。 */
    private fun prepareCard(src: Bitmap, filterMode: FilterMode): Bitmap {
        var current: Bitmap = runCatching {
            val corners = edgeDetector.detectQuadOrNull(src)
            if (corners != null) corrector.correct(src, corners) else src
        }.getOrElse { src }

        // 身份证等标准证件为横版（约 85.6mm × 54mm）；若用户竖着拍导致高大于宽，自动顺时针旋转 90°
        if (current.height > current.width * 1.15f) {
            val rotated = Bitmap.createBitmap(
                current, 0, 0, current.width, current.height,
                Matrix().apply { postRotate(90f) },
                true
            )
            if (current !== src && rotated !== current) current.recycle()
            current = rotated
        }

        if (filterMode != FilterMode.ORIGINAL) {
            val filtered = imageFilter.apply(current, filterMode)
            if (current !== src && filtered !== current) current.recycle()
            current = filtered
        }
        return current
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
