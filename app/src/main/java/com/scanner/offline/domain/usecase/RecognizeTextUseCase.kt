package com.scanner.offline.domain.usecase

import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.OcrResult
import com.scanner.offline.domain.repository.DocumentRepository
import com.scanner.offline.engine.ocr.OcrEngineFactory
import com.scanner.offline.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class RecognizeTextUseCase @Inject constructor(
    private val factory: OcrEngineFactory,
    private val repo: DocumentRepository
) {
    suspend operator fun invoke(
        imagePath: String,
        language: Language,
        pageIdToUpdate: Long? = null
    ): OcrResult = withContext(Dispatchers.Default) {
        // OCR 不需要原始 2400px 全分辨率：限制到 2000px 可显著减少推理时间，
        // 同时保持足够的文字清晰度。
        val bmp = BitmapUtils.decode(File(imagePath), maxSize = OCR_MAX_SIZE)
            ?: error("无法读取图片")
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

    private companion object {
        const val OCR_MAX_SIZE = 2000
    }
}
