package com.scanner.offline.engine.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadStabilityTrackerTest {

    private val steadyQuad = listOf(
        NormPoint(0.1f, 0.1f),
        NormPoint(0.9f, 0.1f),
        NormPoint(0.9f, 0.9f),
        NormPoint(0.1f, 0.9f)
    )

    @Test
    fun `连续稳定 N 帧后触发自动拍摄并进入冷却期`() {
        val tracker = QuadStabilityTracker(requiredStableFrames = 3, cooldownMs = 1500L)

        val r1 = tracker.update(steadyQuad, 1000L)
        assertFalse(r1.shouldCapture)
        assertEquals(1f / 3f, r1.progress, 0.01f)

        val r2 = tracker.update(steadyQuad, 1250L)
        assertFalse(r2.shouldCapture)
        assertEquals(2f / 3f, r2.progress, 0.01f)

        val r3 = tracker.update(steadyQuad, 1500L)
        assertTrue("第 3 帧稳定应触发拍摄", r3.shouldCapture)
        assertEquals(1f, r3.progress, 0.01f)

        // 冷却期内即使持续稳定也不应再次触发
        val cool1 = tracker.update(steadyQuad, 2000L)
        assertFalse(cool1.shouldCapture)
        assertEquals(0f, cool1.progress, 0.001f)

        // 冷却期结束后重新计数并可再次触发
        assertFalse(tracker.update(steadyQuad, 3100L).shouldCapture)
        assertFalse(tracker.update(steadyQuad, 3350L).shouldCapture)
        assertTrue(tracker.update(steadyQuad, 3600L).shouldCapture)
    }

    @Test
    fun `剧烈抖动或丢失文档边框会重置稳定计数`() {
        val tracker = QuadStabilityTracker(requiredStableFrames = 3, maxCornerDelta = 0.03f)
        tracker.update(steadyQuad, 100L)
        tracker.update(steadyQuad, 300L)

        val shifted = steadyQuad.map { NormPoint(it.x + 0.08f, it.y) }
        val afterShift = tracker.update(shifted, 500L)
        assertFalse(afterShift.shouldCapture)
        assertEquals(1f / 3f, afterShift.progress, 0.01f)

        val afterNull = tracker.update(null, 700L)
        assertFalse(afterNull.shouldCapture)
        assertEquals(0f, afterNull.progress, 0.001f)
    }

    @Test
    fun `过小面积或异常顶点数被忽略`() {
        val tracker = QuadStabilityTracker(requiredStableFrames = 2, minQuadArea = 0.08f)
        val tiny = listOf(
            NormPoint(0.4f, 0.4f),
            NormPoint(0.45f, 0.4f),
            NormPoint(0.45f, 0.45f),
            NormPoint(0.4f, 0.45f)
        )
        assertEquals(0f, tracker.update(tiny, 100L).progress, 0.001f)
        assertEquals(0f, tracker.update(tiny.take(3), 200L).progress, 0.001f)
    }
}

