package com.scanner.offline.engine.image

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2

/**
 * 文档边缘检测：基于 OpenCV 的 Canny + findContours + approxPolyDP 算法。
 *
 * 流程：
 *   1. 缩放到中等分辨率（加快处理）
 *   2. 灰度 + 高斯模糊去噪
 *   3. Canny 边缘检测
 *   4. 形态学闭运算把断边连起来
 *   5. findContours 找出所有轮廓
 *   6. approxPolyDP 把轮廓近似为多边形，过滤出 4 个顶点的凸四边形
 *   7. 选面积最大的那个，认为它就是文档
 *   8. 把四角点排序为「左上、右上、右下、左下」并归一化到 [0,1]
 *
 * 失败时（OpenCV 加载失败 / 没找到合适的四边形）会**自动降级**到几何兜底，
 * 这样即便在 OpenCV native 没就绪的设备上也不会崩溃。
 */
class EdgeDetector {

    /**
     * 返回 4 个角点（顺序：左上、右上、右下、左下，归一化到 [0,1]）。
     */
    fun detect(bitmap: Bitmap): List<PointF> {
        return runCatching { detectWithOpenCv(bitmap) }
            .getOrElse {
                Log.w(TAG, "OpenCV 边缘检测失败，降级到默认四角：${it.message}")
                fallback()
            } ?: fallback()
    }

    private fun detectWithOpenCv(bitmap: Bitmap): List<PointF>? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 50 || h < 50) return null

        // 1. 缩到统一处理尺寸，避免大图慢
        val targetMax = 800.0
        val scale = (targetMax / maxOf(w, h)).coerceAtMost(1.0)
        val scaledW = (w * scale).toInt().coerceAtLeast(2)
        val scaledH = (h * scale).toInt().coerceAtLeast(2)

        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        val resized = Mat()
        Imgproc.resize(src, resized, Size(scaledW.toDouble(), scaledH.toDouble()))

        // 2. 灰度 + 高斯模糊
        val gray = Mat()
        Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        // 3. Canny
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)

        // 4. 闭运算：用一个 5x5 矩形核把断边连起来
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)

        // 5. findContours
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        if (contours.isEmpty()) {
            release(src, resized, gray, edges, hierarchy, kernel)
            return null
        }

        // 6 & 7. 找面积最大且能近似为凸四边形的轮廓
        val totalArea = (scaledW * scaledH).toDouble()
        val minArea = totalArea * 0.20  // 文档至少占 20%
        var bestQuad: List<Point>? = null
        var bestArea = 0.0
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea) continue
            val curve = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(curve, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(curve, approx, 0.02 * peri, true)
            val pts = approx.toList()
            curve.release(); approx.release()
            if (pts.size == 4 && area > bestArea && Imgproc.isContourConvex(MatOfPoint(*pts.toTypedArray()))) {
                bestQuad = pts
                bestArea = area
            }
        }
        contours.forEach { it.release() }
        release(src, resized, gray, edges, hierarchy, kernel)

        val quad = bestQuad ?: return null

        // 8. 归一化 + 排序为左上/右上/右下/左下
        val normalized = quad.map { p ->
            PointF(
                (p.x / scaledW.toDouble()).toFloat().coerceIn(0f, 1f),
                (p.y / scaledH.toDouble()).toFloat().coerceIn(0f, 1f)
            )
        }
        return sortCorners(normalized)
    }

    /**
     * 把任意顺序的 4 个点重排成：左上、右上、右下、左下。
     *
     * 算法：以中心点为原点，按极角排序（atan2），起点选最接近左上方向的那一个。
     */
    private fun sortCorners(points: List<PointF>): List<PointF> {
        require(points.size == 4)
        val cx = points.sumOf { it.x.toDouble() } / 4.0
        val cy = points.sumOf { it.y.toDouble() } / 4.0

        // 按以中心为原点的极角排序（CCW），但我们需要 CW 起于左上：
        // 用左上 = (x<cx, y<cy)、右上 = (x>cx, y<cy)、右下 = (x>cx, y>cy)、左下 = (x<cx, y>cy) 划分四象限
        val tl = points.minBy { it.x + it.y }
        val br = points.maxBy { it.x + it.y }
        val tr = points.minBy { it.y - it.x }   // y 小、x 大
        val bl = points.maxBy { it.y - it.x }   // y 大、x 小
        val sorted = listOf(tl, tr, br, bl)
        // 万一有重复（异常形状），就退化为按象限近似
        return if (sorted.distinct().size == 4) sorted else fallback()
    }

    private fun fallback(): List<PointF> {
        val inset = 0.05f
        return listOf(
            PointF(inset, inset),
            PointF(1f - inset, inset),
            PointF(1f - inset, 1f - inset),
            PointF(inset, 1f - inset)
        )
    }

    private fun release(vararg mats: Mat) {
        mats.forEach { it.release() }
    }

    @Suppress("unused")
    private fun debugAtan2(p: PointF, cx: Double, cy: Double): Double =
        atan2((p.y - cy), (p.x - cx))

    companion object {
        private const val TAG = "EdgeDetector"
    }
}
