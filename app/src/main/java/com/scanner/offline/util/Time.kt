package com.scanner.offline.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Time {
    private val full = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val short = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    fun formatFull(ts: Long): String = full.format(Date(ts))
    fun formatDate(ts: Long): String = date.format(Date(ts))
    fun formatShort(ts: Long): String = short.format(Date(ts))

    fun nowDocName(prefix: String = "扫描"): String {
        val s = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$s"
    }
}
