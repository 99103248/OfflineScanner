package com.scanner.offline.domain.repository

import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.model.Page
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeAll(): Flow<List<Document>>
    suspend fun get(id: Long): Document?
    suspend fun create(name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun delete(id: Long)
    suspend fun addPage(page: Page): Long
    suspend fun updatePageOcr(pageId: Long, text: String, langCode: String)
    suspend fun deletePage(pageId: Long)
}
