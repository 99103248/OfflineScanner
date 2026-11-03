package com.scanner.offline.domain.repository

import com.scanner.offline.domain.model.Document
import com.scanner.offline.domain.model.Page
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    /** 文档库（未删除），支持按名称/OCR 全文搜索，收藏置顶 */
    fun observeAll(query: String = ""): Flow<List<Document>>
    fun observeTrashed(): Flow<List<Document>>
    suspend fun get(id: Long): Document?
    suspend fun getPage(pageId: Long): Page?
    suspend fun create(name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun setFavorite(id: Long, favorite: Boolean)
    /** 移入回收站（软删除） */
    suspend fun moveToTrash(id: Long)
    suspend fun restoreFromTrash(id: Long)
    /** 永久删除（同时清理磁盘上的页面图片文件） */
    suspend fun deleteForever(id: Long)
    /** 清空回收站，返回清理掉的文档数量 */
    suspend fun emptyTrash(): Int
    /** 清理回收站中早于 [cutoff] 的文档，返回清理数量 */
    suspend fun purgeTrashOlderThan(cutoff: Long): Int
    suspend fun addPage(page: Page): Long
    /** 原子地取下一页序号（空文档返回 0） */
    suspend fun nextPageIndex(docId: Long): Int
    /** 按给定页面 id 顺序重排（收藏/封面用） */
    suspend fun reorderPages(docId: Long, orderedPageIds: List<Long>)
    /** 删除页后压紧 index */
    suspend fun reindexPages(docId: Long)
    /** 备份恢复：按原始时间戳/收藏状态创建文档 */
    suspend fun createImported(name: String, createdAt: Long, updatedAt: Long, isFavorite: Boolean): Long
    /** 备份用：完整加载所有未删除文档（含页面） */
    suspend fun getAllForBackup(): List<Document>
    suspend fun updatePageOcr(pageId: Long, text: String, langCode: String)
    suspend fun updatePageImage(pageId: Long, processedPath: String, thumbnailPath: String)
    suspend fun deletePage(pageId: Long)
}
