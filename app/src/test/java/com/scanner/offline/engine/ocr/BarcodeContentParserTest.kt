package com.scanner.offline.engine.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeContentParserTest {

    @Test
    fun `解析标准 Wi-Fi 二维码含转义分号`() {
        val parsed = BarcodeContentParser.parse("WIFI:T:WPA;S:Office\\;Guest;P:secret99;H:false;;")
        assertEquals(BarcodeContentType.WIFI, parsed.type)
        assertEquals("secret99", parsed.primaryCopyValue)
        assertEquals("复制密码", parsed.primaryCopyLabel)
        assertTrue(parsed.fields.any { it.first == "网络名称" && it.second == "Office;Guest" })
        assertTrue(parsed.fields.any { it.first == "加密方式" && it.second == "WPA" })
    }

    @Test
    fun `解析网址与邮箱和电话`() {
        val url = BarcodeContentParser.parse("https://example.com/doc?id=42")
        assertEquals(BarcodeContentType.URL, url.type)
        assertEquals("复制链接", url.primaryCopyLabel)

        val email = BarcodeContentParser.parse("mailto:user@example.com?subject=hi")
        assertEquals(BarcodeContentType.EMAIL, email.type)
        assertEquals("user@example.com", email.primaryCopyValue)

        val phone = BarcodeContentParser.parse("tel:+86-13800138000")
        assertEquals(BarcodeContentType.PHONE, phone.type)
        assertEquals("+86-13800138000", phone.primaryCopyValue)
    }

    @Test
    fun `解析 vCard 名片与普通文本及空字符串`() {
        val vcard = BarcodeContentParser.parse(
            "BEGIN:VCARD\nVERSION:3.0\nFN:张三\nORG:离线科技\nTEL:13900001111\nEND:VCARD"
        )
        assertEquals(BarcodeContentType.VCARD, vcard.type)
        assertEquals("张三", vcard.summary)
        assertEquals(3, vcard.fields.size)

        val plain = BarcodeContentParser.parse("SN-2026-0001")
        assertEquals(BarcodeContentType.TEXT, plain.type)
        assertEquals("SN-2026-0001", plain.primaryCopyValue)

        val empty = BarcodeContentParser.parse("   ")
        assertEquals(BarcodeContentType.TEXT, empty.type)
    }
}

