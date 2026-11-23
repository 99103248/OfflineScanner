package com.scanner.offline.engine.export

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 纯文本编码检测与解码器：
 * 1. 优先识别 UTF-8 / UTF-16LE / UTF-16BE BOM；
 * 2. 无 BOM 时用严格模式尝试 UTF-8 解码；
 * 3. 若包含非法 UTF-8 字节序列，自动回退到 GB18030（兼容 GBK / GB2312 中文文本）。
 */
object TextCharsetDetector {

    private val GB18030: Charset = runCatching { Charset.forName("GB18030") }
        .getOrElse { Charsets.UTF_8 }

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        // UTF-8 BOM
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }

        // UTF-16 LE BOM
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte()
        ) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }

        // UTF-16 BE BOM
        if (bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte()
        ) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        // 严格 UTF-8 验证
        val utf8Decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val utf8Result = runCatching {
            utf8Decoder.decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
        if (utf8Result != null) return utf8Result

        // 中文 Windows / 本地文件常见 GBK / GB18030 回退
        return String(bytes, GB18030)
    }
}

