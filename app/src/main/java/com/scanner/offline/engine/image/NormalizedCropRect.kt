package com.scanner.offline.engine.image

import com.scanner.offline.domain.model.CropAspect
import kotlin.math.abs
import kotlin.math.min
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
 *
 * @param imageAspect 图片像素宽/高，用于把 [CropAspect] 从像素比换算到归一化坐标。
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
        val l = (safe.left * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
        val t = (safe.top * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)
        val r = (safe.right * imageWidth).roundToInt().coerceIn(l + 1, imageWidth)
        val b = (safe.bottom * imageHeight).roundToInt().coerceIn(t + 1, imageHeight)
        return PixelRect(l, t, r - l, b - t)
    }

    fun moveCorner(
        index: Int,
        x: Float,
        y: Float,
        imageAspect: Float = 1f,
        cropAspect: CropAspect = CropAspect.FREE
    ): NormalizedCropRect {
        val nx = x.coerceIn(0f, 1f)
        val ny = y.coerceIn(0f, 1f)
        val moved = when (index) {
            0 -> copy(left = nx, top = ny)
            1 -> copy(right = nx, top = ny)
            2 -> copy(right = nx, bottom = ny)
            3 -> copy(left = nx, bottom = ny)
            else -> this
        }.coerced()
        return moved.withAspect(cropAspect, imageAspect, anchorOppositeOf(index))
    }

    /**
     * 按像素宽高比调整矩形。anchor 为保持不动的角：0 左上 … 3 左下。
     */
    fun withAspect(
        aspect: CropAspect,
        imageAspect: Float,
        anchorCorner: Int = 0
    ): NormalizedCropRect {
        val ratio = aspect.widthOverHeight ?: return coerced()
        val img = if (imageAspect <= 0f) 1f else imageAspect
        val targetNorm = ratio / img
        if (targetNorm <= 0f) return coerced()
        var w = width.coerceAtLeast(MIN_SIZE)
        var h = w / targetNorm
        if (h < MIN_SIZE) {
            h = MIN_SIZE
            w = h * targetNorm
        }
        if (w > 1f) {
            w = 1f
            h = (w / targetNorm).coerceAtMost(1f)
        }
        if (h > 1f) {
            h = 1f
            w = (h * targetNorm).coerceAtMost(1f)
        }
        val (l, t) = when (anchorCorner) {
            1 -> (right - w).coerceIn(0f, 1f - w) to top.coerceIn(0f, 1f - h)
            2 -> (right - w).coerceIn(0f, 1f - w) to (bottom - h).coerceIn(0f, 1f - h)
            3 -> left.coerceIn(0f, 1f - w) to (bottom - h).coerceIn(0f, 1f - h)
            else -> left.coerceIn(0f, 1f - w) to top.coerceIn(0f, 1f - h)
        }
        return NormalizedCropRect(l, t, l + w, t + h).coerced()
    }

    private fun anchorOppositeOf(corner: Int): Int = when (corner) {
        0 -> 2
        1 -> 3
        2 -> 0
        3 -> 1
        else -> 0
    }

    companion object {
        const val MIN_SIZE = 0.08f
        fun full(): NormalizedCropRect = NormalizedCropRect(0f, 0f, 1f, 1f)
        fun inset(margin: Float = 0.08f): NormalizedCropRect =
            NormalizedCropRect(margin, margin, 1f - margin, 1f - margin).coerced()

        fun centered(aspect: CropAspect, imageAspect: Float): NormalizedCropRect {
            val base = inset(0.1f)
            val fitted = base.withAspect(aspect, imageAspect, 0)
            val w = fitted.width
            val h = fitted.height
            val l = ((1f - w) / 2f).coerceAtLeast(0f)
            val t = ((1f - h) / 2f).coerceAtLeast(0f)
            return NormalizedCropRect(l, t, l + w, t + h).coerced()
        }
    }
}

fun CropAspect.pixelRatioMatches(norm: NormalizedCropRect, imageAspect: Float, epsilon: Float = 0.08f): Boolean {
    val expected = widthOverHeight ?: return true
    val actual = if (norm.height <= 0f) 0f else (norm.width / norm.height) * imageAspect
    return abs(actual - expected) / expected <= epsilon || min(abs(actual - expected), 1f) < epsilon
}
