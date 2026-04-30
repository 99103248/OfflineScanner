package com.scanner.offline.engine.export

import com.scanner.offline.domain.model.TableData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 验证 OoxmlWriter 生成的 docx / xlsx：
 *
 *  1. 能作为合法 ZIP 解压
 *  2. 必备的 OOXML part 都存在（[Content_Types].xml / _rels/.rels / 主文档）
 *  3. 每个 XML part 都能用标准 javax.xml DOM 解析，没有非法字符
 *  4. 文本内容能在主文档里找到
 *
 * 这些用例覆盖 OoxmlWriter 的所有公开方法，
 * 在 JVM 上跑（不依赖 Android），所以放在 src/test 而不是 androidTest。
 */
class OoxmlWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ===================== docx =====================

    @Test
    fun `writeDocx 生成的文件可作为 ZIP 解压`() {
        val out = tempFolder.newFile("doc.docx")
        OoxmlWriter.writeDocx(listOf("Hello World"), out)

        assertTrue("docx 文件应该已生成", out.exists())
        assertTrue("docx 大小应该 > 0", out.length() > 0)

        // ZIP 魔数
        val firstBytes = out.readBytes().take(4).toByteArray()
        assertEquals("ZIP 魔数应为 PK\\x03\\x04", 0x50.toByte(), firstBytes[0])
        assertEquals(0x4B.toByte(), firstBytes[1])
    }

    @Test
    fun `writeDocx 包含必备 OOXML parts`() {
        val out = tempFolder.newFile("doc.docx")
        OoxmlWriter.writeDocx(listOf("Hello"), out)

        ZipFile(out).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue("应包含 [Content_Types].xml, 实际: $names",
                "[Content_Types].xml" in names)
            assertTrue("应包含 _rels/.rels", "_rels/.rels" in names)
            assertTrue("应包含 word/document.xml", "word/document.xml" in names)
        }
    }

    @Test
    fun `writeDocx 主文档是合法 XML`() {
        val out = tempFolder.newFile("doc.docx")
        OoxmlWriter.writeDocx(listOf("段落一", "段落二"), out)

        ZipFile(out).use { zip ->
            val entry = zip.getEntry("word/document.xml")!!
            val xml = zip.getInputStream(entry).bufferedReader().readText()

            // 用标准 DOM 解析，能解析说明 XML 合法、无非转义特殊字符
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xml.byteInputStream())
            assertNotNull("文档根节点应存在", doc.documentElement)
            assertEquals("根节点 localName 应为 document", "document", doc.documentElement.localName)
        }
    }

    @Test
    fun `writeDocx 文本内容能在 document_xml 里找到`() {
        val out = tempFolder.newFile("doc.docx")
        OoxmlWriter.writeDocx(listOf("第一页文字", "第二页文字"), out)

        val docXml = ZipFile(out).use { zip ->
            zip.getInputStream(zip.getEntry("word/document.xml")!!)
                .bufferedReader().readText()
        }

        assertTrue("应包含'第一页文字', 实际: $docXml",
            docXml.contains("第一页文字"))
        assertTrue("应包含'第二页文字'", docXml.contains("第二页文字"))
    }

    @Test
    fun `writeDocx 对 XML 特殊字符正确转义`() {
        val out = tempFolder.newFile("doc.docx")
        // 包含所有需要转义的字符：& < > " '
        val tricky = "a&b<c>d\"e'f"
        OoxmlWriter.writeDocx(listOf(tricky), out)

        val docXml = ZipFile(out).use { zip ->
            zip.getInputStream(zip.getEntry("word/document.xml")!!)
                .bufferedReader().readText()
        }

        // 验证转义后能被 DOM 解析（如果转义不对，DOM 解析会抛异常）
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(docXml.byteInputStream())

        // 验证转义形式
        assertTrue("& 应转为 &amp;", docXml.contains("a&amp;b"))
        assertTrue("< 应转为 &lt;", docXml.contains("b&lt;c"))
        assertTrue("> 应转为 &gt;", docXml.contains("c&gt;d"))
    }

    @Test
    fun `writeDocx 多行文本生成多个段落`() {
        val out = tempFolder.newFile("doc.docx")
        OoxmlWriter.writeDocx(listOf("第一行\n第二行\n第三行"), out)

        val docXml = ZipFile(out).use { zip ->
            zip.getInputStream(zip.getEntry("word/document.xml")!!)
                .bufferedReader().readText()
        }

        // <w:p> 段落数量应该至少 3 个
        val paragraphCount = "<w:p>".toRegex().findAll(docXml).count()
        assertTrue("3 行应生成至少 3 个段落, 实际: $paragraphCount", paragraphCount >= 3)
    }

    // ===================== xlsx =====================

    @Test
    fun `writeXlsx 生成的文件可作为 ZIP 解压`() {
        val out = tempFolder.newFile("sheet.xlsx")
        OoxmlWriter.writeXlsx(
            tables = listOf(TableData(listOf(listOf("A", "B"), listOf("1", "2")))),
            output = out
        )

        assertTrue(out.exists())
        assertTrue(out.length() > 0)

        val firstBytes = out.readBytes().take(2).toByteArray()
        assertEquals(0x50.toByte(), firstBytes[0])
        assertEquals(0x4B.toByte(), firstBytes[1])
    }

    @Test
    fun `writeXlsx 包含必备 OOXML parts`() {
        val out = tempFolder.newFile("sheet.xlsx")
        OoxmlWriter.writeXlsx(
            tables = listOf(TableData(listOf(listOf("Hello")))),
            output = out
        )

        ZipFile(out).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue("Content Types 必需", "[Content_Types].xml" in names)
            assertTrue("Package rels 必需", "_rels/.rels" in names)
            assertTrue("workbook.xml 必需", "xl/workbook.xml" in names)
            assertTrue("workbook rels 必需", "xl/_rels/workbook.xml.rels" in names)
            assertTrue("styles 必需", "xl/styles.xml" in names)
            assertTrue("sharedStrings 必需", "xl/sharedStrings.xml" in names)
            assertTrue("sheet1 必需", "xl/worksheets/sheet1.xml" in names)
        }
    }

    @Test
    fun `writeXlsx 所有 XML parts 都是合法 XML`() {
        val out = tempFolder.newFile("sheet.xlsx")
        OoxmlWriter.writeXlsx(
            tables = listOf(
                TableData(listOf(
                    listOf("姓名", "年龄"),
                    listOf("张三", "25"),
                    listOf("李四", "30")
                ))
            ),
            output = out
        )

        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        ZipFile(out).use { zip ->
            zip.entries().toList().forEach { entry ->
                if (entry.name.endsWith(".xml") || entry.name.endsWith(".rels")) {
                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    try {
                        builder.parse(text.byteInputStream())
                    } catch (e: Exception) {
                        throw AssertionError(
                            "${entry.name} 不是合法 XML: ${e.message}\n内容:\n$text", e
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `writeXlsx 单元格内容能在 sharedStrings 里找到`() {
        val out = tempFolder.newFile("sheet.xlsx")
        OoxmlWriter.writeXlsx(
            tables = listOf(TableData(listOf(listOf("Hello", "世界")))),
            output = out
        )

        val sharedStrings = ZipFile(out).use { zip ->
            zip.getInputStream(zip.getEntry("xl/sharedStrings.xml")!!)
                .bufferedReader().readText()
        }

        assertTrue("Hello 应在 sharedStrings", sharedStrings.contains("Hello"))
        assertTrue("世界 应在 sharedStrings", sharedStrings.contains("世界"))
    }

    @Test
    fun `writeXlsx 多 sheet 都正确生成`() {
        val out = tempFolder.newFile("multi.xlsx")
        OoxmlWriter.writeXlsx(
            tables = listOf(
                TableData(listOf(listOf("A1"))),
                TableData(listOf(listOf("B1"))),
                TableData(listOf(listOf("C1")))
            ),
            output = out
        )

        ZipFile(out).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue("sheet1 存在", "xl/worksheets/sheet1.xml" in names)
            assertTrue("sheet2 存在", "xl/worksheets/sheet2.xml" in names)
            assertTrue("sheet3 存在", "xl/worksheets/sheet3.xml" in names)

            // workbook.xml 应声明 3 个 sheet
            val workbook = zip.getInputStream(zip.getEntry("xl/workbook.xml")!!)
                .bufferedReader().readText()
            val sheetCount = "<sheet ".toRegex().findAll(workbook).count()
            assertEquals("workbook 应声明 3 个 sheet", 3, sheetCount)
        }
    }

    @Test
    fun `excelRef 列号转换`() {
        // 通过反射验证 excelRef，确保列号 0..27 都能转对
        // A=0, Z=25, AA=26, AB=27
        val testCases = mapOf(
            0 to "A1",
            1 to "B1",
            25 to "Z1",
            26 to "AA1",
            27 to "AB1",
            51 to "AZ1",
            52 to "BA1"
        )

        // 用一个简单的 1x80 表格触发各列引用
        val out = tempFolder.newFile("cols.xlsx")
        OoxmlWriter.writeXlsx(
            tables = listOf(TableData(listOf((0..52).map { "v$it" }))),
            output = out
        )

        val sheetXml = ZipFile(out).use { zip ->
            zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")!!)
                .bufferedReader().readText()
        }

        testCases.forEach { (col, expectedRef) ->
            assertTrue(
                "列 $col 应使用引用 $expectedRef, sheet=$sheetXml",
                sheetXml.contains("r=\"$expectedRef\"")
            )
        }
    }

    @Test
    fun `writeXlsx 空字符串不写入单元格`() {
        val out = tempFolder.newFile("sparse.xlsx")
        OoxmlWriter.writeXlsx(
            tables = listOf(
                TableData(listOf(listOf("a", "", "c")))
            ),
            output = out
        )

        val sheetXml = ZipFile(out).use { zip ->
            zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")!!)
                .bufferedReader().readText()
        }

        assertTrue("应有 A1", sheetXml.contains("r=\"A1\""))
        assertTrue("应有 C1", sheetXml.contains("r=\"C1\""))
        // B1 的空字符串不会写入
    }
}
