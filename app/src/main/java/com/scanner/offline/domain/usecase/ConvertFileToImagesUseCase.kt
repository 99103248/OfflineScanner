package com.scanner.offline.domain.usecase

import com.scanner.offline.data.storage.ExportResult
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.domain.model.InputDocumentType
import com.scanner.offline.domain.model.PdfImageLayout
import com.scanner.offline.engine.export.FileToImageConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ConvertFileToImagesUseCase @Inject constructor(
    private val storage: StorageManager,
    private val converter: FileToImageConverter
) {
    suspend operator fun invoke(
        sourcePath: String,
        inputType: InputDocumentType,
        format: ExportFormat,
        baseName: String = "converted",
        pdfLayout: PdfImageLayout = PdfImageLayout.SEPARATE_PAGES
    ): List<ExportResult> = withContext(Dispatchers.IO) {
        require(format == ExportFormat.JPG || format == ExportFormat.PNG || format == ExportFormat.WEBP) {
            "文档转图片仅支持 JPG / PNG / WebP"
        }
        val file = File(sourcePath)
        val layout = if (inputType == InputDocumentType.PDF) pdfLayout else PdfImageLayout.SEPARATE_PAGES
        val bitmaps = converter.convertToBitmaps(file, inputType, layout)
        try {
            bitmaps.mapIndexed { index, bmp ->
                val name = when {
                    bitmaps.size == 1 && layout == PdfImageLayout.LONG_IMAGE -> "${baseName}_long"
                    bitmaps.size == 1 -> baseName
                    else -> "${baseName}_p${index + 1}"
                }
                val sink = storage.createExportSink(
                    name = name,
                    extension = format.extension,
                    mimeType = format.mimeType
                )
                sink.openOutputStream().use { os ->
                    converter.writeBitmap(bmp, format, os)
                }
                ExportResult(
                    displayName = sink.displayName,
                    humanLocation = sink.humanLocation,
                    shareUri = sink.shareUri,
                    outputFile = sink.outputFile,
                    mimeType = format.mimeType
                )
            }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }
}
