package com.scanner.offline.engine.image

import kotlin.math.abs
import kotlin.math.hypot

/**
 * 归一化二维点（纯 Kotlin，不依赖 android.graphics.PointF，便于 JVM 单测）。
 */
data class NormPoint(val x: Float, val y: Float)

/**
 * 相机自动拍摄稳定度追踪器。
 *
 * 当实时文档边缘检测在连续 [requiredStableFrames] 帧内都检出有效四边形，
 * 且相邻帧四个角点的最大欧氏位移均不超过 [maxCornerDelta] 时，判定画面已稳定，
 * 触发自动拍摄。触发后自动重置状态并进入冷却期，避免连续重复拍照。
 */
class QuadStabilityTracker(
    private val requiredStableFrames: Int = 5,
    private val maxCornerDelta: Float = 0.035f,
    private val minQuadArea: Float = 0.08f,
    private val cooldownMs: Long = 1800L
) {
    private var lastQuad: List<NormPoint>? = null
    private var stableCount: Int = 0
    private var lastTriggerMs: Long = Long.MIN_VALUE

    data class UpdateResult(
        val progress: Float,
        val shouldCapture: Boolean
    )

    fun reset() {
        lastQuad = null
        stableCount = 0
    }

    fun update(quad: List<NormPoint>?, nowMs: Long): UpdateResult {
        if (quad == null || quad.size != 4 || polygonArea(quad) < minQuadArea) {
            reset()
            return UpdateResult(progress = 0f, shouldCapture = false)
        }
        if (lastTriggerMs != Long.MIN_VALUE && nowMs - lastTriggerMs < cooldownMs) {
            lastQuad = quad
            stableCount = 0
            return UpdateResult(progress = 0f, shouldCapture = false)
        }

        val prev = lastQuad
        if (prev == null) {
            lastQuad = quad
            stableCount = 1
        } else {
            val maxShift = (0..3).maxOf { i ->
                hypot((quad[i].x - prev[i].x).toDouble(), (quad[i].y - prev[i].y).toDouble()).toFloat()
            }
            if (maxShift <= maxCornerDelta) {
                stableCount++
            } else {
                stableCount = 1
            }
            lastQuad = quad
        }

        val required = requiredStableFrames.coerceAtLeast(1)
        val progress = (stableCount.toFloat() / required).coerceIn(0f, 1f)
        return if (stableCount >= required) {
            lastTriggerMs = nowMs
            reset()
            UpdateResult(progress = 1f, shouldCapture = true)
        } else {
            UpdateResult(progress = progress, shouldCapture = false)
        }
    }

    companion object {
        /** 鞋带公式计算四边形归一化面积（0..1）。 */
        fun polygonArea(pts: List<NormPoint>): Float {
            if (pts.size < 3) return 0f
            var sum = 0.0
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                sum += (a.x.toDouble() * b.y.toDouble()) - (b.x.toDouble() * a.y.toDouble())
            }
            return (abs(sum) * 0.5).toFloat()
        }
    }
}

