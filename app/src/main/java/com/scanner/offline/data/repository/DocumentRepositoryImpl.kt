package com.scanner.offline.data.repository

import com.scanner.offline.data.db.DocumentDao
import com.scanner.offline.data.db.DocumentEntity
import com.scanner.offline.data.db.DocumentSummary
import com.scanner.offline.data.db.PageEntity
import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.Page
import com.scanner.offline.domain.model.TextBlock
import com.scanner.offline.domain.repository.DocumentRepository
import com.scanner.offline.engine.ocr.OcrBlockCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao
) : DocumentRepository {

    override fun observeAll(query: String): Flow<List<Document>> =
        dao.observeActiveDocuments(query).map { list ->
            list.map { it.toDomain() }
        }

    override fun observeTrashed(): Flow<List<Document>> =
        dao.observeTrashedSummaries().map { list ->
            list.map { it.toDomain() }
        }

    override fun observeDocument(id: Long): Flow<Document?> =
        combine(
            dao.observeDocument(id),
            dao.observePagesByDoc(id)
        ) { doc, pages ->
            doc?.toDomain(pages.map { it.toDomain() })
        }

    override suspend fun get(id: Long): Document? {
        val pair = dao.getDocumentWithPages(id) ?: return null
        return pair.first.toDomain(pair.second.map { it.toDomain() })
    }

    override suspend fun getPage(pageId: Long): Page? =
        dao.getPage(pageId)?.toDomain()

    override suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertDocument(
            DocumentEntity(name = name, createdAt = now, updatedAt = now)
        )
    }

    override suspend fun rename(id: Long, name: String) {
        dao.renameDocument(id, name, System.currentTimeMillis())
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        dao.setFavorite(id, favorite, System.currentTimeMillis())
    }

    override suspend fun moveToTrash(id: Long) {
        dao.moveToTrash(id, System.currentTimeMillis())
    }

    override suspend fun restoreFromTrash(id: Long) {
        dao.restoreFromTrash(id, System.currentTimeMillis())
    }

    override suspend fun deleteForever(id: Long) {
        val pages = dao.getPagesByDoc(id)
        dao.deleteDocumentById(id)
        var docDir: File? = null
        pages.forEach { page ->
            listOf(page.originalPath, page.processedPath, page.thumbnailPath)
                .filter { it.isNotBlank() }
                .forEach { path ->
                    runCatching {
                        val f = File(path)
                        if (docDir == null && f.parentFile?.name == id.toString()) {
                            docDir = f.parentFile
                        }
                        f.delete()
                    }
                }
        }
        docDir?.let { dir -> runCatching { if (dir.listFiles().isNullOrEmpty()) dir.delete() } }
    }

    override suspend fun emptyTrash(): Int {
        val ids = dao.listTrashedIds()
        ids.forEach { deleteForever(it) }
        return ids.size
    }

    override suspend fun purgeTrashOlderThan(cutoff: Long): Int {
        val expiredIds = dao.listExpiredTrashedIds(cutoff)
        expiredIds.forEach { deleteForever(it) }
        return expiredIds.size
    }

    override suspend fun addPage(page: Page): Long {
        val pageId = dao.insertPage(
            PageEntity(
                docId = page.docId,
                index = page.index,
                originalPath = page.originalPath,
                processedPath = page.processedPath,
                thumbnailPath = page.thumbnailPath,
                ocrText = page.ocrText,
                ocrLanguage = page.ocrLanguage?.code,
                ocrBlocksJson = OcrBlockCodec.encode(page.ocrBlocks).takeIf { it.isNotBlank() }
            )
        )
        dao.touchDocument(page.docId, System.currentTimeMillis())
        return pageId
    }

    override suspend fun nextPageIndex(docId: Long): Int = dao.maxPageIndex(docId) + 1

    override suspend fun reorderPages(docId: Long, orderedPageIds: List<Long>) {
        // 只接受当前文档的页面，避免误把别的文档页面 index 改乱
        val validIds = dao.getPagesByDoc(docId).map { it.id }.toSet()
        val safe = orderedPageIds.filter { it in validIds }
        if (safe.size == validIds.size) {
            dao.reorderPages(safe)
            dao.touchDocument(docId, System.currentTimeMillis())
        }
    }

    override suspend fun reindexPages(docId: Long) {
        dao.reindexPages(docId)
    }

    override suspend fun createImported(
        name: String,
        createdAt: Long,
        updatedAt: Long,
        isFavorite: Boolean
    ): Long = dao.insertDocument(
        DocumentEntity(
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isFavorite = isFavorite
        )
    )

    override suspend fun getAllForBackup(): List<Document> =
        dao.getAllDocuments()
            .filter { it.deletedAt == null }
            .map { doc ->
                doc.toDomain(dao.getPagesByDoc(doc.id).map { it.toDomain() })
            }

    override suspend fun updatePageOcr(
        pageId: Long,
        text: String,
        langCode: String,
        blocks: List<TextBlock>
    ) {
        val page = dao.getPage(pageId)
        val encoded = OcrBlockCodec.encode(blocks).takeIf { it.isNotBlank() }
        dao.updatePageOcr(pageId, text, langCode, encoded)
        page?.let { dao.touchDocument(it.docId, System.currentTimeMillis()) }
    }

    override suspend fun updatePageImage(pageId: Long, processedPath: String, thumbnailPath: String) {
        val page = dao.getPage(pageId)
        dao.updatePageImage(pageId, processedPath, thumbnailPath)
        page?.let { dao.touchDocument(it.docId, System.currentTimeMillis()) }
    }

    override suspend fun deletePage(pageId: Long) {
        val page = dao.getPage(pageId)
        dao.deletePage(pageId)
        page?.let { p ->
            // 页面原图/处理图/缩略图可能被其他文档复制的场景不存在（私有目录），直接清理
            listOf(p.originalPath, p.processedPath, p.thumbnailPath)
                .filter { it.isNotBlank() }
                .forEach { path -> runCatching { File(path).delete() } }
            dao.reindexPages(p.docId)
            dao.touchDocument(p.docId, System.currentTimeMillis())
        }
    }
}

private fun DocumentSummary.toDomain(): Document =
    Document(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isFavorite = isFavorite,
        deletedAt = deletedAt,
        pageCount = pageCount,
        coverPath = coverPath
    )

private fun DocumentEntity.toDomain(pages: List<Page>): Document =
    Document(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isFavorite = isFavorite,
        deletedAt = deletedAt,
        pages = pages
    )

private fun PageEntity.toDomain(): Page =
    Page(
        id = id,
        docId = docId,
        index = index,
        originalPath = originalPath,
        processedPath = processedPath,
        thumbnailPath = thumbnailPath,
        ocrText = ocrText,
        ocrLanguage = ocrLanguage?.let { code ->
            Language.entries.firstOrNull { it.code == code }
        },
        ocrBlocks = OcrBlockCodec.decode(ocrBlocksJson)
    )
