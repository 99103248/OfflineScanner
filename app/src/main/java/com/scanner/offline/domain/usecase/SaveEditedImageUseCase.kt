package com.scanner.offline.domain.usecase

import android.graphics.Bitmap
import com.scanner.offline.data.storage.ExportResult
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.engine.export.FileToImageConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SaveEditedImageUseCase @Inject constructor(
    private val storage: StorageManager,
    private val converter: FileToImageConverter
) {
    suspend operator fun invoke(
        bitmap: Bitmap,
        format: ExportFormat,
        baseName: String = "edited",
        quality: Int = 92
    ): ExportResult = withContext(Dispatchers.IO) {
        require(format == ExportFormat.JPG || format == ExportFormat.PNG || format == ExportFormat.WEBP) {
            "另存为仅支持 JPG / PNG / WebP"
        }
        val sink = storage.createExportSink(
            name = baseName,
            extension = format.extension,
            mimeType = format.mimeType
        )
        sink.openOutputStream().use { os -> converter.writeBitmap(bitmap, format, os, quality) }
        ExportResult(
            displayName = sink.displayName,
            humanLocation = sink.humanLocation,
            shareUri = sink.shareUri,
            outputFile = sink.outputFile,
            mimeType = format.mimeType
        )
    }
}
