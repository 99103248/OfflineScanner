package com.scanner.offline.engine.export

/**
 * 纯 Kotlin PDF 页码范围解析器。
 *
 * 支持语法（1-based 输入，返回 0-based 索引列表，保持输入顺序并去重）：
 * - 单页：`1, 3, 5`
 * - 闭区间：`2-5`、`5-2`（自动正向展开）
 * - 半开区间：`3-`（第 3 页至末页）、`-3`（第 1 页至第 3 页）
 * - 兼容中文分隔符：`，`、`、`、`；`、`~`、`～`、`—`
 */
object PdfPageRangeParser {

    fun parse(raw: String, pageCount: Int): List<Int> {
        if (pageCount <= 0 || raw.isBlank()) return emptyList()
        val normalized = raw
            .replace('，', ',')
            .replace('、', ',')
            .replace('；', ',')
            .replace(';', ',')
            .replace('～', '-')
            .replace('~', '-')
            .replace('—', '-')
            .replace('–', '-')

        val tokens = normalized.split(Regex("[,\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = LinkedHashSet<Int>()
        for (token in tokens) {
            if ('-' in token) {
                val dashIdx = token.indexOf('-')
                // 忽略含多个连字符的畸形片段（如 "1-2-3"）
                if (token.indexOf('-', dashIdx + 1) >= 0) continue
                val leftStr = token.substring(0, dashIdx).trim()
                val rightStr = token.substring(dashIdx + 1).trim()
                if (leftStr.isEmpty() && rightStr.isEmpty()) continue

                val start = if (leftStr.isEmpty()) 1 else leftStr.toIntOrNull() ?: continue
                val end = if (rightStr.isEmpty()) pageCount else rightStr.toIntOrNull() ?: continue
                val lo = minOf(start, end).coerceAtLeast(1)
                val hi = maxOf(start, end).coerceAtMost(pageCount)
                if (lo <= hi) {
                    for (p in lo..hi) {
                        result += (p - 1)
                    }
                }
            } else {
                val page = token.toIntOrNull() ?: continue
                if (page in 1..pageCount) {
                    result += (page - 1)
                }
            }
        }
        return result.toList()
    }
}

