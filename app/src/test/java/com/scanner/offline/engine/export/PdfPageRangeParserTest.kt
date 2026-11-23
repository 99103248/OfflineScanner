package com.scanner.offline.engine.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageRangeParserTest {

    @Test
    fun `单页与区间混合解析并去重保序`() {
        assertEquals(listOf(0, 1, 2, 4), PdfPageRangeParser.parse("1-3, 5, 2", 10))
    }

    @Test
    fun `支持反向区间与中文标点`() {
        assertEquals(listOf(2, 3, 4, 0), PdfPageRangeParser.parse("5～3，1；3", 8))
    }

    @Test
    fun `支持半开区间至首尾页`() {
        assertEquals(listOf(0, 1, 2, 7, 8, 9), PdfPageRangeParser.parse("-3, 8-", 10))
    }

    @Test
    fun `越界页码与非法输入安全过滤`() {
        assertEquals(listOf(0, 1, 2), PdfPageRangeParser.parse("0, -1, 1-99, abc, 1-2-3", 3))
        assertTrue(PdfPageRangeParser.parse("", 5).isEmpty())
        assertTrue(PdfPageRangeParser.parse("1-3", 0).isEmpty())
        assertTrue(PdfPageRangeParser.parse("10-20", 5).isEmpty())
    }
}

