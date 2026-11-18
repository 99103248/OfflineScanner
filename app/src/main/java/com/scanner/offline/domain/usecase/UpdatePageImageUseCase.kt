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
        val newProcessed = storage.saveBitmap(
            bitmap,
            storage.newProcessedFile(page.docId, page.index)
        )
        val thumbH = (bitmap.height * 320f / bitmap.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val thumb = Bitmap.createScaledBitmap(bitmap, 320, thumbH, true)
        val newThumb = storage.saveBitmap(
            thumb,
            storage.newThumbnailFile(page.docId, page.index),
            quality = 70
        )
        if (thumb !== bitmap) thumb.recycle()

        repo.updatePageImage(pageId, newProcessed.absolutePath, newThumb.absolutePath)

        // 清理旧版本处理图与缩略图（不误删原图）
        if (page.processedPath != newProcessed.absolutePath && page.processedPath != page.originalPath) {
            runCatching { File(page.processedPath).delete() }
        }
        if (page.thumbnailPath != newThumb.absolutePath && page.thumbnailPath != page.originalPath) {
            runCatching { File(page.thumbnailPath).delete() }
        }
    }
}
