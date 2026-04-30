package com.scanner.offline.engine.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 透视矫正：把任意四边形区域校正成正向矩形。
 *
 * 优先使用 OpenCV 的 `getPerspectiveTransform + warpPerspective`，质量最好；
 * 当 OpenCV 不可用（极端情况）会自动降级为 [Matrix.setPolyToPoly] + Canvas，
 * 这个降级方案用的是仿射近似，对小角度倾斜效果可接受，大角度会有失真。
 */
class PerspectiveCorrector {

    /**
     * @param source        原图
     * @param normalizedPts 4 个归一化角点，顺序：左上、右上、右下、左下
     */
    fun correct(source: Bitmap, normalizedPts: List<PointF>): Bitmap {
        require(normalizedPts.size == 4) { "需要 4 个角点" }
        return runCatching { correctWithOpenCv(source, normalizedPts) }
            .getOrElse {
                Log.w(TAG, "OpenCV 矫正失败，降级到仿射近似：${it.message}")
                correctWithMatrix(source, normalizedPts)
            }
    }

    private fun correctWithOpenCv(source: Bitmap, normalizedPts: List<PointF>): Bitmap {
        val w = source.width.toDouble()
        val h = source.height.toDouble()
        val srcPts = normalizedPts.map { Point(it.x * w, it.y * h) }

        // 估算目标矩形宽高：取上下两边、左右两边的较大值
        val widthTop = dist(srcPts[0], srcPts[1])
        val widthBottom = dist(srcPts[3], srcPts[2])
        val heightLeft = dist(srcPts[0], srcPts[3])
        val heightRight = dist(srcPts[1], srcPts[2])
        val outW = max(widthTop, widthBottom).roundToInt().coerceAtLeast(1)
        val outH = max(heightLeft, heightRight).roundToInt().coerceAtLeast(1)

        val src = Mat()
        Utils.bitmapToMat(source, src)
        val srcMat = MatOfPoint2f(*srcPts.toTypedArray())
        val dstMat = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outW.toDouble(), 0.0),
            Point(outW.toDouble(), outH.toDouble()),
            Point(0.0, outH.toDouble())
        )
        val transform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        val out = Mat(outH, outW, CvType.CV_8UC4)
        Imgproc.warpPerspective(src, out, transform, Size(outW.toDouble(), outH.toDouble()))

        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(out, result)

        src.release(); srcMat.release(); dstMat.release(); transform.release(); out.release()
        return result
    }

    private fun correctWithMatrix(source: Bitmap, normalizedPts: List<PointF>): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val src = FloatArray(8).apply {
            this[0] = normalizedPts[0].x * w; this[1] = normalizedPts[0].y * h
            this[2] = normalizedPts[1].x * w; this[3] = normalizedPts[1].y * h
            this[4] = normalizedPts[2].x * w; this[5] = normalizedPts[2].y * h
            this[6] = normalizedPts[3].x * w; this[7] = normalizedPts[3].y * h
        }
        val widthTop = hypot(src[2] - src[0], src[3] - src[1])
        val widthBottom = hypot(src[4] - src[6], src[5] - src[7])
        val heightLeft = hypot(src[6] - src[0], src[7] - src[1])
        val heightRight = hypot(src[4] - src[2], src[5] - src[3])
        val outW = max(widthTop, widthBottom).roundToInt().coerceAtLeast(1)
        val outH = max(heightLeft, heightRight).roundToInt().coerceAtLeast(1)

        val dst = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    /** 限制 Bitmap 的最长边，避免内存炸 */
    fun clamp(bitmap: Bitmap, maxSize: Int = 2400): Bitmap {
        val long = max(bitmap.width, bitmap.height)
        if (long <= maxSize) return bitmap
        val scale = maxSize.toFloat() / long
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun dist(a: Point, b: Point): Double = hypot(a.x - b.x, a.y - b.y)

    @Suppress("unused")
    private fun unused() {
        min(0, 0)
    }

    companion object {
        private const val TAG = "PerspectiveCorrector"
    }
}
