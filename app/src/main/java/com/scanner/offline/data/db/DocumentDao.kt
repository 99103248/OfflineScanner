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

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity): Long

    @Query("SELECT * FROM pages WHERE docId = :docId ORDER BY `index` ASC")
    suspend fun getPagesByDoc(docId: Long): List<PageEntity>

    @Query("UPDATE pages SET ocrText = :text, ocrLanguage = :lang WHERE id = :pageId")
    suspend fun updatePageOcr(pageId: Long, text: String, lang: String)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePage(pageId: Long)

    /** 一次拿出文档及全部页 */
    @Transaction
    suspend fun getDocumentWithPages(id: Long): Pair<DocumentEntity, List<PageEntity>>? {
        val doc = getDocument(id) ?: return null
        return doc to getPagesByDoc(id)
    }
}
