package com.scanner.offline.data.backup

import android.content.Context
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.Page
import com.scanner.offline.domain.repository.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地备份 / 恢复。
 *
 * 备份是一个 zip：
 *  - `manifest.json`：文档与页面元数据（名称、时间、收藏、OCR 文本）
 *  - `images/...`：原图 / 处理图 / 缩略图
 *
 * 恢复时重建文档与页面，图片复制到应用私有目录，不依赖任何云服务。
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: DocumentRepository,
    private val storage: StorageManager
) {

    suspend fun backupTo(
        output: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): BackupSummary = withContext(Dispatchers.IO) {
        val docs = repo.getAllForBackup()
        val totalPages = docs.sumOf { it.pages.size }
        var done = 0
        val manifest = JSONObject().apply {
            put("app", "OffScan")
            put("version", 1)
            put("createdAt", System.currentTimeMillis())
            put("documents", JSONArray())
        }
        val documentsJson = manifest.getJSONArray("documents")

        ZipOutputStream(output.buffered()).use { zos ->
            docs.forEachIndexed { docIndex, doc ->
                val pagesJson = JSONArray()
                doc.pages.forEach { page ->
                    val prefix = "images/d${docIndex}_p${page.index}"
                    val originalEntry = "$prefix" + "_orig.jpg"
                    val processedEntry = "$prefix" + "_proc.jpg"
                    val thumbEntry = "$prefix" + "_thumb.jpg"
                    val originalOk = addFile(zos, originalEntry, page.originalPath)
                    val processedOk = addFile(zos, processedEntry, page.processedPath)
                    val thumbOk = addFile(zos, thumbEntry, page.thumbnailPath)
                    if (!processedOk && !originalOk) return@forEach
                    pagesJson.put(
                        JSONObject().apply {
                            put("index", page.index)
                            put("original", if (originalOk) originalEntry else processedEntry)
                            put("processed", if (processedOk) processedEntry else originalEntry)
                            put("thumbnail", if (thumbOk) thumbEntry else if (processedOk) processedEntry else originalEntry)
                            put("ocrText", page.ocrText ?: "")
                            put("ocrLanguage", page.ocrLanguage?.code ?: "")
                        }
                    )
                    done++
                    onProgress(done, totalPages.coerceAtLeast(1))
                }
                documentsJson.put(
                    JSONObject().apply {
                        put("name", doc.name)
                        put("createdAt", doc.createdAt)
                        put("updatedAt", doc.updatedAt)
                        put("isFavorite", doc.isFavorite)
                        put("pages", pagesJson)
                    }
                )
            }
            val manifestBytes = manifest.toString().toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestBytes)
            zos.closeEntry()
        }
        BackupSummary(documentCount = docs.size, pageCount = done)
    }

    suspend fun restoreFrom(
        input: InputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): RestoreSummary = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply { mkdirs() }
        var manifestText: String? = null
        try {
            ZipInputStream(input.buffered()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name
                    if (entry.isDirectory) {
                        zis.closeEntry()
                        continue
                    }
                    if (name == "manifest.json") {
                        manifestText = zis.readBytes().toString(Charsets.UTF_8)
                    } else {
                        val target = File(tempDir, name)
                        // 防 zip slip
                        if (!target.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)) {
                            zis.closeEntry()
                            continue
                        }
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out -> zis.copyTo(out) }
                    }
                    zis.closeEntry()
                }
            }

            val manifest = manifestText?.let { JSONObject(it) }
                ?: error("备份文件缺少 manifest.json")
            val documents = manifest.optJSONArray("documents") ?: JSONArray()
            val totalPages = (0 until documents.length()).sumOf { i ->
                documents.getJSONObject(i).optJSONArray("pages")?.length() ?: 0
            }
            var done = 0
            var restoredDocs = 0
            var restoredPages = 0

            for (i in 0 until documents.length()) {
                val docJson = documents.getJSONObject(i)
                val name = docJson.optString("name").ifBlank { "恢复的文档" }
                val docId = repo.createImported(
                    name = name,
                    createdAt = docJson.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = docJson.optLong("updatedAt", System.currentTimeMillis()),
                    isFavorite = docJson.optBoolean("isFavorite", false)
                )
                restoredDocs++
                val pages = docJson.optJSONArray("pages") ?: JSONArray()
                for (j in 0 until pages.length()) {
                    val pageJson = pages.getJSONObject(j)
                    val original = pageJson.optString("original")
                    val processed = pageJson.optString("processed")
                    val thumbnail = pageJson.optString("thumbnail")
                    val srcProcessed = File(tempDir, processed).takeIf { it.exists() }
                    val srcOriginal = File(tempDir, original).takeIf { it.exists() } ?: srcProcessed
                    if (srcProcessed == null || srcOriginal == null) {
                        done++
                        onProgress(done, totalPages.coerceAtLeast(1))
                        continue
                    }
                    val index = repo.nextPageIndex(docId)
                    val destOriginal = storage.newOriginalFile(docId, index)
                    val destProcessed = storage.newProcessedFile(docId, index)
                    val destThumb = storage.newThumbnailFile(docId, index)
                    srcOriginal.copyTo(destOriginal, overwrite = true)
                    srcProcessed.copyTo(destProcessed, overwrite = true)
                    val srcThumb = File(tempDir, thumbnail).takeIf { it.exists() }
                    if (srcThumb != null) {
                        srcThumb.copyTo(destThumb, overwrite = true)
                    } else {
                        srcProcessed.copyTo(destThumb, overwrite = true)
                    }
                    val lang = pageJson.optString("ocrLanguage")
                        .takeIf { it.isNotBlank() }
                        ?.let { code -> Language.entries.firstOrNull { it.code == code } }
                    repo.addPage(
                        Page(
                            docId = docId,
                            index = index,
                            originalPath = destOriginal.absolutePath,
                            processedPath = destProcessed.absolutePath,
                            thumbnailPath = destThumb.absolutePath,
                            ocrText = pageJson.optString("ocrText").takeIf { it.isNotBlank() },
                            ocrLanguage = lang
                        )
                    )
                    restoredPages++
                    done++
                    onProgress(done, totalPages.coerceAtLeast(1))
                }
            }
            RestoreSummary(documentCount = restoredDocs, pageCount = restoredPages)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun addFile(zos: ZipOutputStream, entryName: String, path: String): Boolean {
        val file = File(path)
        if (!file.exists() || !file.canRead()) return false
        zos.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
        return true
    }
}

data class BackupSummary(val documentCount: Int, val pageCount: Int)
data class RestoreSummary(val documentCount: Int, val pageCount: Int)
