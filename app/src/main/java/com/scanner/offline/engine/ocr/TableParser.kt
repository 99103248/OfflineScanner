package com.scanner.offline.engine.ocr

import com.scanner.offline.domain.model.TableData

object TableParser {
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
