package com.scanner.offline

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanner.offline.engine.image.ImageTransform
import com.scanner.offline.engine.image.NormalizedCropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageTransformTest {

    private val transform = ImageTransform()

    @Test
    fun flipHorizontal_twice_restores_pixels() {
        val src = unique2x2()
        val once = transform.flipHorizontal(src)
        val twice = transform.flipHorizontal(once)
        assertPixelsEqual(src, twice)
        assertEquals(Color.GREEN, once.getPixel(0, 0))
        assertEquals(Color.RED, once.getPixel(1, 0))
    }

    @Test
    fun flipVertical_twice_restores_pixels() {
        val src = unique2x2()
        val once = transform.flipVertical(src)
        val twice = transform.flipVertical(once)
        assertPixelsEqual(src, twice)
        assertEquals(Color.BLUE, once.getPixel(0, 0))
        assertEquals(Color.RED, once.getPixel(0, 1))
    }

    @Test
    fun rotate90_swaps_dimensions_and_four_times_restores() {
        val src = unique2x2()
        val r1 = transform.rotate90Clockwise(src)
        assertEquals(2, r1.width)
        assertEquals(2, r1.height)
        val r4 = (1..3).fold(r1) { acc, _ -> transform.rotate90Clockwise(acc) }
        assertPixelsEqual(src, r4)
    }

    @Test
    fun rotate90ccw_then_cw_restores() {
        val src = unique2x2()
        val ccw = transform.rotate90CounterClockwise(src)
        val back = transform.rotate90Clockwise(ccw)
        assertPixelsEqual(src, back)
    }

    @Test
    fun crop_center_half_has_expected_size_and_color() {
        val src = unique2x2()
        val cropped = transform.crop(src, NormalizedCropRect(0.5f, 0f, 1f, 0.5f))
        assertEquals(1, cropped.width)
        assertEquals(1, cropped.height)
        assertEquals(Color.GREEN, cropped.getPixel(0, 0))
        assertTrue(cropped.byteCount > 0)
    }

    private fun unique2x2(): Bitmap {
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, Color.RED)
        bmp.setPixel(1, 0, Color.GREEN)
        bmp.setPixel(0, 1, Color.BLUE)
        bmp.setPixel(1, 1, Color.YELLOW)
        return bmp
    }

    private fun assertPixelsEqual(a: Bitmap, b: Bitmap) {
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                assertEquals("pixel ($x,$y)", a.getPixel(x, y), b.getPixel(x, y))
            }
        }
    }
}
