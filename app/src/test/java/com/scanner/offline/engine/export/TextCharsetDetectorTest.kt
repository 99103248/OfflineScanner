package com.scanner.offline.engine.export

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class TextCharsetDetectorTest {

    @Test
    fun `解码无 BOM 的 UTF-8 中文文本`() {
        val text = "离线扫描测试文档 2026"
        assertEquals(text, TextCharsetDetector.decode(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `解码带 UTF-8 BOM 的文本并去除 BOM`() {
        val text = "带BOM的中文"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + text.toByteArray(Charsets.UTF_8)
        assertEquals(text, TextCharsetDetector.decode(bytes))
    }

    @Test
    fun `解码 UTF-16LE 与 UTF-16BE 带 BOM 文本`() {
        val text = "Hello 简体中文"
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)
        assertEquals(text, TextCharsetDetector.decode(le))
        assertEquals(text, TextCharsetDetector.decode(be))
    }

    @Test
    fun `解码 GBK 编码中文自动回退不乱码`() {
        val text = "合同甲方：测试有限公司\n金额：人民币壹万元整"
        val gbkBytes = text.toByteArray(Charset.forName("GB18030"))
        assertEquals(text, TextCharsetDetector.decode(gbkBytes))
    }

    @Test
    fun `空字节数组返回空字符串`() {
        assertEquals("", TextCharsetDetector.decode(ByteArray(0)))
    }
}

