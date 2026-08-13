package com.scanner.offline.engine.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import com.scanner.offline.domain.model.WatermarkPosition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 矩形裁剪 / 翻转 / 旋转 / 水印 / 批注。全部在本地 Bitmap 上完成。
 */
@Singleton
class ImageTransform @Inject constructor() {

    fun crop(source: Bitmap, rect: NormalizedCropRect): Bitmap {
        val px = rect.toPixels(source.width, source.height)
        if (px.left == 0 && px.top == 0 &&
            px.width == source.width && px.height == source.height
        ) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        }
        return Bitmap.createBitmap(source, px.left, px.top, px.width, px.height)
    }

    fun flipHorizontal(source: Bitmap): Bitmap = transform(source, Matrix().apply { preScale(-1f, 1f) })

    fun flipVertical(source: Bitmap): Bitmap = transform(source, Matrix().apply { preScale(1f, -1f) })

    fun rotate90Clockwise(source: Bitmap): Bitmap =
        transform(source, Matrix().apply { postRotate(90f) })

    fun rotate90CounterClockwise(source: Bitmap): Bitmap =
        transform(source, Matrix().apply { postRotate(-90f) })

    /**
     * 任意角度旋转。空白处用 [fillColor] 填充（默认白底，PNG 可传透明）。
     */
    fun rotateDegrees(source: Bitmap, degrees: Float, fillColor: Int = Color.WHITE): Bitmap {
        if (abs(degrees) < 0.01f) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        }
        val rad = Math.toRadians(degrees.toDouble())
        val cos = abs(cos(rad)).toFloat()
        val sin = abs(sin(rad)).toFloat()
        val newW = max(1, (source.width * cos + source.height * sin).toInt())
        val newH = max(1, (source.width * sin + source.height * cos).toInt())
        val out = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(fillColor)
        val matrix = Matrix().apply {
            postTranslate(-source.width / 2f, -source.height / 2f)
            postRotate(degrees)
            postTranslate(newW / 2f, newH / 2f)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    fun applyWatermark(
        source: Bitmap,
        text: String,
        position: WatermarkPosition,
        alpha: Int = 140,
        textSizePx: Float = source.width * 0.045f
    ): Bitmap {
        if (text.isBlank()) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        }
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha.coerceIn(20, 255), 255, 255, 255)
            textSize = textSizePx.coerceAtLeast(18f)
            setShadowLayer(4f, 1f, 1f, Color.argb(180, 0, 0, 0))
        }
        val pad = source.width * 0.04f
        val fm = paint.fontMetrics
        val textW = paint.measureText(text)
        val textH = fm.descent - fm.ascent
        val x = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.BOTTOM_LEFT -> pad
            WatermarkPosition.TOP_RIGHT, WatermarkPosition.BOTTOM_RIGHT -> out.width - pad - textW
            WatermarkPosition.CENTER -> (out.width - textW) / 2f
        }
        val y = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_RIGHT -> pad - fm.ascent
            WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT -> out.height - pad - fm.descent
            WatermarkPosition.CENTER -> (out.height + textH) / 2f - fm.descent
        }
        canvas.drawText(text, x, y, paint)
        return out
    }

    fun bakeStrokes(source: Bitmap, strokes: List<List<Pair<Float, Float>>>, color: Int = Color.RED, strokeWidth: Float = 8f): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        if (strokes.isEmpty()) return out
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { pts ->
            if (pts.size < 2) return@forEach
            val path = Path()
            path.moveTo(pts.first().first * out.width, pts.first().second * out.height)
            pts.drop(1).forEach { (x, y) ->
                path.lineTo(x * out.width, y * out.height)
            }
            canvas.drawPath(path, paint)
        }
        return out
    }

    private fun transform(source: Bitmap, matrix: Matrix): Bitmap {
        val out = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        return if (out.config == Bitmap.Config.ARGB_8888) out
        else out.copy(Bitmap.Config.ARGB_8888, false).also { if (it !== out) out.recycle() }
    }
}
