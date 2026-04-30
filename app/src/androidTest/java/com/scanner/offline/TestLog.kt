package com.scanner.offline

import android.util.Log

/**
 * 测试日志工具：所有 OCR / 导出测试输出都用统一前缀 `TestLog`，
 * 这样 `adb logcat -s TestLog:*` 可以方便过滤。
 */
object TestLog {
    private const val TAG = "TestLog"

    fun section(title: String) {
        Log.i(TAG, "")
        Log.i(TAG, "===== $title =====")
    }

    fun kv(key: String, value: String) {
        Log.i(TAG, "  $key: $value")
    }
}
