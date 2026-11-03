package com.scanner.offline.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Delete
    suspend fun deleteDocument(doc: DocumentEntity)

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocument(id: Long): DocumentEntity?

    @Query("UPDATE documents SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameDocument(id: Long, name: String, updatedAt: Long)

    @Query("UPDATE documents SET isFavorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, updatedAt: Long)

    /** 软删除：进回收站 */
    @Query("UPDATE documents SET deletedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun moveToTrash(id: Long, deletedAt: Long)

    /** 从回收站恢复 */
    @Query("UPDATE documents SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreFromTrash(id: Long, updatedAt: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    /**
     * 文档库列表（未删除）。支持按名称或任意页 OCR 全文搜索，
     * 收藏置顶，其余按更新时间倒序。
     *
     * 页数与封面用相关子查询一次取回，避免列表页 N+1 查询。
     */
    @Query(
        """
        SELECT d.id AS id,
               d.name AS name,
               d.createdAt AS createdAt,
               d.updatedAt AS updatedAt,
               d.isFavorite AS isFavorite,
               d.deletedAt AS deletedAt,
               (SELECT COUNT(*) FROM pages p WHERE p.docId = d.id) AS pageCount,
               (SELECT p.thumbnailPath FROM pages p
                WHERE p.docId = d.id ORDER BY p.`index` ASC LIMIT 1) AS coverPath
        FROM documents d
        WHERE d.deletedAt IS NULL
          AND (:q = '' OR d.name LIKE '%' || :q || '%' OR EXISTS (
                SELECT 1 FROM pages p2
                WHERE p2.docId = d.id AND p2.ocrText LIKE '%' || :q || '%'
              ))
        ORDER BY d.isFavorite DESC, d.updatedAt DESC
        """
    )
    fun observeActiveDocuments(q: String): Flow<List<DocumentSummary>>

    /** 回收站列表，按删除时间倒序 */
    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashedDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT id FROM documents WHERE deletedAt IS NOT NULL")
    suspend fun listTrashedIds(): List<Long>

    /** 备份用：取出所有文档（含回收站） */
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    suspend fun getAllDocuments(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity): Long

    @Query("SELECT * FROM pages WHERE docId = :docId ORDER BY `index` ASC")
    suspend fun getPagesByDoc(docId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    suspend fun getPage(id: Long): PageEntity?

    @Query("UPDATE pages SET ocrText = :text, ocrLanguage = :lang WHERE id = :pageId")
    suspend fun updatePageOcr(pageId: Long, text: String, lang: String)

    @Query("UPDATE pages SET processedPath = :processed, thumbnailPath = :thumb WHERE id = :pageId")
    suspend fun updatePageImage(pageId: Long, processed: String, thumb: String)

    /** 当前文档最大页序号；空文档返回 -1。用于原子地计算下一页 index。 */
    @Query("SELECT COALESCE(MAX(`index`), -1) FROM pages WHERE docId = :docId")
    suspend fun maxPageIndex(docId: Long): Int

    @Query("SELECT COUNT(*) FROM pages WHERE docId = :docId")
    suspend fun countPages(docId: Long): Int

    @Query("UPDATE pages SET `index` = :index WHERE id = :pageId")
    suspend fun updatePageIndex(pageId: Long, index: Int)

    /** 按给定顺序重排页面；调用方需保证 ids 覆盖该文档全部页面。 */
    @Transaction
    suspend fun reorderPages(orderedPageIds: List<Long>) {
        orderedPageIds.forEachIndexed { idx, id -> updatePageIndex(id, idx) }
    }

    /** 删除页后把剩余页的 index 重新压紧为 0..n-1。 */
    @Transaction
    suspend fun reindexPages(docId: Long) {
        getPagesByDoc(docId).forEachIndexed { idx, page ->
            if (page.index != idx) updatePageIndex(page.id, idx)
        }
    }

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePage(pageId: Long)

    /** 一次拿出文档及全部页 */
    @Transaction
    suspend fun getDocumentWithPages(id: Long): Pair<DocumentEntity, List<PageEntity>>? {
        val doc = getDocument(id) ?: return null
        return doc to getPagesByDoc(id)
    }
}
