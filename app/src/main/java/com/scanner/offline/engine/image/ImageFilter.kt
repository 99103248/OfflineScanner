package com.scanner.offline.engine.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.scanner.offline.domain.model.FilterMode
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * 文档滤镜。
 *
 * - ORIGINAL：原样返回
 * - ENHANCE：提亮 + 提对比度（ColorMatrix，CPU 友好）
 * - GRAYSCALE：去色（ColorMatrix）
 * - BLACK_WHITE：OpenCV 局部自适应二值化 + 抗锯齿融合（OpenCV 不可用时降级到 ColorMatrix）
 * - REMOVE_SHADOW：HSV 亮度通道多尺度背景估计与光照归一化（去阴影同时保留红章/彩笔等色彩信息）
 */
class ImageFilter {

    fun apply(source: Bitmap, mode: FilterMode): Bitmap = when (mode) {
        FilterMode.ORIGINAL -> source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        FilterMode.ENHANCE -> applyMatrix(source, enhanceMatrix())
        FilterMode.GRAYSCALE -> applyMatrix(source, grayscaleMatrix())
        FilterMode.BLACK_WHITE -> applyBlackWhite(source)
        FilterMode.REMOVE_SHADOW -> removeShadow(source)
        FilterMode.REMOVE_HANDWRITING -> removeHandwriting(source)
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

    // ---------- 黑白滤镜：OpenCV 局部自适应阈值 ----------

    private fun applyBlackWhite(source: Bitmap): Bitmap {
        return runCatching { blackWhiteOpenCv(source) }
            .getOrElse {
                Log.w(TAG, "OpenCV 自适应黑白失败，降级到 ColorMatrix：${it.message}")
                applyMatrix(source, blackWhiteMatrix())
            }
    }

    private fun blackWhiteOpenCv(source: Bitmap): Bitmap {
        check(OpenCvLoader.ensureLoaded()) { "OpenCV 未加载" }
        val src = Mat()
        Utils.bitmapToMat(source, src)

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)

        var blockSize = (minOf(source.width, source.height) / 24).coerceAtLeast(21)
        if (blockSize % 2 == 0) blockSize += 1

        val adaptive = Mat()
        Imgproc.adaptiveThreshold(
            blurred,
            adaptive,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            blockSize,
            11.0
        )

        // 将自适应二值结果与原灰度按 82:18 混合，使文字边缘平滑不刺眼
        val blended = Mat()
        Core.addWeighted(adaptive, 0.82, blurred, 0.18, 0.0, blended)

        val rgba = Mat()
        Imgproc.cvtColor(blended, rgba, Imgproc.COLOR_GRAY2RGBA)

        val bmp = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bmp)

