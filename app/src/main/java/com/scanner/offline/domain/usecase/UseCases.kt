package com.scanner.offline.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.scanner.offline.data.storage.ExportResult
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.FilterMode
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.OcrResult
import com.scanner.offline.domain.model.Page
import com.scanner.offline.domain.model.TableData
import com.scanner.offline.domain.repository.DocumentRepository
import com.scanner.offline.engine.export.DocumentExporter
import com.scanner.offline.engine.image.ImageFilter
import com.scanner.offline.engine.image.PerspectiveCorrector
import com.scanner.offline.engine.ocr.OcrEngineFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 单一目的的业务用例。每个类只做一件事，方便单测和复用。
 */

class SaveScannedPageUseCase @Inject constructor(
    private val repo: DocumentRepository,
    private val storage: StorageManager,
    private val filter: ImageFilter,
    private val corrector: PerspectiveCorrector
) {
    /**
     * @param processedBitmapPath 已经裁剪过的临时文件路径
     * @param filterMode 应用的滤镜
     * @param docId 已存在文档 id；为 null 时新建文档
     */
    suspend operator fun invoke(
        processedBitmapPath: String,
        filterMode: FilterMode,
        docId: Long?,
        documentName: String
    ): Long = withContext(Dispatchers.IO) {
        val targetDocId = docId ?: repo.create(documentName)

        val src = BitmapFactory.decodeFile(processedBitmapPath)
            ?: error("无法读取扫描图")
        val clamped = corrector.clamp(src, maxSize = 2400)
        val filtered = filter.apply(clamped, filterMode)
        val thumb = Bitmap.createScaledBitmap(filtered, 320, (filtered.height * 320f / filtered.width).toInt(), true)

        val pageIndex = (repo.get(targetDocId)?.pages?.size ?: 0)

        val processedFile = storage.saveBitmap(filtered, storage.newProcessedFile(targetDocId, pageIndex))
        val originalFile = storage.saveBitmap(clamped, storage.newOriginalFile(targetDocId, pageIndex))
        val thumbFile = storage.saveBitmap(thumb, storage.newThumbnailFile(targetDocId, pageIndex), quality = 70)

        repo.addPage(
            Page(
                docId = targetDocId,
                index = pageIndex,
                originalPath = originalFile.absolutePath,
                processedPath = processedFile.absolutePath,
                thumbnailPath = thumbFile.absolutePath
            )
        )

        if (clamped !== src) src.recycle()
        clamped.recycle(); filtered.recycle(); thumb.recycle()
        targetDocId
    }
}

class RecognizeTextUseCase @Inject constructor(
    private val factory: OcrEngineFactory,
    private val repo: DocumentRepository
) {
    suspend operator fun invoke(
        imagePath: String,
        language: Language,
        pageIdToUpdate: Long? = null
    ): OcrResult = withContext(Dispatchers.Default) {
        val bmp = BitmapFactory.decodeFile(imagePath) ?: error("无法读取图片")
        try {
            val res = factory.recognize(bmp, language)
            pageIdToUpdate?.let { id ->
                repo.updatePageOcr(id, res.text, res.language.code)
            }
            res
        } finally {
            bmp.recycle()
        }
    }
}

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

class ConvertImageFormatUseCase @Inject constructor(
    private val storage: StorageManager,
    private val exporter: DocumentExporter
) {
    suspend operator fun invoke(
        sourcePath: String,
        format: ExportFormat,
        baseName: String = "converted"
    ): ExportResult = withContext(Dispatchers.IO) {
        val sink = storage.createExportSink(
            name = baseName,
            extension = format.extension,
            mimeType = format.mimeType
        )
        sink.openOutputStream().use { os -> exporter.convertImage(sourcePath, format, os) }
        ExportResult(
            displayName = sink.displayName,
            humanLocation = sink.humanLocation,
            shareUri = sink.shareUri,
            outputFile = sink.outputFile,
            mimeType = format.mimeType
        )
    }
}
