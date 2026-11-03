package com.scanner.offline.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.FilterMode
import com.scanner.offline.engine.image.EdgeDetector
import com.scanner.offline.engine.image.PerspectiveCorrector
import com.scanner.offline.util.BitmapUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 把一批相册图片追加到已有文档：
 * 解码 → 自动边缘检测 → 透视矫正 → 保存为页面。
 */
class AppendImagesUseCase @Inject constructor(
    private val savePage: SaveScannedPageUseCase,
    private val edgeDetector: EdgeDetector,
    private val corrector: PerspectiveCorrector,
    private val storage: StorageManager,
    @ApplicationContext private val context: Context
) {
    /**
     * @return 成功追加的页数
     */
    suspend operator fun invoke(
        docId: Long,
        imageUris: List<String>,
        filterMode: FilterMode = FilterMode.ENHANCE,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        var done = 0
        imageUris.forEachIndexed { index, uriString ->
            val bmp = decode(uriString) ?: run {
                onProgress(index + 1, imageUris.size)
                return@forEachIndexed
            }
            var corrected: Bitmap? = null
            var clamped: Bitmap? = null
            try {
                val corners = edgeDetector.detect(bmp)
                corrected = corrector.correct(bmp, corners)
                clamped = corrector.clamp(corrected, 2400)
                val tmp = storage.newCacheImage("append")
                storage.saveBitmap(clamped, tmp)
                savePage(
                    processedBitmapPath = tmp.absolutePath,
                    filterMode = filterMode,
                    docId = docId,
                    documentName = ""
                )
                done++
            } catch (_: Exception) {
                // 单张失败不影响后续图片
            } finally {
                bmp.recycle()
                if (corrected != null && corrected !== bmp) corrected.recycle()
                if (clamped != null && clamped !== corrected) clamped.recycle()
                onProgress(index + 1, imageUris.size)
            }
        }
        done
    }

    private fun decode(raw: String): Bitmap? {
        File(raw).takeIf { it.exists() }?.let { return BitmapUtils.decode(it) }
        val uri = Uri.parse(raw)
        return if (uri.scheme == "file" || uri.scheme == null) {
            BitmapUtils.decode(File(uri.path ?: raw))
        } else {
            BitmapUtils.decode(context.contentResolver, uri)
        }
    }
}
