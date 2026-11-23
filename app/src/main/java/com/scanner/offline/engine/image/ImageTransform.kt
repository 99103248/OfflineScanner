package com.scanner.offline.engine.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.scanner.offline.domain.model.RedactStyle
import com.scanner.offline.domain.model.WatermarkPosition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 矩形裁剪 / 透视拉平 / 自由套索 / 翻转 / 旋转 / 水印 / 批注。全部在本地 Bitmap 上完成。
 */
@Singleton
class ImageTransform @Inject constructor(
    private val perspectiveCorrector: PerspectiveCorrector
) {

    constructor() : this(PerspectiveCorrector())

    fun crop(source: Bitmap, rect: NormalizedCropRect): Bitmap {
        val px = rect.toPixels(source.width, source.height)
        val safeLeft = px.left.coerceIn(0, source.width - 1)
        val safeTop = px.top.coerceIn(0, source.height - 1)
        val safeWidth = px.width.coerceIn(1, source.width - safeLeft)
        val safeHeight = px.height.coerceIn(1, source.height - safeTop)
        if (safeLeft == 0 && safeTop == 0 &&
            safeWidth == source.width && safeHeight == source.height
        ) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        }
        return Bitmap.createBitmap(source, safeLeft, safeTop, safeWidth, safeHeight)
    }

    /**
     * 四角透视拉平裁剪（CamScanner 风格）。
     * @param corners 4 个归一化角点，顺序：左上、右上、右下、左下
     */
    fun perspectiveCrop(source: Bitmap, corners: List<PointF>): Bitmap {
        if (corners.size != 4) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        }
        val corrected = perspectiveCorrector.correct(source, corners)
        val clamped = perspectiveCorrector.clamp(corrected, 2400)
        if (clamped !== corrected) corrected.recycle()
        return clamped
    }

    /**
     * 手绘套索自由裁剪（任意闭合多边形）。
     * @param normalizedPoints 归一化手绘轮廓点序列
     * @param transparentBg 是否保留透明背景（PNG 为 true，JPG/其他为 false 用白色填充）
     * @param fillColor 非透明背景下的填充颜色（默认白色）
     */
    fun freeformCrop(
        source: Bitmap,
        normalizedPoints: List<PointF>,
        transparentBg: Boolean = false,
        fillColor: Int = Color.WHITE
    ): Bitmap {
        if (normalizedPoints.size < 3) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        }
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val pixelPoints = normalizedPoints.map { PointF(it.x * w, it.y * h) }
        val minX = pixelPoints.minOf { it.x }.coerceIn(0f, w - 1f)
        val maxX = pixelPoints.maxOf { it.x }.coerceIn(minX + 1f, w)
        val minY = pixelPoints.minOf { it.y }.coerceIn(0f, h - 1f)
        val maxY = pixelPoints.maxOf { it.y }.coerceIn(minY + 1f, h)

        val outW = (maxX - minX).toInt().coerceAtLeast(1)
        val outH = (maxY - minY).toInt().coerceAtLeast(1)

        val masked = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(masked)

        val path = Path().apply {
            moveTo(pixelPoints.first().x - minX, pixelPoints.first().y - minY)
            for (i in 1 until pixelPoints.size) {
                lineTo(pixelPoints[i].x - minX, pixelPoints[i].y - minY)
            }
            close()
        }

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        maskCanvas.drawPath(path, maskPaint)

        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        maskCanvas.drawBitmap(source, -minX, -minY, photoPaint)

        if (transparentBg) {
            return masked
        }

        val filledOut = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val filledCanvas = Canvas(filledOut)
        filledCanvas.drawColor(fillColor)
        filledCanvas.drawBitmap(masked, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        masked.recycle()
        return filledOut
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

    /**
     * 亮度与对比度微调：
     * @param brightness 亮度偏移 [-80f, 80f]，0 为不变
     * @param contrast   对比度倍率 [0.5f, 2.0f]，1 为不变
     */
    fun adjustTone(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val c = contrast.coerceIn(0.5f, 2.0f)
        val b = brightness.coerceIn(-80f, 80f)
        if (abs(c - 1f) < 0.01f && abs(b) < 0.5f) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        }
        val t = (1f - c) * 127.5f + b
        val cm = android.graphics.ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        }
        Canvas(out).drawBitmap(source, 0f, 0f, paint)
        return out
    }

    fun applyWatermark(
        source: Bitmap,
        text: String,
        position: WatermarkPosition,
        alpha: Int = 140,
        textSizePx: Float = source.width * 0.045f,
        colorRgb: Int = Color.WHITE
    ): Bitmap {
        if (text.isBlank()) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        }
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val r = Color.red(colorRgb)
        val g = Color.green(colorRgb)
        val b = Color.blue(colorRgb)

        if (position == WatermarkPosition.TILED) {
            // 全屏斜向平铺防盗水印（常用于证件复印件、合同扫描件）
            val tiledSize = (minOf(out.width, out.height) * 0.036f).coerceIn(18f, 64f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha.coerceIn(20, 200), r, g, b)
                textSize = tiledSize
                isFakeBoldText = true
            }
            val textW = paint.measureText(text).coerceAtLeast(tiledSize * 2f)
            val stepX = (textW * 1.45f).coerceAtLeast(tiledSize * 5f)
            val stepY = (tiledSize * 4.5f).coerceAtLeast(96f)
            val diag = kotlin.math.hypot(out.width.toDouble(), out.height.toDouble()).toFloat()
            canvas.save()
            canvas.translate(out.width / 2f, out.height / 2f)
            canvas.rotate(-30f)
            var y = -diag
            var rowIdx = 0
            while (y <= diag) {
                var x = -diag + if (rowIdx % 2 == 0) 0f else stepX * 0.5f
                while (x <= diag) {
                    canvas.drawText(text, x, y, paint)
                    x += stepX
                }
                y += stepY
                rowIdx++
            }
            canvas.restore()
            return out
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha.coerceIn(20, 255), r, g, b)
            textSize = textSizePx.coerceAtLeast(18f)
            setShadowLayer(4f, 1f, 1f, Color.argb(160, 0, 0, 0))
        }
        val pad = source.width * 0.04f
        val fm = paint.fontMetrics
        val textW = paint.measureText(text)
        val textH = fm.descent - fm.ascent
        val x = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.BOTTOM_LEFT -> pad
            WatermarkPosition.TOP_RIGHT, WatermarkPosition.BOTTOM_RIGHT -> out.width - pad - textW
            WatermarkPosition.CENTER -> (out.width - textW) / 2f
            WatermarkPosition.TILED -> pad
        }
        val y = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_RIGHT -> pad - fm.ascent
            WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT -> out.height - pad - fm.descent
            WatermarkPosition.CENTER -> (out.height + textH) / 2f - fm.descent
            WatermarkPosition.TILED -> pad
        }
        canvas.drawText(text, x, y, paint)
        return out
    }

    /**
     * 打码脱敏：把若干归一化矩形涂黑或马赛克。
     */
    fun redact(
        source: Bitmap,
        rects: List<NormalizedCropRect>,
        style: RedactStyle,
        blockSize: Int = 18
    ): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        if (rects.isEmpty()) return out
        val canvas = Canvas(out)
        val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        rects.forEach { rect ->
            if (rect.width <= 0f || rect.height <= 0f) return@forEach
            val p = rect.toPixels(out.width, out.height)
            if (p.width <= 0 || p.height <= 0) return@forEach
            when (style) {
                RedactStyle.BLACK -> canvas.drawRect(
                    p.left.toFloat(), p.top.toFloat(),
                    p.right.toFloat(), p.bottom.toFloat(), blackPaint
                )
                RedactStyle.MOSAIC -> {
                    val region = Bitmap.createBitmap(source, p.left, p.top, p.width, p.height)
                    val smallW = (p.width / blockSize).coerceAtLeast(1)
                    val smallH = (p.height / blockSize).coerceAtLeast(1)
                    val small = Bitmap.createScaledBitmap(region, smallW, smallH, false)
                    val mosaic = Bitmap.createScaledBitmap(small, p.width, p.height, false)
                    canvas.drawBitmap(mosaic, p.left.toFloat(), p.top.toFloat(), null)
                    if (small !== region) small.recycle()
                    if (mosaic !== small) mosaic.recycle()
                    region.recycle()
                }
            }
        }
        return out
    }

    fun bakeStrokes(
        source: Bitmap,
        strokes: List<List<Pair<Float, Float>>>,
        color: Int = Color.RED,
        strokeWidth: Float = 8f
    ): Bitmap {
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

    /**
     * 烘焙多色/多粗细手写批注或电子签名。
     * [ColoredStroke.widthRatio] 为相对于图片最短边的线宽比例，确保不同分辨率下粗细一致。
     */
    fun bakeColoredStrokes(source: Bitmap, strokes: List<ColoredStroke>): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        if (strokes.isEmpty()) return out
        val canvas = Canvas(out)
        val minEdge = minOf(out.width, out.height).coerceAtLeast(1).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            paint.color = stroke.color
            paint.strokeWidth = (minEdge * stroke.widthRatio).coerceAtLeast(2f)
            val path = Path()
            path.moveTo(stroke.points.first().first * out.width, stroke.points.first().second * out.height)
            stroke.points.drop(1).forEach { (x, y) ->
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

/** 带颜色与线宽比例的归一化手绘笔画（用于多色批注与电子签名）。 */
data class ColoredStroke(
    val points: List<Pair<Float, Float>>,
    val color: Int = Color.BLACK,
    val widthRatio: Float = 0.005f
)
