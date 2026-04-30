package com.scanner.offline.engine.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.scanner.offline.domain.model.FilterMode
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * 文档滤镜。
 *
 * - ORIGINAL：原样返回
 * - ENHANCE：提亮 + 提对比度（ColorMatrix，CPU 友好）
 * - GRAYSCALE：去色（ColorMatrix）
 * - BLACK_WHITE：去色 + 大对比度（ColorMatrix）
 * - REMOVE_SHADOW：使用 OpenCV 自适应阈值，对纸面阴影效果最好；OpenCV 不可用时降级到 ColorMatrix
 */
class ImageFilter {

    fun apply(source: Bitmap, mode: FilterMode): Bitmap = when (mode) {
        FilterMode.ORIGINAL -> source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        FilterMode.ENHANCE -> applyMatrix(source, enhanceMatrix())
        FilterMode.GRAYSCALE -> applyMatrix(source, grayscaleMatrix())
        FilterMode.BLACK_WHITE -> applyMatrix(source, blackWhiteMatrix())
        FilterMode.REMOVE_SHADOW -> removeShadow(source)
    }

    // ---------- 颜色矩阵滤镜 ----------

    private fun applyMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(out).drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun enhanceMatrix(): ColorMatrix {
        val contrast = 1.25f
        val brightness = 18f
        val t = (1f - contrast) * 127.5f + brightness
        return ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun grayscaleMatrix(): ColorMatrix =
        ColorMatrix().apply { setSaturation(0f) }

    private fun blackWhiteMatrix(): ColorMatrix {
        val gray = grayscaleMatrix()
        val contrast = 2.2f
        val t = (1f - contrast) * 127.5f
        val highContrast = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return ColorMatrix().apply {
            postConcat(gray)
            postConcat(highContrast)
        }
    }

    // ---------- 去阴影：OpenCV 自适应阈值 ----------

    /**
     * 思路：用大核背景估计 + 除法归一化，把局部光照变化抹平。
     *
     * 1. 转成灰度
     * 2. 用大尺寸中值滤波估计背景（≈ 纸面光照）
     * 3. 原图除以背景再乘 255，得到光照均匀的图
     * 4. 适度增对比度，让字更清晰
     */
    private fun removeShadow(source: Bitmap): Bitmap {
        return runCatching { removeShadowOpenCv(source) }
            .getOrElse {
                Log.w(TAG, "OpenCV 去阴影失败，降级到 ColorMatrix：${it.message}")
                applyMatrix(source, fallbackShadowMatrix())
            }
    }

    private fun removeShadowOpenCv(source: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(source, src)

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

        // 中值滤波核要够大才能逼近纸面背景。这里取短边的 1/15，
        // 同时强制为奇数（OpenCV 要求）。
        var ksize = (minOf(source.width, source.height) / 15).coerceAtLeast(31)
        if (ksize % 2 == 0) ksize += 1

        val bg = Mat()
        Imgproc.medianBlur(gray, bg, ksize)

        // result = (gray / bg) * 255  →  归一化光照
        val grayF = Mat(); val bgF = Mat()
        gray.convertTo(grayF, org.opencv.core.CvType.CV_32F)
        bg.convertTo(bgF, org.opencv.core.CvType.CV_32F)
        val ratio = Mat()
        org.opencv.core.Core.divide(grayF, bgF, ratio, 255.0)
        val out8 = Mat()
        ratio.convertTo(out8, org.opencv.core.CvType.CV_8U)

        // 略微提高对比度
        val contrast = Mat()
        out8.convertTo(contrast, -1, 1.1, -10.0)

        // 转回 RGBA 给 Bitmap
        val rgba = Mat()
        Imgproc.cvtColor(contrast, rgba, Imgproc.COLOR_GRAY2RGBA)

        val bmp = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bmp)

        listOf(src, gray, bg, grayF, bgF, ratio, out8, contrast, rgba).forEach { it.release() }
        return bmp
    }

    /** OpenCV 不可用时的退化版 —— 接近 ENHANCE，但更激进一些 */
    private fun fallbackShadowMatrix(): ColorMatrix {
        val contrast = 1.4f
        val brightness = 24f
        val t = (1f - contrast) * 127.5f + brightness
        return ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    @Suppress("unused")
    private fun unused() {
        Size(0.0, 0.0)
    }

    companion object {
        private const val TAG = "ImageFilter"
    }
}
