package com.scanner.offline.engine.ocr

import com.scanner.offline.domain.model.BoundingBox
import com.scanner.offline.domain.model.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableParserTest {
    @Test
    fun `按制表符和多空格拆列`() {
        val table = TableParser.fromPlainText("姓名\t分数\n张三  90")
        assertEquals(2, table.rowCount)
        assertEquals(2, table.columnCount)
        assertEquals("张三", table.rows[1][0])
    }

    @Test
    fun `按二维文本框坐标聚类还原表格行列`() {
        val blocks = listOf(
            TextBlock("姓名", 0.99f, BoundingBox(20, 10, 100, 40, 0.02f, 0.01f, 0.1f, 0.04f)),
            TextBlock("部门", 0.98f, BoundingBox(220, 12, 300, 42, 0.22f, 0.012f, 0.3f, 0.042f)),
            TextBlock("成绩", 0.99f, BoundingBox(420, 11, 500, 41, 0.42f, 0.011f, 0.5f, 0.041f)),
            // 第二行故意打乱输入顺序，验证按 X 轴自动排序
            TextBlock("98", 0.95f, BoundingBox(425, 80, 480, 110, 0.425f, 0.08f, 0.48f, 0.11f)),
            TextBlock("李四", 0.97f, BoundingBox(22, 78, 98, 108, 0.022f, 0.078f, 0.098f, 0.108f)),
            TextBlock("研发部", 0.96f, BoundingBox(218, 79, 310, 109, 0.218f, 0.079f, 0.31f, 0.109f))
        )
        val table = TableParser.fromOcr("姓名\n部门\n成绩\n98\n李四\n研发部", blocks)
        assertEquals(2, table.rowCount)
        assertEquals(3, table.columnCount)
        assertEquals(listOf("姓名", "部门", "成绩"), table.rows[0])
        assertEquals(listOf("李四", "研发部", "98"), table.rows[1])
    }

    @Test
    fun `OcrBlockCodec 序列化与反序列化保持文本与归一化坐标一致`() {
        val original = listOf(
            TextBlock(
                text = "第一行\n带换行与\t制表符\\反斜杠",
                confidence = 0.92f,
                boundingBox = BoundingBox(10, 20, 300, 80, 0.05f, 0.10f, 0.75f, 0.40f)
            ),
            TextBlock(
                text = "Second Block",
                confidence = 0.88f,
                boundingBox = null
            )
        )
        val encoded = OcrBlockCodec.encode(original)
        assertTrue(encoded.isNotBlank())
        val decoded = OcrBlockCodec.decode(encoded)
        assertEquals(2, decoded.size)
        assertEquals("第一行\n带换行与\t制表符\\反斜杠", decoded[0].text)
        assertEquals(0.92f, decoded[0].confidence, 0.0001f)
        assertEquals(true, decoded[0].boundingBox?.hasNormalized)
        assertEquals(0.05f, decoded[0].boundingBox!!.normLeft, 0.0001f)
        assertEquals(0.75f, decoded[0].boundingBox!!.normRight, 0.0001f)
        assertEquals(null, decoded[1].boundingBox)
    }
}
