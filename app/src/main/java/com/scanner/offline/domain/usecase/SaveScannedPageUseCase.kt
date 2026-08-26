package com.scanner.offline.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.FilterMode
import com.scanner.offline.domain.model.Page
import com.scanner.offline.domain.repository.DocumentRepository
import com.scanner.offline.engine.image.ImageFilter
import com.scanner.offline.engine.image.PerspectiveCorrector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
        var clamped: Bitmap? = null
        var filtered: Bitmap? = null
        var thumb: Bitmap? = null
        try {
            clamped = corrector.clamp(src, maxSize = 2400)
            filtered = filter.apply(clamped, filterMode)
            thumb = Bitmap.createScaledBitmap(filtered, 320, (filtered.height * 320f / filtered.width).toInt(), true)

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
            targetDocId
        } finally {
            if (clamped !== src) src.recycle()
            clamped?.recycle()
            filtered?.recycle()
            thumb?.recycle()
        }
    }
}
