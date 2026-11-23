package com.scanner.offline.domain.usecase

import com.scanner.offline.domain.model.FilterMode
import com.scanner.offline.domain.repository.DocumentRepository
import com.scanner.offline.engine.image.ImageFilter
import com.scanner.offline.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 对已保存的文档页面无损重新套用滤镜：
 * 优先从 [com.scanner.offline.domain.model.Page.originalPath] 解码原图，
 * 避免多次叠加滤镜导致画质劣化；若原图缺失则回退至 processedPath。
 */
class ApplyPageFilterUseCase @Inject constructor(
    private val repo: DocumentRepository,
    private val imageFilter: ImageFilter,
    private val updatePageImage: UpdatePageImageUseCase
) {
    suspend operator fun invoke(pageId: Long, mode: FilterMode): Unit =
        withContext(Dispatchers.IO) {
            val page = repo.getPage(pageId) ?: error("页面不存在")
            val sourceFile = File(page.originalPath).takeIf { it.exists() && it.canRead() }
                ?: File(page.processedPath).takeIf { it.exists() && it.canRead() }
                ?: error("无法读取页面图片")
            val src = BitmapUtils.decode(sourceFile, maxSize = 2400)
                ?: error("无法解码页面图片")
            val filtered = try {
                imageFilter.apply(src, mode)
            } finally {
                src.recycle()
            }
            try {
                updatePageImage(pageId, filtered)
            } finally {
                filtered.recycle()
            }
        }
}