        listOf(src, gray, blurred, adaptive, blended, rgba).forEach { it.release() }
        return bmp
    }

    // ---------- 去阴影：OpenCV HSV 亮度通道背景归一化（保留彩色） ----------

    /**
     * 思路：
     * 1. 转到 HSV 色彩空间，拆分出 H（色相）、S（饱和度）、V（明度）
     * 2. 对 V 通道下采样 4 倍后做形态学闭运算 + 高斯模糊，快速平滑地估计纸面背景光照
     * 3. 用 V / bg * 255 归一化光照并适度拉伸对比度，消除阴影同时保留红章/彩笔的 H 与 S
     * 4. 抑制纸面微弱色偏，使白纸区域更干净
     */
    private fun removeShadow(source: Bitmap): Bitmap {
        return runCatching { removeShadowOpenCv(source) }
            .getOrElse {
                Log.w(TAG, "OpenCV 去阴影失败，降级到 ColorMatrix：${it.message}")
                applyMatrix(source, fallbackShadowMatrix())
            }
    }

    private fun removeShadowOpenCv(source: Bitmap): Bitmap {
        check(OpenCvLoader.ensureLoaded()) { "OpenCV 未加载" }
        val w = source.width
        val h = source.height

        val src = Mat()
        Utils.bitmapToMat(source, src)

        val rgb = Mat()
        Imgproc.cvtColor(src, rgb, Imgproc.COLOR_RGBA2RGB)

        val hsv = Mat()
        Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)

        val channels = ArrayList<Mat>(3)
        Core.split(hsv, channels)
        val chH = channels[0]
        val chS = channels[1]
        val chV = channels[2]

        // 下采样 4 倍做形态学闭运算 + 高斯模糊，速度比全图大核 medianBlur 快约 10 倍
        val smallW = (w / 4).coerceAtLeast(16)
        val smallH = (h / 4).coerceAtLeast(16)
        val vSmall = Mat()
        Imgproc.resize(chV, vSmall, Size(smallW.toDouble(), smallH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)

        var ksize = (minOf(smallW, smallH) / 6).coerceAtLeast(11)
        if (ksize % 2 == 0) ksize += 1
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(ksize.toDouble(), ksize.toDouble()))
        val closedSmall = Mat()
        Imgproc.morphologyEx(vSmall, closedSmall, Imgproc.MORPH_CLOSE, kernel)

        var blurSize = (ksize / 2).coerceAtLeast(5)
        if (blurSize % 2 == 0) blurSize += 1
        Imgproc.GaussianBlur(closedSmall, closedSmall, Size(blurSize.toDouble(), blurSize.toDouble()), 0.0)

        val bg = Mat()
        Imgproc.resize(closedSmall, bg, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)

        // V_norm = (V / bg) * 255
        val vFloat = Mat()
        val bgFloat = Mat()
        chV.convertTo(vFloat, CvType.CV_32F)
        bg.convertTo(bgFloat, CvType.CV_32F)
        // 避免除以 0
        Core.max(bgFloat, org.opencv.core.Scalar(1.0), bgFloat)

        val ratio = Mat()
        Core.divide(vFloat, bgFloat, ratio, 255.0)
        val vOut = Mat()
        ratio.convertTo(vOut, CvType.CV_8U, 1.12, -12.0)

        // 轻微压低纸面环境光的低饱和度色偏，同时保留印章/彩字的高饱和度
        val sOut = Mat()
        chS.convertTo(sOut, CvType.CV_8U, 1.05, -8.0)

        val mergedHsv = Mat()
        Core.merge(listOf(chH, sOut, vOut), mergedHsv)

        val outRgb = Mat()
        Imgproc.cvtColor(mergedHsv, outRgb, Imgproc.COLOR_HSV2RGB)
        val outRgba = Mat()
        Imgproc.cvtColor(outRgb, outRgba, Imgproc.COLOR_RGB2RGBA)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outRgba, bmp)

        listOf(
            src, rgb, hsv, chH, chS, chV,
            vSmall, kernel, closedSmall, bg,
            vFloat, bgFloat, ratio, vOut, sOut,
            mergedHsv, outRgb, outRgba
        ).forEach { it.release() }
        return bmp
    }

    // ---------- 去蓝红手写笔迹（试卷/批注还原） ----------

    /**
     * 去除试卷或打印件上的蓝色/红色手写笔迹，尽量保留黑色印刷文字与表格线。
     * 优先用 OpenCV HSV 通道精确识别蓝/红高饱和度笔迹并用干净纸面底色填充；
     * OpenCV 不可用时降级到纯像素 RGB 通道检测。
     */
    private fun removeHandwriting(source: Bitmap): Bitmap {
        return runCatching { removeHandwritingOpenCv(source) }
            .getOrElse {
                Log.w(TAG, "OpenCV 去笔迹失败，降级到像素实现：${it.message}")
                removeHandwritingFallback(source)
            }
    }

    private fun removeHandwritingOpenCv(source: Bitmap): Bitmap {
        check(OpenCvLoader.ensureLoaded()) { "OpenCV 未加载" }
        val w = source.width
        val h = source.height
        val src = Mat()
        Utils.bitmapToMat(source, src)

        val rgb = Mat()
        Imgproc.cvtColor(src, rgb, Imgproc.COLOR_RGBA2RGB)
        val hsv = Mat()
        Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)

        // OpenCV HSV 范围：H in [0,179], S in [0,255], V in [0,255]
        // 蓝色笔迹（蓝圆珠笔/中性笔）：H 90..138, S >= 45, V >= 40
        val blueMask = Mat()
        Core.inRange(hsv, org.opencv.core.Scalar(90.0, 45.0, 40.0), org.opencv.core.Scalar(138.0, 255.0, 255.0), blueMask)
        // 红色笔迹（红笔批改）：H 0..14 或 162..179, S >= 45, V >= 40
        val redMask1 = Mat()
        val redMask2 = Mat()
        Core.inRange(hsv, org.opencv.core.Scalar(0.0, 45.0, 40.0), org.opencv.core.Scalar(14.0, 255.0, 255.0), redMask1)
        Core.inRange(hsv, org.opencv.core.Scalar(162.0, 45.0, 40.0), org.opencv.core.Scalar(179.0, 255.0, 255.0), redMask2)

        val inkMask = Mat()
        Core.bitwise_or(blueMask, redMask1, inkMask)
        Core.bitwise_or(inkMask, redMask2, inkMask)

        // 适度膨胀 1px 消除笔迹边缘残留彩晕
        val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        Imgproc.dilate(inkMask, inkMask, dilateKernel)

        // 转为灰度并提亮背景，然后把蓝/红笔迹掩码区域涂白还原空白纸面
        val gray = Mat()
        Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
        val enhancedGray = Mat()
        gray.convertTo(enhancedGray, CvType.CV_8U, 1.15, 12.0)
        enhancedGray.setTo(org.opencv.core.Scalar(252.0), inkMask)

        val outRgba = Mat()
        Imgproc.cvtColor(enhancedGray, outRgba, Imgproc.COLOR_GRAY2RGBA)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outRgba, bmp)

        listOf(
            src, rgb, hsv, blueMask, redMask1, redMask2,
            inkMask, dilateKernel, gray, enhancedGray, outRgba
        ).forEach { it.release() }
        return bmp
    }

    private fun removeHandwritingFallback(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = android.graphics.Color.red(c)
            val g = android.graphics.Color.green(c)
            val b = android.graphics.Color.blue(c)
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0] // 0..360
            val sat = hsv[1] // 0..1
            val value = hsv[2] // 0..1
            val isBlueInk = hue in 180f..275f && sat >= 0.18f && value >= 0.16f
            val isRedInk = (hue <= 28f || hue >= 325f) && sat >= 0.18f && value >= 0.16f
            if (isBlueInk || isRedInk) {
                pixels[i] = android.graphics.Color.rgb(252, 252, 252)
            } else {
                val lum = ((0.299f * r + 0.587f * g + 0.114f * b) * 1.12f + 10f).toInt().coerceIn(0, 255)
                pixels[i] = android.graphics.Color.rgb(lum, lum, lum)
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
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

    companion object {
        private const val TAG = "ImageFilter"
    }
}
