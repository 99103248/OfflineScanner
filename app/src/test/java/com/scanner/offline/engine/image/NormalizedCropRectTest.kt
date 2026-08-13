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
}
