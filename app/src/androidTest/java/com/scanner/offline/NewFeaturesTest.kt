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

    @Test
    fun delete_page_then_append_does_not_overwrite_existing_page_file() = runBlocking {
        val storage = StorageManager(context, ExportPreferences(context))
        val docId = newDoc("NoCollision")
        val f0 = storage.newProcessedFile(docId, 0).apply { writeText("page0") }
        val f1 = storage.newProcessedFile(docId, 1).apply { writeText("page1_must_survive") }
        val p0 = db.documentDao().insertPage(
            PageEntity(
                docId = docId,
                index = 0,
                originalPath = f0.absolutePath,
                processedPath = f0.absolutePath,
                thumbnailPath = f0.absolutePath
            )
        )
        db.documentDao().insertPage(
            PageEntity(
                docId = docId,
                index = 1,
                originalPath = f1.absolutePath,
                processedPath = f1.absolutePath,
                thumbnailPath = f1.absolutePath
            )
        )
        val repo = DocumentRepositoryImpl(db.documentDao())
        repo.deletePage(p0)

        val nextIdx = repo.nextPageIndex(docId)
        assertEquals(1, nextIdx)
        val fNew = storage.newProcessedFile(docId, nextIdx).apply { writeText("page2_new") }
        assertTrue("新页文件路径不应与原第1页冲突", fNew.absolutePath != f1.absolutePath)
        assertEquals("page1_must_survive", f1.readText())
    }

    @Test
    fun textsToPdf_auto_paginates_long_text_without_truncation() {
        val longText = (1..120).joinToString("\n") { "第 $it 行：这是一段用于验证自动分页的长文本内容" }
        val pdf = File(context.cacheDir, "long_text_paginated.pdf")
        DocumentExporter().textsToPdf(listOf(longText), pdf)

        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue("120 行长文本应自动分页（至少 2 页），实际 ${renderer.pageCount}", renderer.pageCount >= 2)
        }
    }

    @Test
    fun id_card_composer_auto_rotates_portrait_and_adds_tiled_watermark() {
        // 模拟竖拍的身份证照片（宽 < 高）
        val front = Bitmap.createBitmap(630, 1000, Bitmap.Config.ARGB_8888)
        Canvas(front).drawColor(Color.rgb(220, 230, 245))
        val back = Bitmap.createBitmap(630, 1000, Bitmap.Config.ARGB_8888)
        Canvas(back).drawColor(Color.rgb(245, 225, 225))

        val composed = IdCardComposer(EdgeDetector(), PerspectiveCorrector()).compose(
            front = front,
            back = back,
            filterMode = com.scanner.offline.domain.model.FilterMode.ENHANCE,
            watermarkText = "仅供办理业务使用"
        )
        assertEquals(1240, composed.width)
        assertEquals(1754, composed.height)
        // 检查顶部空白带区域存在平铺水印绘制（不再全为纯白）
        var nonWhiteTopPixels = 0
        for (y in 20 until 160 step 4) {
            for (x in 40 until 1200 step 4) {
                if (composed.getPixel(x, y) != Color.WHITE) nonWhiteTopPixels++
            }
        }
        assertTrue("平铺防盗水印应在页眉留白区有可见像素", nonWhiteTopPixels > 0)
        composed.recycle()
        front.recycle()
        back.recycle()
    }

    @Test
    fun image_filter_remove_handwriting_erases_blue_and_red_keeps_black() {
        val src = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val c = Canvas(src)
        c.drawColor(Color.WHITE)
        val p = android.graphics.Paint().apply { strokeWidth = 14f }
        // 黑色印刷文字条
        p.color = Color.rgb(20, 20, 20)
        c.drawRect(30f, 40f, 270f, 80f, p)
        // 蓝色手写笔迹
        p.color = Color.rgb(20, 70, 230)
        c.drawRect(30f, 130f, 270f, 170f, p)
        // 红色批改笔迹
        p.color = Color.rgb(230, 30, 30)
        c.drawRect(30f, 220f, 270f, 260f, p)

        val out = com.scanner.offline.engine.image.ImageFilter().apply(
            src,
            com.scanner.offline.domain.model.FilterMode.REMOVE_HANDWRITING
        )
        val blackPix = out.getPixel(150, 60)
        val bluePix = out.getPixel(150, 150)
        val redPix = out.getPixel(150, 240)
        assertTrue("黑色印刷区应保留深色，实际 R=${Color.red(blackPix)}", Color.red(blackPix) < 100)
        assertTrue("蓝色手写区应被清除变白，实际 R=${Color.red(bluePix)}", Color.red(bluePix) > 220)
        assertTrue("红色批改区应被清除变白，实际 R=${Color.red(redPix)}", Color.red(redPix) > 220)
        out.recycle()
        src.recycle()
    }

    @Test
    fun image_transform_tone_adjust_tiled_watermark_and_colored_strokes() {
        val transform = ImageTransform()
        val src = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        Canvas(src).drawColor(Color.rgb(120, 120, 120))

        val brighter = transform.adjustTone(src, brightness = 45f, contrast = 1.2f)
        assertTrue("提亮后像素亮度应升高", Color.red(brighter.getPixel(120, 120)) > 150)
        brighter.recycle()

        val signed = transform.bakeColoredStrokes(
            src,
            listOf(
                com.scanner.offline.engine.image.ColoredStroke(
                    points = listOf(0.1f to 0.5f, 0.9f to 0.5f),
                    color = Color.rgb(21, 101, 192),
                    widthRatio = 0.04f
                )
            )
        )
        val mid = signed.getPixel(120, 120)
        assertTrue("蓝签笔画应正确写入画布", Color.blue(mid) > Color.red(mid) + 80)
        signed.recycle()

        val whiteBg = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        Canvas(whiteBg).drawColor(Color.WHITE)
        val tiled = transform.applyWatermark(
            whiteBg,
            "内部资料",
            com.scanner.offline.domain.model.WatermarkPosition.TILED,
            alpha = 100,
            colorRgb = Color.DKGRAY
        )
        var marked = 0
        for (y in 0 until 400 step 5) {
            for (x in 0 until 400 step 5) {
                if (tiled.getPixel(x, y) != Color.WHITE) marked++
            }
        }
        assertTrue("全屏平铺水印应覆盖图像多处区域", marked > 10)
        tiled.recycle()
        whiteBg.recycle()
        src.recycle()
    }

    @Test
    fun apply_page_filter_reapplies_nondestructively_from_original() = runBlocking {
        val repo = DocumentRepositoryImpl(db.documentDao())
        val storage = StorageManager(context, ExportPreferences(context))
        val docId = repo.create("FilterDoc")

        val origBmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        Canvas(origBmp).drawColor(Color.rgb(200, 50, 50))
        val origFile = storage.saveBitmap(origBmp, storage.newOriginalFile(docId, 0))
        val procFile = storage.saveBitmap(origBmp, storage.newProcessedFile(docId, 0))
        val thumbFile = storage.saveBitmap(origBmp, storage.newThumbnailFile(docId, 0))
        origBmp.recycle()

        val pageId = repo.addPage(
            Page(
                docId = docId,
                index = 0,
                originalPath = origFile.absolutePath,
                processedPath = procFile.absolutePath,
                thumbnailPath = thumbFile.absolutePath
            )
        )

        val useCase = com.scanner.offline.domain.usecase.ApplyPageFilterUseCase(
            repo = repo,
            imageFilter = com.scanner.offline.engine.image.ImageFilter(),
            updatePageImage = com.scanner.offline.domain.usecase.UpdatePageImageUseCase(repo, storage)
        )
        useCase(pageId, com.scanner.offline.domain.model.FilterMode.GRAYSCALE)

        val updated = repo.getPage(pageId)!!
        assertEquals("原图路径应保持不变", origFile.absolutePath, updated.originalPath)
        assertTrue("原图文件仍存在", origFile.exists())
        assertTrue("处理图应切换为新路径刷新缓存", updated.processedPath != procFile.absolutePath)
        assertTrue("新处理图文件应存在", File(updated.processedPath).exists())
        val decoded = android.graphics.BitmapFactory.decodeFile(updated.processedPath)!!
        val pix = decoded.getPixel(100, 100)
        assertEquals("灰度处理后 R 与 B 应近似相等", Color.red(pix).toDouble(), Color.blue(pix).toDouble(), 5.0)
        decoded.recycle()
    }

    @Test
    fun storage_manager_calculates_and_clears_cache() {
        val storage = StorageManager(context, ExportPreferences(context))
        val f = storage.newCacheImage("test_cache").apply { writeBytes(ByteArray(2048) { 1 }) }
        assertTrue("缓存体积应包含新建缓存文件", storage.calculateCacheBytes() >= 2048L)
        val removed = storage.clearAllCache()
        assertTrue("应至少清理 1 个文件", removed >= 1)
        assertTrue("缓存文件应已删除", !f.exists())
    }
}
