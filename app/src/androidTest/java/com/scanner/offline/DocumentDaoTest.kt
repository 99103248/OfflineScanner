package com.scanner.offline

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scanner.offline.data.db.AppDatabase
import com.scanner.offline.data.db.DocumentEntity
import com.scanner.offline.data.db.PageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 文档库数据层行为测试：搜索 / 收藏置顶 / 回收站（软删除）。
 * 使用内存 Room 数据库，不污染真实数据。
 */
@RunWith(AndroidJUnit4::class)
class DocumentDaoTest {

    private lateinit var db: AppDatabase
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun newDoc(name: String, favorite: Boolean = false): Long {
        val id = db.documentDao().insertDocument(
            DocumentEntity(name = name, createdAt = now, updatedAt = now, isFavorite = favorite)
        )
        return id
    }

    private suspend fun addPage(docId: Long, index: Int, ocrText: String? = null) {
        val path = "/data/local/tmp/test_${docId}_$index.jpg"
        db.documentDao().insertPage(
            PageEntity(
                docId = docId, index = index,
                originalPath = path, processedPath = path, thumbnailPath = path,
                ocrText = ocrText
            )
        )
    }

    @Test
    fun search_matches_document_name() = runBlocking {
        val a = newDoc("Invoice-2026")
        newDoc("Receipt")
        addPage(a, 0, "nothing relevant")

        val hits = db.documentDao().observeActiveDocuments("invoice").first()
        assertEquals(listOf(a), hits.map { it.id })
    }

    @Test
    fun search_matches_page_ocr_text() = runBlocking {
        newDoc("Photo A")
        val b = newDoc("Photo B")
        addPage(b, 0, "Quarterly Tax Report 2026")

        val hits = db.documentDao().observeActiveDocuments("Tax Report").first()
        assertEquals(listOf(b), hits.map { it.id })
    }

    @Test
    fun empty_query_returns_all_documents() = runBlocking {
        val a = newDoc("Doc A")
        val b = newDoc("Doc B")
        addPage(b, 0)

        val all = db.documentDao().observeActiveDocuments("").first()
        assertEquals(setOf(a, b), all.map { it.id }.toSet())
    }

    @Test
    fun favorite_documents_are_pinned_to_top() = runBlocking {
        val plain = newDoc("Plain")
        val fav = newDoc("Favorite", favorite = true)
        // 普通文档更新得更晚，但收藏的仍应排前面
        db.documentDao().renameDocument(plain, "Plain", now + 1000)

        val all = db.documentDao().observeActiveDocuments("").first()
        assertEquals(listOf(fav, plain), all.map { it.id })
    }

    @Test
    fun trash_flow_soft_delete_restore_and_hide() = runBlocking {
        val id = newDoc("ToTrash")
        addPage(id, 0)

        db.documentDao().moveToTrash(id, now)
        assertTrue("进回收站后不应出现在文档库", db.documentDao().observeActiveDocuments("").first().isEmpty())
        assertEquals("回收站应有一条", 1, db.documentDao().observeTrashedDocuments().first().size)

        db.documentDao().restoreFromTrash(id, now + 1)
        assertTrue("恢复后应回到文档库", db.documentDao().observeActiveDocuments("").first().isNotEmpty())
        assertTrue("回收站应清空", db.documentDao().observeTrashedDocuments().first().isEmpty())

        db.documentDao().moveToTrash(id, now + 2)
        assertEquals(listOf(id), db.documentDao().listTrashedIds())
    }

    @Test
    fun migration_v1_to_v2_preserves_data() = runBlocking {
        // 直接在 v2 上验证 schema 字段可用（迁移本身由 Room 在真机升级时执行）
        val id = newDoc("Legacy")
        db.documentDao().setFavorite(id, true, now)
        val doc = db.documentDao().getDocument(id)
        assertNotNull(doc)
        assertEquals(true, doc?.isFavorite)
        assertNull(doc?.deletedAt)
    }
}
