package com.scanner.offline.engine.image

import kotlin.math.roundToInt

/** 像素坐标系下的裁剪矩形（左上角 + 宽高）。 */
data class PixelRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

/**
 * 归一化裁剪框，坐标范围 [0, 1]，相对于图片宽高。
 * 纯数据结构，可在 JVM 单测里验证，不依赖 Android Bitmap。
 */
data class NormalizedCropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    val isFull: Boolean
        get() = left <= 0.0001f && top <= 0.0001f &&
            right >= 0.9999f && bottom >= 0.9999f

    fun coerced(minSize: Float = MIN_SIZE): NormalizedCropRect {
        val min = minSize.coerceIn(0.01f, 0.5f)
        var l = left.coerceIn(0f, 1f)
        var t = top.coerceIn(0f, 1f)
        var r = right.coerceIn(0f, 1f)
        var b = bottom.coerceIn(0f, 1f)
        if (r - l < min) {
            val mid = ((l + r) / 2f).coerceIn(min / 2f, 1f - min / 2f)
            l = (mid - min / 2f).coerceAtLeast(0f)
            r = (l + min).coerceAtMost(1f)
            l = (r - min).coerceAtLeast(0f)
        }
        if (b - t < min) {
            val mid = ((t + b) / 2f).coerceIn(min / 2f, 1f - min / 2f)
            t = (mid - min / 2f).coerceAtLeast(0f)
            b = (t + min).coerceAtMost(1f)
            t = (b - min).coerceAtLeast(0f)
        }
        return NormalizedCropRect(l, t, r, b)
    }

    fun toPixels(imageWidth: Int, imageHeight: Int): PixelRect {
        require(imageWidth > 0 && imageHeight > 0)
        val safe = coerced()
        var l = (safe.left * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
        var t = (safe.top * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)
        var r = (safe.right * imageWidth).roundToInt().coerceIn(l + 1, imageWidth)
        var b = (safe.bottom * imageHeight).roundToInt().coerceIn(t + 1, imageHeight)
        return PixelRect(l, t, r - l, b - t)
    }

    fun moveCorner(index: Int, x: Float, y: Float): NormalizedCropRect {
        val nx = x.coerceIn(0f, 1f)
        val ny = y.coerceIn(0f, 1f)
        return when (index) {
            0 -> copy(left = nx, top = ny)   // 左上
            1 -> copy(right = nx, top = ny)  // 右上
            2 -> copy(right = nx, bottom = ny) // 右下
            3 -> copy(left = nx, bottom = ny)  // 左下
            else -> this
        }.coerced()
    }

    companion object {
        const val MIN_SIZE = 0.08f
        fun full(): NormalizedCropRect = NormalizedCropRect(0f, 0f, 1f, 1f)
        fun inset(margin: Float = 0.08f): NormalizedCropRect =
            NormalizedCropRect(margin, margin, 1f - margin, 1f - margin).coerced()
    }
}
