package com.scanner.offline.engine.export

import com.scanner.offline.domain.model.TableData
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 简化版 OOXML 生成器 —— 不依赖 Apache POI。
 *
 * .docx / .xlsx 的本质是带预定义 XML 的 ZIP 包：
 *   - [Content_Types].xml         全局类型清单
 *   - _rels/.rels                 包级关系（指向主文档）
 *   - word/document.xml           Word 主体
 *   - xl/workbook.xml + xl/worksheets/sheet1.xml + xl/styles.xml + xl/sharedStrings.xml  Excel 主体
 *
 * 只生成最常用的"纯文本段落"和"无格式表格"，足够满足 OCR 文本输出场景，
 * 用 Word / Excel / WPS / 在线 Office 打开都能正确显示。
 *
 * 如果未来需要更复杂的格式（图片、合并单元格、字体等），可以再扩展，
 * 也可以在那时切换回 POI for Android。
 */
internal object OoxmlWriter {

    // ============================== Word ==============================

    fun writeDocx(texts: List<String>, output: File): File {
        FileOutputStream(output).use { os -> writeDocx(texts, os) }
        return output
    }

    fun writeDocx(texts: List<String>, output: OutputStream) {
        ZipOutputStream(output).also { zos ->
            zos.putString(
                "[Content_Types].xml",
                xmlProlog +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/word/document.xml\" " +
                "ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                "</Types>"
            )
            zos.putString(
                "_rels/.rels",
                xmlProlog +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" " +
                "Target=\"word/document.xml\"/>" +
                "</Relationships>"
            )

            val body = StringBuilder()
            texts.forEachIndexed { idx, text ->
                if (idx > 0) {
                    body.append("<w:p/>")
                }
                text.split('\n').forEach { line ->
                    body.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                        .append(escapeXml(line))
                        .append("</w:t></w:r></w:p>")
                }
            }

            zos.putString(
                "word/document.xml",
                xmlProlog +
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                "<w:body>$body</w:body>" +
                "</w:document>"
            )
            zos.finish()
            // 注意：不 close 外层 OutputStream，由调用方决定
        }
    }

    // ============================== Excel ==============================

    fun writeXlsx(tables: List<TableData>, output: File): File {
        FileOutputStream(output).use { os -> writeXlsx(tables, os) }
        return output
    }

    fun writeXlsx(tables: List<TableData>, output: OutputStream) {
        val stringIndex = LinkedHashMap<String, Int>()
        tables.forEach { table ->
            table.rows.forEach { row -> row.forEach { put(stringIndex, it) } }
        }
        val sharedStringsXml = buildSharedStrings(stringIndex)

        ZipOutputStream(output).also { zos ->
            zos.putString(
                "[Content_Types].xml",
                buildContentTypes(tables.size)
            )
            zos.putString(
                "_rels/.rels",
                xmlProlog +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" " +
                "Target=\"xl/workbook.xml\"/>" +
                "</Relationships>"
            )
            zos.putString(
                "xl/_rels/workbook.xml.rels",
                buildWorkbookRels(tables.size)
            )
            zos.putString(
                "xl/workbook.xml",
                buildWorkbook(tables.size)
            )
            zos.putString(
                "xl/styles.xml",
                xmlProlog +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>" +
                "<borders count=\"1\"><border/></borders>" +
                "<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>" +
                "<cellXfs count=\"1\"><xf/></cellXfs>" +
                "</styleSheet>"
            )
            zos.putString("xl/sharedStrings.xml", sharedStringsXml)

            tables.forEachIndexed { idx, table ->
                zos.putString(
                    "xl/worksheets/sheet${idx + 1}.xml",
                    buildSheet(table, stringIndex)
                )
            }
            zos.finish()
        }
    }

    private fun put(map: LinkedHashMap<String, Int>, value: String) {
        if (value !in map) map[value] = map.size
    }

    private fun buildSharedStrings(map: Map<String, Int>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            .append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
            .append("count=\"${map.size}\" uniqueCount=\"${map.size}\">")
        map.keys.forEach { k ->
            sb.append("<si><t xml:space=\"preserve\">").append(escapeXml(k)).append("</t></si>")
        }
        sb.append("</sst>")
        return sb.toString()
    }

    private fun buildContentTypes(sheetCount: Int): String {
        val sheets = (1..sheetCount).joinToString("") { i ->
            "<Override PartName=\"/xl/worksheets/sheet$i.xml\" " +
            "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        }
        return xmlProlog +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" " +
            "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" " +
            "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            "<Override PartName=\"/xl/sharedStrings.xml\" " +
            "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>" +
            sheets +
            "</Types>"
    }

    private fun buildWorkbookRels(sheetCount: Int): String {
        val rels = StringBuilder()
        for (i in 1..sheetCount) {
            rels.append("<Relationship Id=\"rId$i\" ")
                .append("Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" ")
                .append("Target=\"worksheets/sheet$i.xml\"/>")
        }
        rels.append("<Relationship Id=\"rId${sheetCount + 1}\" ")
            .append("Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" ")
            .append("Target=\"styles.xml\"/>")
        rels.append("<Relationship Id=\"rId${sheetCount + 2}\" ")
            .append("Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" ")
            .append("Target=\"sharedStrings.xml\"/>")
        return xmlProlog +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            rels +
            "</Relationships>"
    }

    private fun buildWorkbook(sheetCount: Int): String {
        val sheets = (1..sheetCount).joinToString("") { i ->
            "<sheet name=\"Sheet$i\" sheetId=\"$i\" r:id=\"rId$i\"/>"
        }
        return xmlProlog +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets>$sheets</sheets>" +
            "</workbook>"
    }

    private fun buildSheet(table: TableData, strings: Map<String, Int>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            .append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        table.rows.forEachIndexed { rIdx, row ->
            val rowIndex = rIdx + 1
            sb.append("<row r=\"$rowIndex\">")
            row.forEachIndexed { cIdx, value ->
                if (value.isNotEmpty()) {
                    val ref = excelRef(cIdx, rowIndex)
                    val si = strings[value] ?: 0
                    sb.append("<c r=\"$ref\" t=\"s\"><v>$si</v></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    /** 把 (列下标0-based, 行号1-based) 转为 "A1" 这样的 Excel 引用 */
    private fun excelRef(colIndex: Int, rowIndex: Int): String {
        var c = colIndex
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + c % 26))
            c = c / 26 - 1
            if (c < 0) break
        }
        return sb.toString() + rowIndex
    }

    // ============================== 工具函数 ==============================

    private const val xmlProlog = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

    private fun ZipOutputStream.putString(entry: String, content: String) {
        putNextEntry(ZipEntry(entry))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
