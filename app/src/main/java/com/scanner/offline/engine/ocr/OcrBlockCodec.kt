package com.scanner.offline.engine.ocr

import com.scanner.offline.domain.model.BoundingBox
import com.scanner.offline.domain.model.TextBlock

/**
 * 纯 Kotlin 的 [TextBlock] 列表序列化/反序列化工具（不依赖 Android `org.json`，可在纯 JVM 单测直接使用）。
 *
 * 每行编码一个 [TextBlock]，字段以 `\t` 分隔：
 * `confidence \t left \t top \t right \t bottom \t normLeft \t normTop \t normRight \t normBottom \t escapedText`
 */
object OcrBlockCodec {

    fun encode(blocks: List<TextBlock>): String {
        if (blocks.isEmpty()) return ""
        return blocks.joinToString("\n") { block ->
            val box = block.boundingBox
            val l = box?.left ?: -1
            val t = box?.top ?: -1
            val r = box?.right ?: -1
            val b = box?.bottom ?: -1
            val nl = box?.normLeft ?: -1f
            val nt = box?.normTop ?: -1f
            val nr = box?.normRight ?: -1f
            val nb = box?.normBottom ?: -1f
            buildString {
                append(block.confidence).append('\t')
                append(l).append('\t')
                append(t).append('\t')
                append(r).append('\t')
                append(b).append('\t')
                append(nl).append('\t')
                append(nt).append('\t')
                append(nr).append('\t')
                append(nb).append('\t')
                append(escape(block.text))
            }
        }
    }

    fun decode(raw: String?): List<TextBlock> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split('\t', limit = 10)
                if (parts.size < 10) return@mapNotNull null
                val conf = parts[0].toFloatOrNull() ?: 1f
                val l = parts[1].toIntOrNull() ?: -1
                val t = parts[2].toIntOrNull() ?: -1
                val r = parts[3].toIntOrNull() ?: -1
                val b = parts[4].toIntOrNull() ?: -1
                val nl = parts[5].toFloatOrNull() ?: -1f
                val nt = parts[6].toFloatOrNull() ?: -1f
                val nr = parts[7].toFloatOrNull() ?: -1f
                val nb = parts[8].toFloatOrNull() ?: -1f
                val text = unescape(parts[9])
                val box = if (r > l && b > t && l >= 0 && t >= 0) {
                    BoundingBox(
                        left = l,
                        top = t,
                        right = r,
                        bottom = b,
                        normLeft = nl,
                        normTop = nt,
                        normRight = nr,
                        normBottom = nb
                    )
                } else null
                TextBlock(text = text, confidence = conf, boundingBox = box)
            }
            .toList()
    }

    private fun escape(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun unescape(s: String): String = buildString(s.length) {
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    '\\' -> append('\\')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> {
                        append(ch)
                        append(next)
                    }
                }
                i += 2
            } else {
                append(ch)
                i++
            }
        }
    }
}

