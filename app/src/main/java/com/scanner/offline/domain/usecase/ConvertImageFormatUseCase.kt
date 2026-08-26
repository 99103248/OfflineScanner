package com.scanner.offline.domain.usecase

import com.scanner.offline.data.storage.ExportResult
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.engine.export.DocumentExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
