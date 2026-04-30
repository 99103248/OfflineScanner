package com.scanner.offline

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader

@HiltAndroidApp
class ScannerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initOpenCv()
    }

    private fun initOpenCv() {
        // 4.9.0+ 使用 initLocal()：直接加载随 APK 打包的 native library，
        // 不再需要历史上的 OpenCV Manager 服务，也不需要异步回调。
        val ok = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        if (ok) {
            Log.i(TAG, "OpenCV ${OpenCVLoader.OPENCV_VERSION} loaded")
        } else {
            Log.e(TAG, "OpenCV failed to load — 边缘检测降级到纯 Bitmap 兜底实现")
        }
    }

    companion object {
        private const val TAG = "ScannerApp"
    }
}
