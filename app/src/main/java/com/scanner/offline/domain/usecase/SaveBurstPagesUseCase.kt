package com.scanner.offline.domain.usecase

import android.graphics.Bitmap
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

class SaveBurstPagesUseCase @Inject constructor(
    private val savePage: SaveScannedPageUseCase,
    private val edgeDetector: EdgeDetector,
    private val corrector: PerspectiveCorrector,
    private val storage: StorageManager,
    @ApplicationContext private val context: android.content.Context
) {
    suspend operator fun invoke(imageUris: List<String>, documentName: String): Long =
        withContext(Dispatchers.IO) {
            require(imageUris.isNotEmpty()) { "没有拍摄的页面" }
            var docId: Long? = null
            imageUris.forEach { uriString ->
                val bmp = decode(uriString) ?: return@forEach
                var corrected: Bitmap? = null
                var clamped: Bitmap? = null
                try {
                    val corners = edgeDetector.detect(bmp)
                    corrected = corrector.correct(bmp, corners)
                    clamped = corrector.clamp(corrected, 2400)
                    val tmp = storage.newCacheImage("burst")
                    storage.saveBitmap(clamped, tmp)
                    docId = savePage(
                        processedBitmapPath = tmp.absolutePath,
                        filterMode = FilterMode.ORIGINAL,
                        docId = docId,
                        documentName = documentName
                    )
                } finally {
                    bmp.recycle()
                    if (corrected !== null && corrected !== bmp) corrected.recycle()
                    if (clamped !== null && clamped !== corrected) clamped.recycle()
                }
            }
            docId ?: error("连拍保存失败")
        }

    private fun decode(imageUri: String): Bitmap? {
        val uri = android.net.Uri.parse(imageUri)
        return if (uri.scheme == "file" || uri.scheme == null) {
            BitmapUtils.decode(File(uri.path ?: imageUri))
        } else {
            BitmapUtils.decode(context.contentResolver, uri)
        }
    }
}
