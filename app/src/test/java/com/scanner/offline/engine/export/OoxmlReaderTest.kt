package com.scanner.offline.engine.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OoxmlReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `readDocxTexts 能读取 OoxmlWriter 生成的多页 docx`() {
        val docx = tempFolder.newFile("multi.docx")
        OoxmlWriter.writeDocx(
            texts = listOf("第一页\n行二", "第二页英文"),
            output = docx
        )
        val pages = OoxmlReader.readDocxTexts(docx)
        assertEquals(2, pages.size)
        assertTrue(pages[0].contains("第一页"))
        assertTrue(pages[1].contains("第二页英文"))
    }

    @Test
    fun `parseDocumentXml 转义字符还原正确`() {
        val xml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:body>
            <w:p><w:r><w:t>a&amp;b&lt;c&gt;</w:t></w:r></w:p>
            </w:body>
            </w:document>
        """.trimIndent()
        val text = OoxmlReader.parseDocumentXml(xml).single()
        assertEquals("a&b<c>", text)
    }

    @Test
    fun `parseDocumentXml 同时兼容旧版空段落分页与新版 w_br 分页符`() {
        val legacyXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:body>
            <w:p><w:r><w:t>旧版第一页</w:t></w:r></w:p><w:p/><w:p><w:r><w:t>旧版第二页</w:t></w:r></w:p>
            </w:body>
            </w:document>
        """.trimIndent()
        val legacyPages = OoxmlReader.parseDocumentXml(legacyXml)
        assertEquals(listOf("旧版第一页", "旧版第二页"), legacyPages)

        val modernXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:body>
            <w:p><w:r><w:t>新版第一页</w:t></w:r></w:p><w:p><w:r><w:br w:type="page"/></w:r></w:p><w:p><w:r><w:t>新版第二页</w:t></w:r></w:p>
            </w:body>
            </w:document>
        """.trimIndent()
        val modernPages = OoxmlReader.parseDocumentXml(modernXml)
        assertEquals(listOf("新版第一页", "新版第二页"), modernPages)
    }
}
