package com.scanner.offline.engine.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class TableParserTest {
    @Test
    fun `按制表符和多空格拆列`() {
        val table = TableParser.fromPlainText("姓名\t分数\n张三  90")
        assertEquals(2, table.rowCount)
        assertEquals(2, table.columnCount)
        assertEquals("张三", table.rows[1][0])
    }
}
