package com.scanner.offline.engine.image

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * 文档边缘检测：基于 OpenCV 的多通道 Canny + findContours + approxPolyDP 算法。
 *
 * 改进要点：
 *   1. 支持灰度标准阈值、灰度低对比度阈值、HSV 饱和度通道三路候选检测，
 *      大幅提升浅色桌面拍白纸、发票小票、身份证件的检出率；
 *   2. 将最小面积占比下调至 8%，兼顾小尺寸票据与名片；
 *   3. 提供 [detectQuadOrNull] 供相机取景器实时判断画面中是否检测到真实文档边框。
 */
class EdgeDetector {

    /**
     * 返回 4 个角点（顺序：左上、右上、右下、左下，归一化到 [0,1]）。
     * 未检测到有效四边形时自动回退到 5% 内缩矩形。
     */
    fun detect(bitmap: Bitmap): List<PointF> {
        return detectQuadOrNull(bitmap) ?: fallback()
    }

    /**
     * 尝试检测文档四角点；若画面中未找到符合条件的凸四边形，返回 null（不返回默认框）。
     */
    fun detectQuadOrNull(bitmap: Bitmap): List<PointF>? {
        return runCatching { detectWithOpenCv(bitmap) }
            .onFailure { Log.w(TAG, "OpenCV 边缘检测异常：${it.message}") }
            .getOrNull()
    }

    private fun detectWithOpenCv(bitmap: Bitmap): List<PointF>? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 50 || h < 50) return null
        if (!OpenCvLoader.ensureLoaded()) return null

        // 1. 缩到统一处理尺寸，避免大图慢
        val targetMax = 800.0
        val scale = (targetMax / maxOf(w, h)).coerceAtMost(1.0)
        val scaledW = (w * scale).toInt().coerceAtLeast(2)
        val scaledH = (h * scale).toInt().coerceAtLeast(2)

        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        val resized = Mat()
        Imgproc.resize(src, resized, Size(scaledW.toDouble(), scaledH.toDouble()))

        // 灰度通道
        val gray = Mat()
        Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        // HSV 饱和度通道（针对浅色木桌/暖光下白纸对比度不足的场景）
        val rgb = Mat()
        val hsv = Mat()
        val hsvChannels = ArrayList<Mat>(3)
        Imgproc.cvtColor(resized, rgb, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
        Core.split(hsv, hsvChannels)
        val sat = hsvChannels[1]
        Imgproc.GaussianBlur(sat, sat, Size(5.0, 5.0), 0.0)

        val totalArea = (scaledW * scaledH).toDouble()
        val minArea = totalArea * 0.08 // 支持证件、小票、名片（至少占画面 8%）
        val maxArea = totalArea * 0.98

        val candidates = listOf(
            Triple(gray, 50.0, 150.0),
            Triple(gray, 25.0, 80.0),
            Triple(sat, 35.0, 110.0)
        )

        var bestQuad: List<Point>? = null
        var bestArea = 0.0
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))

        for ((channel, low, high) in candidates) {
            val edges = Mat()
            val hierarchy = Mat()
            val contours = mutableListOf<MatOfPoint>()
            try {
                Imgproc.Canny(channel, edges, low, high)
                Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
                Imgproc.findContours(
                    edges,
                    contours,
                    hierarchy,
                    Imgproc.RETR_LIST,
                    Imgproc.CHAIN_APPROX_SIMPLE
                )
                for (contour in contours) {
                    val area = Imgproc.contourArea(contour)
                    if (area < minArea || area > maxArea) continue
                    val curve = MatOfPoint2f(*contour.toArray())
                    val peri = Imgproc.arcLength(curve, true)
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(curve, approx, 0.02 * peri, true)
                    val pts = approx.toList()
                    curve.release()
                    approx.release()
                    if (pts.size == 4 && area > bestArea) {
                        val convexCheck = MatOfPoint(*pts.toTypedArray())
                        val isConvex = Imgproc.isContourConvex(convexCheck)
                        convexCheck.release()
                        if (isConvex) {
                            bestQuad = pts
                            bestArea = area
                        }
                    }
                }
            } finally {
                contours.forEach { it.release() }
                release(edges, hierarchy)
            }
            // 若在当前通道已找到占画面 25% 以上的理想大轮廓，无需继续尝试后续通道
            if (bestArea >= totalArea * 0.25) break
        }

        hsvChannels.forEach { it.release() }
        release(src, resized, gray, rgb, hsv, kernel)

        val quad = bestQuad ?: return null

        val normalized = quad.map { p ->
            PointF(
                (p.x / scaledW.toDouble()).toFloat().coerceIn(0f, 1f),
                (p.y / scaledH.toDouble()).toFloat().coerceIn(0f, 1f)
            )
        }
        return sortCornersOrNull(normalized)
    }

    private fun sortCornersOrNull(points: List<PointF>): List<PointF>? {
        if (points.size != 4) return null
        val tl = points.minBy { it.x + it.y }
        val br = points.maxBy { it.x + it.y }
        val tr = points.minBy { it.y - it.x }
        val bl = points.maxBy { it.y - it.x }
        val sorted = listOf(tl, tr, br, bl)
        return if (sorted.distinct().size == 4) sorted else null
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

    companion object {
        private const val TAG = "EdgeDetector"
    }
}
