package com.scanner.offline.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.InputStream

object BitmapUtils {

    /** 从 URI 读取 Bitmap，并按 Exif 旋转 */
    fun decode(resolver: ContentResolver, uri: Uri, maxSize: Int = 4096): Bitmap? {
        // 第一遍读尺寸
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val sample = calcSampleSize(opts.outWidth, opts.outHeight, maxSize)

        val decode = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp: Bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decode)
        } ?: return null

        val orientation = readOrientation(resolver, uri)
        return rotate(bmp, orientation)
    }

    fun decode(file: File, maxSize: Int = 4096): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val sample = calcSampleSize(opts.outWidth, opts.outHeight, maxSize)
        val decode = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, decode) ?: return null
        val orientation = readOrientation(file)
        return rotate(bmp, orientation)
    }

    private fun calcSampleSize(w: Int, h: Int, maxSize: Int): Int {
        if (w <= 0 || h <= 0) return 1
        var sample = 1
        while (w / sample > maxSize || h / sample > maxSize) sample *= 2
        return sample
    }

    private fun readOrientation(resolver: ContentResolver, uri: Uri): Int =
        runCatching {
            resolver.openInputStream(uri)?.use(::readOrientation)
                ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrElse { ExifInterface.ORIENTATION_NORMAL }

    private fun readOrientation(file: File): Int =
        runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrElse { ExifInterface.ORIENTATION_NORMAL }

    private fun readOrientation(input: InputStream): Int =
        ExifInterface(input).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )

    private fun rotate(src: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return src
        }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (rotated !== src) src.recycle()
        return rotated
    }
}
