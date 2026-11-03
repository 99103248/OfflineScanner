package com.scanner.offline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scanner.offline.data.backup.BackupManager
import com.scanner.offline.data.db.AppDatabase
import com.scanner.offline.data.db.DocumentEntity
import com.scanner.offline.data.db.PageEntity
import com.scanner.offline.data.repository.DocumentRepositoryImpl
import com.scanner.offline.data.storage.ExportPreferences
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.domain.model.Language
import com.scanner.offline.domain.model.Page
import com.scanner.offline.domain.model.PdfPaperSize
import com.scanner.offline.domain.model.RedactStyle
import com.scanner.offline.engine.export.DocumentExporter
import com.scanner.offline.engine.image.EdgeDetector
import com.scanner.offline.engine.image.IdCardComposer
import com.scanner.offline.engine.image.ImageTransform
import com.scanner.offline.engine.image.NormalizedCropRect
import com.scanner.offline.engine.image.PerspectiveCorrector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 新增功能的集成测试：文档摘要查询、页面重排、PDF 纸张、备份恢复、证件合成、打码。
 */
@RunWith(AndroidJUnit4::class)
class NewFeaturesTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun newDoc(name: String, favorite: Boolean = false): Long =
        db.documentDao().insertDocument(
            DocumentEntity(name = name, createdAt = now, updatedAt = now, isFavorite = favorite)
        )

    private suspend fun addPage(
        docId: Long,
        index: Int,
        thumb: String = "/tmp/thumb_$index.jpg",
        ocr: String? = null
    ): Long = db.documentDao().insertPage(
        PageEntity(
            docId = docId,
            index = index,
            originalPath = "/tmp/orig_$index.jpg",
            processedPath = "/tmp/proc_$index.jpg",
            thumbnailPath = thumb,
            ocrText = ocr
        )
    )

    private fun writeBitmap(name: String, bitmap: Bitmap): File {
        val file = File(context.cacheDir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return file
    }

    @Test
    fun document_summary_reports_page_count_and_cover() = runBlocking {
        val docId = newDoc("Summary")
        addPage(docId, 0, thumb = "/tmp/first.jpg")
        addPage(docId, 1, thumb = "/tmp/second.jpg")

        val summary = db.documentDao().observeActiveDocuments("").first().single()
        assertEquals(docId, summary.id)
        assertEquals(2, summary.pageCount)
        assertEquals("/tmp/first.jpg", summary.coverPath)
    }

    @Test
    fun page_reorder_and_reindex_after_delete() = runBlocking {
        val docId = newDoc("Pages")
        val p0 = addPage(docId, 0)
        val p1 = addPage(docId, 1)
        val p2 = addPage(docId, 2)

        db.documentDao().reorderPages(listOf(p2, p0, p1))
        val ordered = db.documentDao().getPagesByDoc(docId)
        assertEquals(listOf(p2, p0, p1), ordered.map { it.id })
        assertEquals(listOf(0, 1, 2), ordered.map { it.index })

        db.documentDao().deletePage(p0)
        db.documentDao().reindexPages(docId)
        val after = db.documentDao().getPagesByDoc(docId)
        assertEquals(listOf(p2, p1), after.map { it.id })
        assertEquals(listOf(0, 1), after.map { it.index })
    }

    @Test
    fun pdf_export_uses_a4_page_size() {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        val img = writeBitmap("a4_page.jpg", bitmap)
        bitmap.recycle()

        val pdf = File(context.cacheDir, "a4_export.pdf")
        DocumentExporter().imagesToPdf(
            imagePaths = listOf(img.absolutePath),
            output = pdf,
            paperSize = PdfPaperSize.A4
        )

        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals(1, renderer.pageCount)
            renderer.openPage(0).use { page ->
                // 横图会自动使用 A4 横向：842 x 595 pt
                assertTrue("A4 横向宽度应约为 842pt，实际 ${page.width}", page.width in 837..847)
                assertTrue("A4 横向高度应约为 595pt，实际 ${page.height}", page.height in 590..600)
            }
        }
    }

    @Test
    fun backup_restore_roundtrip_preserves_pages_and_ocr() = runBlocking {
        val dao = db.documentDao()
        val repo = DocumentRepositoryImpl(dao)
        val storage = StorageManager(context, ExportPreferences(context))
        val backup = BackupManager(context, repo, storage)

        val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        val img = writeBitmap("backup_page.jpg", bitmap)
        bitmap.recycle()

        val docId = repo.create("BackupDoc")
        repo.addPage(
            Page(
                docId = docId,
                index = 0,
                originalPath = img.absolutePath,
                processedPath = img.absolutePath,
                thumbnailPath = img.absolutePath,
                ocrText = "备份测试 OCR 文本",
                ocrLanguage = Language.CHINESE
            )
        )

        val buffer = ByteArrayOutputStream()
        val backupSummary = backup.backupTo(buffer)
        assertEquals(1, backupSummary.documentCount)
        assertEquals(1, backupSummary.pageCount)
        assertTrue("备份 zip 应有内容", buffer.size() > 100)

        val before = dao.observeActiveDocuments("").first().size
        val restoreSummary = backup.restoreFrom(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(1, restoreSummary.documentCount)
        assertEquals(1, restoreSummary.pageCount)

        val after = dao.observeActiveDocuments("").first()
        assertEquals(before + 1, after.size)
        val restored = after.first { it.name == "BackupDoc" && it.id != docId }
        val full = repo.get(restored.id)
        assertNotNull(full)
        assertEquals(1, full!!.pages.size)
        assertEquals("备份测试 OCR 文本", full.pages.first().ocrText)
        assertTrue(File(full.pages.first().processedPath).exists())
    }

    @Test
    fun id_card_composer_outputs_a4_canvas() {
        val front = Bitmap.createBitmap(1000, 630, Bitmap.Config.ARGB_8888)
        Canvas(front).drawColor(Color.rgb(240, 240, 250))
        val back = Bitmap.createBitmap(1000, 630, Bitmap.Config.ARGB_8888)
        Canvas(back).drawColor(Color.rgb(250, 240, 240))

        val composed = IdCardComposer(EdgeDetector(), PerspectiveCorrector()).compose(front, back)
        assertEquals(1240, composed.width)
        assertEquals(1754, composed.height)
        // 页面顶部应是白色留白
        assertEquals(Color.WHITE, composed.getPixel(10, 10))
        // 卡片区域应被绘制
        assertTrue(composed.getPixel(620, 500) != Color.WHITE)
        composed.recycle()
        front.recycle()
        back.recycle()
    }

    @Test
    fun redact_black_covers_region() {
        val src = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        Canvas(src).drawColor(Color.WHITE)
        val out = ImageTransform().redact(
            src,
            listOf(NormalizedCropRect(0.25f, 0.25f, 0.75f, 0.75f)),
            RedactStyle.BLACK
        )
        assertEquals(Color.BLACK, out.getPixel(100, 100))
        assertEquals(Color.WHITE, out.getPixel(10, 10))
        out.recycle()
        src.recycle()
    }

    @Test
    fun trash_purge_removes_only_expired_documents() = runBlocking {
        val dao = db.documentDao()
        val repo = DocumentRepositoryImpl(dao)
        val old = newDoc("Old")
        val recent = newDoc("Recent")
        dao.moveToTrash(old, now - 40L * 24 * 60 * 60 * 1000)
        dao.moveToTrash(recent, now)

        val removed = repo.purgeTrashOlderThan(now - 30L * 24 * 60 * 60 * 1000)
        assertEquals(1, removed)
        assertEquals(listOf(recent), dao.listTrashedIds())
    }
}
