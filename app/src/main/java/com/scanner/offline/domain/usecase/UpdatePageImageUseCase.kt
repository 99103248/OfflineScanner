package com.scanner.offline.domain.usecase

import android.graphics.Bitmap
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class UpdatePageImageUseCase @Inject constructor(
    private val repo: DocumentRepository,
    private val storage: StorageManager
) {
    suspend operator fun invoke(pageId: Long, bitmap: Bitmap): Unit = withContext(Dispatchers.IO) {
        val page = repo.getPage(pageId) ?: error("页面不存在")
        storage.saveBitmap(bitmap, File(page.processedPath))
        val thumbH = (bitmap.height * 320f / bitmap.width).toInt().coerceAtLeast(1)
        val thumb = Bitmap.createScaledBitmap(bitmap, 320, thumbH, true)
        storage.saveBitmap(thumb, File(page.thumbnailPath), quality = 70)
        if (thumb !== bitmap) thumb.recycle()
        repo.updatePageImage(pageId, page.processedPath, page.thumbnailPath)
    }
}
