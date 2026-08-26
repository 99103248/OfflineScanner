package com.scanner.offline.domain.usecase

import android.graphics.BitmapFactory
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.OcrResult
import com.scanner.offline.domain.repository.DocumentRepository
import com.scanner.offline.engine.ocr.OcrEngineFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
