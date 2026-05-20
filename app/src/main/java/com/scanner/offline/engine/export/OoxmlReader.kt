package com.scanner.offline.engine.export

import java.io.File
import java.util.zip.ZipFile

/**
 * 从 .docx 中提取纯文本（与 [OoxmlWriter] 输出格式兼容）。
 */
internal object OoxmlReader {

    private val WT_REGEX = Regex("<w:t[^>]*>([\\s\\S]*?)</w:t>")
    private val PAGE_BREAK = Regex("<w:p\\s*/>")

    fun readDocxTexts(file: File): List<String> {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml")
                ?: error("无效的 Word 文档：缺少 word/document.xml")
            val xml = zip.getInputStream(entry).bufferedReader().readText()
            return parseDocumentXml(xml)
        }
    }

    internal fun parseDocumentXml(xml: String): List<String> {
        val body = xml.substringAfter("<w:body>", "").substringBefore("</w:body>")
        if (body.isBlank()) return listOf("")

        val sections = if (PAGE_BREAK.containsMatchIn(body)) {
            body.split(PAGE_BREAK).filter { it.isNotBlank() }
        } else {
            listOf(body)
        }

        return sections.map { section -> extractPlainText(section).trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("") }
    }

    private fun extractPlainText(sectionXml: String): String {
        val paragraphs = sectionXml.split(Regex("<w:p[^>]*>"))
            .filter { it.contains("<w:t") }
        if (paragraphs.isEmpty()) {
            return WT_REGEX.findAll(sectionXml).joinToString("") { unescapeXml(it.groupValues[1]) }
        }
        return paragraphs.joinToString("\n") { para ->
            WT_REGEX.findAll(para).joinToString("") { unescapeXml(it.groupValues[1]) }
        }
    }

    private fun unescapeXml(text: String): String =
        text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
}
