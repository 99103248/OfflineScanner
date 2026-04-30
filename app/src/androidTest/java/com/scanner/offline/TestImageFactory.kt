package com.scanner.offline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * 测试图片工厂：在 Bitmap 上绘制确定性的文字内容，
 * 让 OCR / PDF 测试可以稳定复现，不需要真实拍照。
 */
object TestImageFactory {

    /**
     * 生成一张带英文文字的白底图片，分辨率足够 OCR 识别（>= 16px/字符）。
     */
    fun englishLines(
        lines: List<String> = listOf(
            "Hello World",
            "OfflineScanner v1.0",
            "MLKit OCR Test 2026"
        ),
        width: Int = 1200,
        height: Int = 800,
        textSizePx: Float = 64f
    ): Bitmap = drawLines(lines, width, height, textSizePx, monospace = false)

    /**
     * 生成一张带中文文字的白底图片。
     */
    fun chineseLines(
        lines: List<String> = listOf(
            "中文识别测试",
            "离线文档扫描",
            "二零二六年四月"
        ),
        width: Int = 1200,
        height: Int = 800,
        textSizePx: Float = 80f
    ): Bitmap = drawLines(lines, width, height, textSizePx, monospace = false)

    /**
     * 在白底上绘制多行文字。
     *
     * 字体大小默认值已经过参数调优——保证每行字符高度 ≥ 24px，达到 OCR 推荐输入。
     */
    private fun drawLines(
        lines: List<String>,
        width: Int,
        height: Int,
        textSize: Float,
        monospace: Boolean
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            this.textSize = textSize
            isAntiAlias = true
            typeface = if (monospace) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
        }

        val padding = textSize * 1.5f
        val lineHeight = textSize * 1.4f
        var y = padding + textSize
        lines.forEach { line ->
            canvas.drawText(line, padding, y, paint)
            y += lineHeight
        }
        return bmp
    }
}
