package com.scanner.offline

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ScannerApplication : Application() {
    // OpenCV 改为首次使用时懒加载（见 engine/image/OpenCvLoader.kt），
    // 避免在冷启动主线程同步加载几十 MB 的 native 库。
}
