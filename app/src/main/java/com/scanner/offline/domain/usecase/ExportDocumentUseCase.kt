package com.scanner.offline.domain.usecase

import com.scanner.offline.data.storage.ExportResult
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.PdfPaperSize
import com.scanner.offline.domain.model.TableData
import com.scanner.offline.domain.repository.DocumentRepository
import com.scanner.offline.engine.export.DocumentExporter
import com.scanner.offline.engine.ocr.TableParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ExportDocumentUseCase @Inject constructor(
    private val repo: DocumentRepository,
    private val storage: StorageManager,
    private val exporter: DocumentExporter,
    private val ocr: RecognizeTextUseCase
) {
    /**
     * 按格式导出文档。Excel 模式需要传入 [tablesIfExcel]（或自动通过坐标聚类解析）
     *
     * PDF 模式下 [searchablePdf] = true 时导出"可搜索 PDF"：
     * 每页叠加隐形 OCR 文字层（缺 OCR 的页会先自动识别，有坐标框时按真实位置对齐）。
     */
    suspend operator fun invoke(
        docId: Long,
        format: ExportFormat,
        ocrLanguage: Language = Language.AUTO,
        tablesIfExcel: List<TableData>? = null,
        searchablePdf: Boolean = false,
        pdfPaper: PdfPaperSize = PdfPaperSize.A4,
        pdfMaxImageDimension: Int = 3000,
        imageQuality: Int = 92
    ): List<ExportResult> = withContext(Dispatchers.IO) {
        val doc: Document = repo.get(docId) ?: error("文档不存在")
        if (doc.pages.isEmpty()) error("文档没有页面")

        when (format) {
            ExportFormat.PDF -> {
                val sink = storage.createExportSink(
                    name = doc.name,
                    extension = format.extension,
                    mimeType = format.mimeType
                )
                if (searchablePdf) {
                    val ocrPairs = doc.pages.map { p ->
                        if (!p.ocrText.isNullOrBlank()) {
                            p.ocrText to p.ocrBlocks
                        } else {
                            val res = ocr(p.processedPath, ocrLanguage, p.id)
                            res.text to res.blocks
                        }
                    }
                    sink.openOutputStream().use { os ->
                        exporter.imagesToSearchablePdf(
                            imagePaths = doc.pages.map { it.processedPath },
                            pageTexts = ocrPairs.map { it.first },
                            output = os,
                            paperSize = pdfPaper,
                            maxImageDimension = pdfMaxImageDimension,
                            pageBlocks = ocrPairs.map { it.second }
                        )
                    }
                } else {
                    val paths = doc.pages.map { it.processedPath }
                    sink.openOutputStream().use { os ->
                        exporter.imagesToPdf(
                            paths, os,
                            paperSize = pdfPaper, maxImageDimension = pdfMaxImageDimension
                        )
                    }
                }
                listOf(sink.toResult(format))
            }
            ExportFormat.WORD -> {
                val texts = doc.pages.map { it.ocrText ?: ocr(it.processedPath, ocrLanguage, it.id).text }
                val sink = storage.createExportSink(doc.name, format.extension, format.mimeType)
                sink.openOutputStream().use { os -> exporter.textsToWord(texts, os) }
                listOf(sink.toResult(format))
            }
            ExportFormat.EXCEL -> {
                val tables = tablesIfExcel ?: doc.pages.map { p ->
                    if (!p.ocrText.isNullOrBlank()) {
                        TableParser.fromOcr(p.ocrText, p.ocrBlocks)
                    } else {
                        val res = ocr(p.processedPath, ocrLanguage, p.id)
                        TableParser.fromOcr(res.text, res.blocks)
                    }
                }
                val sink = storage.createExportSink(doc.name, format.extension, format.mimeType)
                sink.openOutputStream().use { os -> exporter.tableToExcel(tables, os) }
                listOf(sink.toResult(format))
            }
            ExportFormat.TXT -> {
                val texts = doc.pages.map { it.ocrText ?: ocr(it.processedPath, ocrLanguage, it.id).text }
                val sink = storage.createExportSink(doc.name, format.extension, format.mimeType)
                sink.openOutputStream().use { os -> exporter.textsToTxt(texts, os) }
                listOf(sink.toResult(format))
            }
            ExportFormat.JPG, ExportFormat.PNG, ExportFormat.WEBP -> {
                // 多页文档逐页导出，避免"只导出第一页"的误解
                doc.pages.mapIndexed { index, page ->
                    val pageName = if (doc.pages.size == 1) doc.name else "${doc.name}_${index + 1}"
                    val sink = storage.createExportSink(pageName, format.extension, format.mimeType)
                    sink.openOutputStream().use { os ->
                        exporter.convertImage(page.processedPath, format, os, quality = imageQuality.coerceIn(40, 100))
                    }
                    sink.toResult(format)
                }
            }
        }
    }

    private fun com.scanner.offline.data.storage.ExportSink.toResult(format: ExportFormat) =
        ExportResult(
            displayName = displayName,
            humanLocation = humanLocation,
            shareUri = shareUri,
            outputFile = outputFile,
            mimeType = format.mimeType
        )
}
