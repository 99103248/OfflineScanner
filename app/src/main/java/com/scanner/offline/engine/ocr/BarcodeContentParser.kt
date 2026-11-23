package com.scanner.offline.engine.ocr

/**
 * 离线扫码内容结构化解析器（不联网，仅做本地协议识别）。
 */
enum class BarcodeContentType(val displayName: String) {
    URL("网址链接"),
    WIFI("Wi-Fi 配置"),
    PHONE("电话号码"),
    EMAIL("电子邮箱"),
    VCARD("联系人名片"),
    TEXT("纯文本")
}

data class ParsedBarcodeContent(
    val type: BarcodeContentType,
    val rawValue: String,
    val summary: String,
    val fields: List<Pair<String, String>> = emptyList(),
    val primaryCopyValue: String = rawValue,
    val primaryCopyLabel: String = "复制内容"
)

object BarcodeContentParser {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PHONE_REGEX = Regex("^\\+?[0-9][0-9\\s-]{5,18}[0-9]$")

    fun parse(rawValue: String): ParsedBarcodeContent {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            return ParsedBarcodeContent(BarcodeContentType.TEXT, rawValue, "")
        }

        // Wi-Fi: WIFI:T:WPA;S:MyWifi;P:secret123;H:false;;
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            return parseWifi(trimmed)
        }

        // URL
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return ParsedBarcodeContent(
                type = BarcodeContentType.URL,
                rawValue = trimmed,
                summary = trimmed,
                fields = listOf("链接" to trimmed),
                primaryCopyValue = trimmed,
                primaryCopyLabel = "复制链接"
            )
        }

        // Mailto or plain email
        if (trimmed.startsWith("mailto:", ignoreCase = true)) {
            val addr = trimmed.substringAfter(':').substringBefore('?').trim()
            return ParsedBarcodeContent(
                type = BarcodeContentType.EMAIL,
                rawValue = trimmed,
                summary = addr.ifBlank { trimmed },
                fields = listOf("邮箱" to addr.ifBlank { trimmed }),
                primaryCopyValue = addr.ifBlank { trimmed },
                primaryCopyLabel = "复制邮箱"
            )
        }
        if (EMAIL_REGEX.matches(trimmed)) {
            return ParsedBarcodeContent(
                type = BarcodeContentType.EMAIL,
                rawValue = trimmed,
                summary = trimmed,
                fields = listOf("邮箱" to trimmed),
                primaryCopyValue = trimmed,
                primaryCopyLabel = "复制邮箱"
            )
        }

        // Tel or plain phone
        if (trimmed.startsWith("tel:", ignoreCase = true)) {
            val phone = trimmed.substringAfter(':').trim()
            return ParsedBarcodeContent(
                type = BarcodeContentType.PHONE,
                rawValue = trimmed,
                summary = phone.ifBlank { trimmed },
                fields = listOf("号码" to phone.ifBlank { trimmed }),
                primaryCopyValue = phone.ifBlank { trimmed },
                primaryCopyLabel = "复制号码"
            )
        }
        if (PHONE_REGEX.matches(trimmed)) {
            return ParsedBarcodeContent(
                type = BarcodeContentType.PHONE,
                rawValue = trimmed,
                summary = trimmed,
                fields = listOf("号码" to trimmed),
                primaryCopyValue = trimmed,
                primaryCopyLabel = "复制号码"
            )
        }

        // vCard
        if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)) {
            return parseVCard(trimmed)
        }

        return ParsedBarcodeContent(
            type = BarcodeContentType.TEXT,
            rawValue = trimmed,
            summary = trimmed
        )
    }

    private fun parseWifi(raw: String): ParsedBarcodeContent {
        val body = raw.substring(5)
        val map = linkedMapOf<String, String>()
        val parts = splitUnescaped(body, ';')
        for (part in parts) {
            val colon = part.indexOf(':')
            if (colon <= 0) continue
            val k = part.substring(0, colon).uppercase()
            val v = unescapeWifi(part.substring(colon + 1))
            if (v.isNotEmpty()) map[k] = v
        }
        val ssid = map["S"].orEmpty()
        val pass = map["P"].orEmpty()
        val enc = map["T"].orEmpty().ifBlank { "开放/未指定" }
        val fields = buildList {
            if (ssid.isNotEmpty()) add("网络名称" to ssid)
            add("加密方式" to enc)
            if (pass.isNotEmpty()) add("密码" to pass)
        }
        return ParsedBarcodeContent(
            type = BarcodeContentType.WIFI,
            rawValue = raw,
            summary = if (ssid.isNotEmpty()) "Wi-Fi：$ssid" else raw,
            fields = fields,
            primaryCopyValue = pass.ifBlank { ssid.ifBlank { raw } },
            primaryCopyLabel = if (pass.isNotBlank()) "复制密码" else "复制名称"
        )
    }

    private fun parseVCard(raw: String): ParsedBarcodeContent {
        val fields = mutableListOf<Pair<String, String>>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val colon = trimmed.indexOf(':')
            if (colon <= 0) return@forEach
            val key = trimmed.substring(0, colon).substringBefore(';').uppercase()
            val value = trimmed.substring(colon + 1).trim()
            if (value.isEmpty()) return@forEach
            when (key) {
                "FN" -> fields += "姓名" to value
                "ORG" -> fields += "机构" to value
                "TEL" -> fields += "电话" to value
                "EMAIL" -> fields += "邮箱" to value
            }
        }
        val summary = fields.firstOrNull { it.first == "姓名" }?.second ?: "联系人名片"
        return ParsedBarcodeContent(
            type = BarcodeContentType.VCARD,
            rawValue = raw,
            summary = summary,
            fields = fields,
            primaryCopyValue = raw,
            primaryCopyLabel = "复制名片"
        )
    }

    private fun splitUnescaped(input: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var escaped = false
        for (ch in input) {
            if (escaped) {
                sb.append('\\').append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == delimiter) {
                out += sb.toString()
                sb.clear()
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    private fun unescapeWifi(s: String): String = buildString(s.length) {
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                append(s[i + 1])
                i += 2
            } else {
                append(s[i])
                i++
            }
        }
    }
}

