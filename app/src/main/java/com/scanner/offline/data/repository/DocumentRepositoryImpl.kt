package com.scanner.offline.data.repository

import com.scanner.offline.data.db.DocumentDao
import com.scanner.offline.data.db.DocumentEntity
import com.scanner.offline.data.db.PageEntity
import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.Page
import com.scanner.offline.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao
) : DocumentRepository {

    override fun observeAll(): Flow<List<Document>> =
        dao.observeAllDocuments().map { list ->
            list.map { it.toDomain(emptyList()) }
        }

    override suspend fun get(id: Long): Document? {
        val pair = dao.getDocumentWithPages(id) ?: return null
        return pair.first.toDomain(pair.second.map { it.toDomain() })
    }

    override suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertDocument(
            DocumentEntity(name = name, createdAt = now, updatedAt = now)
        )
    }

    override suspend fun rename(id: Long, name: String) {
        dao.renameDocument(id, name, System.currentTimeMillis())
    }

    override suspend fun delete(id: Long) {
        dao.deleteDocumentById(id)
    }

    override suspend fun addPage(page: Page): Long {
        return dao.insertPage(
            PageEntity(
                docId = page.docId,
                index = page.index,
                originalPath = page.originalPath,
                processedPath = page.processedPath,
                thumbnailPath = page.thumbnailPath,
                ocrText = page.ocrText,
                ocrLanguage = page.ocrLanguage?.code
            )
        )
    }

    override suspend fun updatePageOcr(pageId: Long, text: String, langCode: String) {
        dao.updatePageOcr(pageId, text, langCode)
    }

    override suspend fun deletePage(pageId: Long) {
        dao.deletePage(pageId)
    }
}

private fun DocumentEntity.toDomain(pages: List<Page>): Document =
    Document(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
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
        }
    )
