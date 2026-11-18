package com.scanner.offline.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Time {
    private val fullFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val shortFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US)
    private val docNameFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)

    fun formatFull(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(fullFormatter)

    fun formatDate(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(dateFormatter)

    fun formatShort(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(shortFormatter)

    fun nowDocName(prefix: String = "扫描"): String {
        val s = Instant.now().atZone(ZoneId.systemDefault()).format(docNameFormatter)
        return "${prefix}_$s"
    }
}
