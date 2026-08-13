package com.scanner.offline.engine.image

import android.graphics.Bitmap
import android.graphics.Matrix
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 矩形裁剪 / 翻转 / 旋转。全部在本地 Bitmap 上完成，不访问网络。
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

    /** 顺时针旋转 90° */
    fun rotate90Clockwise(source: Bitmap): Bitmap =
        transform(source, Matrix().apply { postRotate(90f) })

    private fun transform(source: Bitmap, matrix: Matrix): Bitmap {
        val out = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        return if (out.config == Bitmap.Config.ARGB_8888) out
        else out.copy(Bitmap.Config.ARGB_8888, false).also { if (it !== out) out.recycle() }
    }
}
