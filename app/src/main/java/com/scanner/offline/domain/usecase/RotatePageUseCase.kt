package com.scanner.offline.domain.usecase

import android.graphics.Bitmap
import com.scanner.offline.domain.repository.DocumentRepository
import com.scanner.offline.engine.image.ImageTransform
import com.scanner.offline.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 将文档页面顺时针 / 逆时针旋转 90°，并同步更新缩略图。 */
class RotatePageUseCase @Inject constructor(
    private val repo: DocumentRepository,
    private val transform: ImageTransform,
    private val updatePageImage: UpdatePageImageUseCase
) {
    suspend operator fun invoke(pageId: Long, clockwise: Boolean = true): Unit =
        withContext(Dispatchers.IO) {
            val page = repo.getPage(pageId) ?: error("页面不存在")
            val src: Bitmap = BitmapUtils.decode(File(page.processedPath), maxSize = 4096)
                ?: error("无法读取页面图片")
            val rotated = try {
                if (clockwise) transform.rotate90Clockwise(src)
                else transform.rotate90CounterClockwise(src)
            } finally {
                src.recycle()
            }
            try {
                updatePageImage(pageId, rotated)
            } finally {
                rotated.recycle()
            }
        }
}
