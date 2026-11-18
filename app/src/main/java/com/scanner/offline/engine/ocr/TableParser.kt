package com.scanner.offline.engine.ocr

import com.scanner.offline.domain.model.TableData
import com.scanner.offline.domain.model.TextBlock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object TableParser {

    /**
     * 优先基于 OCR [TextBlock] 的二维坐标还原行列结构；
     * 若缺乏有效坐标框（或文本已被手动大幅修改），自动回退到纯文本切分。
     */
    fun fromOcr(text: String, blocks: List<TextBlock> = emptyList()): TableData {
        val positioned = blocks.filter { it.text.isNotBlank() && it.boundingBox != null }
        if (positioned.size >= 2) {
            val byBoxes = fromBlocks(positioned)
            if (byBoxes.rowCount >= 1 && byBoxes.columnCount >= 2) {
                return byBoxes
            }
        }
        return fromPlainText(text)
    }

    /**
     * 利用文本框的纵坐标重叠/中心距离聚类成行，再按横坐标聚类对齐列。
     */
    fun fromBlocks(blocks: List<TextBlock>): TableData {
        val valid = blocks.filter { it.text.isNotBlank() && it.boundingBox != null }
        if (valid.isEmpty()) return TableData(listOf(listOf("")))

        val sortedByY = valid.sortedBy {
            val b = it.boundingBox!!
            (b.top + b.bottom) / 2f
        }

        val rowClusters = mutableListOf<MutableList<TextBlock>>()
        for (block in sortedByY) {
            val box = block.boundingBox!!
            val cy = (box.top + box.bottom) / 2f
            val h = box.height.coerceAtLeast(1)
            val matchedRow = rowClusters.lastOrNull { row ->
                val rowCy = row.map { (it.boundingBox!!.top + it.boundingBox.bottom) / 2f }.average().toFloat()
                val rowH = row.map { it.boundingBox!!.height.coerceAtLeast(1) }.average().toFloat()
                val overlapTop = max(box.top.toFloat(), row.minOf { it.boundingBox!!.top }.toFloat())
                val overlapBottom = min(box.bottom.toFloat(), row.maxOf { it.boundingBox!!.bottom }.toFloat())
                val overlap = (overlapBottom - overlapTop).coerceAtLeast(0f)
                abs(cy - rowCy) <= max(h.toFloat(), rowH) * 0.55f || overlap >= min(h.toFloat(), rowH) * 0.45f
            }
            if (matchedRow != null) {
                matchedRow.add(block)
            } else {
                rowClusters.add(mutableListOf(block))
            }
        }

        // 每行内部按从左到右排序，如果单个框内部还有制表符/多空格也展开
        val rows = rowClusters.map { rowBlocks ->
            rowBlocks.sortedBy { it.boundingBox!!.left }
                .flatMap { b ->
                    b.text.trim()
                        .split(Regex("[\\t|]+|\\s{2,}"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                }
                .ifEmpty { listOf("") }
        }
        return TableData(rows.ifEmpty { listOf(listOf("")) })
    }

    fun fromPlainText(text: String): TableData {
        val rows = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                line.split(Regex("[\\t|]+|\\s{2,}"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { listOf(line) }
            }
        return TableData(rows.ifEmpty { listOf(listOf("")) })
    }
}
