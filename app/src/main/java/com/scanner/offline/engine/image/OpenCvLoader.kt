package com.scanner.offline.engine.image

import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * OpenCV 懒加载。
 *
 * `OpenCVLoader.initLocal()` 会加载几十 MB 的 native 库，在部分设备/模拟器上
 * 需要数秒。放在 Application.onCreate 会拖慢冷启动，因此改为首次真正用到
 * 图像算法时再加载；加载失败时各算法自动走 Bitmap 兜底实现。
 */
object OpenCvLoader {

    @Volatile
    private var loaded: Boolean? = null

    @Synchronized
    fun ensureLoaded(): Boolean {
        loaded?.let { return it }
        val ok = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        loaded = ok
        if (ok) {
            Log.i(TAG, "OpenCV ${OpenCVLoader.OPENCV_VERSION} loaded")
        } else {
            Log.e(TAG, "OpenCV failed to load — 使用 Bitmap 兜底实现")
        }
        return ok
    }

    private const val TAG = "OpenCvLoader"
}
