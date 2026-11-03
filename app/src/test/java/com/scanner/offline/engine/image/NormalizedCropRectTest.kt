package com.scanner.offline.engine.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedCropRectTest {

    @Test
    fun `full 覆盖整张图`() {
        val full = NormalizedCropRect.full()
        assertTrue(full.isFull)
        val px = full.toPixels(1000, 800)
        assertEquals(0, px.left)
        assertEquals(0, px.top)
        assertEquals(1000, px.width)
        assertEquals(800, px.height)
    }

    @Test
    fun `中心一半映射到像素`() {
        val rect = NormalizedCropRect(0.25f, 0.25f, 0.75f, 0.75f)
        val px = rect.toPixels(200, 100)
        assertEquals(50, px.left)
        assertEquals(25, px.top)
        assertEquals(100, px.width)
        assertEquals(50, px.height)
    }

    @Test
    fun `过小矩形会被抬到最小尺寸`() {
        val tiny = NormalizedCropRect(0.5f, 0.5f, 0.51f, 0.51f).coerced()
        assertTrue(tiny.width >= NormalizedCropRect.MIN_SIZE - 0.0001f)
        assertTrue(tiny.height >= NormalizedCropRect.MIN_SIZE - 0.0001f)
        assertFalse(tiny.isFull)
    }

    @Test
    fun `拖动左上角不会越过右下角`() {
        val start = NormalizedCropRect(0.2f, 0.2f, 0.8f, 0.8f)
        val moved = start.moveCorner(0, 0.9f, 0.9f)
        assertTrue(moved.right - moved.left >= NormalizedCropRect.MIN_SIZE - 0.0001f)
        assertTrue(moved.bottom - moved.top >= NormalizedCropRect.MIN_SIZE - 0.0001f)
        assertTrue(moved.left < moved.right)
        assertTrue(moved.top < moved.bottom)
    }

    @Test
    fun `像素结果始终落在图内且至少 1px`() {
        val rect = NormalizedCropRect(-1f, -1f, 2f, 2f).coerced()
        val px = rect.toPixels(10, 10)
        assertTrue(px.left >= 0)
        assertTrue(px.top >= 0)
        assertTrue(px.right <= 10)
        assertTrue(px.bottom <= 10)
        assertTrue(px.width >= 1)
        assertTrue(px.height >= 1)
    }

    @Test
    fun `A4 比例锁定后宽高比接近 210比297`() {
        val imageAspect = 1f
        val rect = NormalizedCropRect.centered(com.scanner.offline.domain.model.CropAspect.A4, imageAspect)
        val pixelRatio = (rect.width / rect.height) * imageAspect
        val expected = 210f / 297f
        val err = kotlin.math.abs(pixelRatio - expected) / expected
        assertTrue(err < 0.12f)
    }

    @Test
    fun `平移矩形且边界不越界`() {
        val rect = NormalizedCropRect(0.2f, 0.2f, 0.6f, 0.6f)
        val moved = rect.translate(0.1f, -0.1f)
        assertEquals(0.3f, moved.left, 0.0001f)
        assertEquals(0.1f, moved.top, 0.0001f)
        assertEquals(0.7f, moved.right, 0.0001f)
        assertEquals(0.5f, moved.bottom, 0.0001f)

        // 超出边界被 clamp
        val clamped = rect.translate(-0.5f, 0.8f)
        assertEquals(0f, clamped.left, 0.0001f)
        assertEquals(0.4f, clamped.right, 0.0001f)
        assertEquals(0.6f, clamped.top, 0.0001f)
        assertEquals(1.0f, clamped.bottom, 0.0001f)
    }

    @Test
    fun `移动边缘自由模式正常生效`() {
        val rect = NormalizedCropRect(0.2f, 0.2f, 0.8f, 0.8f)
        val topMoved = rect.moveEdge(0, 0.4f)
        assertEquals(0.4f, topMoved.top, 0.0001f)
        assertEquals(0.8f, topMoved.bottom, 0.0001f)

        val rightMoved = rect.moveEdge(1, 0.7f)
        assertEquals(0.7f, rightMoved.right, 0.0001f)
        assertEquals(0.2f, rightMoved.left, 0.0001f)
    }
}
