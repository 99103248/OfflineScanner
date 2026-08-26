package com.scanner.offline.domain.usecase

import com.scanner.offline.data.storage.ExportResult
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.TableData
import com.scanner.offline.domain.repository.DocumentRepository
import com.scanner.offline.engine.export.DocumentExporter
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
     * 按格式导出文档。Excel 模式需要传入 [tablesIfExcel]（占位：未来可由表格识别模型生成）
     *
     * 输出位置由 [StorageManager.createExportSink] 决定：
     *  - 用户在"我的"里选过自定义目录 → 写到 SAF 目录
     *  - 否则 → 写到应用私有外部目录 (/Android/data/<pkg>/files/exports)
     */
    suspend operator fun invoke(
        docId: Long,
        format: ExportFormat,
        ocrLanguage: Language = Language.AUTO,
        tablesIfExcel: List<TableData>? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        val doc: Document = repo.get(docId) ?: error("文档不存在")
        val sink = storage.createExportSink(
            name = doc.name,
            extension = format.extension,
            mimeType = format.mimeType
        )

        // 导出过程中可能要先做 OCR（suspend），所以"打开 OS / 写入 / 关闭"分成三步：
        //   1. 先在协程里把所有需要的文本拿到 (含 OCR 结果)
        //   2. 再打开 OutputStream 把数据写进去
        when (format) {
            ExportFormat.PDF -> {
                val paths = doc.pages.map { it.processedPath }
                sink.openOutputStream().use { os -> exporter.imagesToPdf(paths, os) }
            }
            ExportFormat.WORD -> {
                val texts = doc.pages.map { it.ocrText ?: ocr(it.processedPath, ocrLanguage, it.id).text }
                sink.openOutputStream().use { os -> exporter.textsToWord(texts, os) }
            }
            ExportFormat.EXCEL -> {
                val tables = tablesIfExcel ?: doc.pages.map { p ->
                    val text = p.ocrText ?: ocr(p.processedPath, ocrLanguage, p.id).text
                    TableData(text.split('\n').map { row -> row.split(Regex("[\\t,]+")) })
                }
                sink.openOutputStream().use { os -> exporter.tableToExcel(tables, os) }
            }
            ExportFormat.TXT -> {
                val texts = doc.pages.map { it.ocrText ?: ocr(it.processedPath, ocrLanguage, it.id).text }
                sink.openOutputStream().use { os -> exporter.textsToTxt(texts, os) }
            }
            ExportFormat.JPG, ExportFormat.PNG, ExportFormat.WEBP -> {
                val firstPage = doc.pages.firstOrNull() ?: error("文档无页面")
                sink.openOutputStream().use { os ->
                    exporter.convertImage(firstPage.processedPath, format, os)
                }
            }
        }
        ExportResult(
            displayName = sink.displayName,
            humanLocation = sink.humanLocation,
            shareUri = sink.shareUri,
            outputFile = sink.outputFile,
            mimeType = format.mimeType
        )
    }
}
