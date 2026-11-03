package com.scanner.offline.data.settings

import android.content.Context
import com.scanner.offline.domain.model.DocumentSort
import com.scanner.offline.domain.model.FilterMode
import com.scanner.offline.domain.model.PdfPaperSize
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val displayName: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

data class AppSettings(
    val defaultFilter: FilterMode = FilterMode.ENHANCE,
    val exportQuality: Int = 92,
    val pdfPaper: PdfPaperSize = PdfPaperSize.A4,
    val pdfMaxImageDimension: Int = 3000,
    val autoOcrOnSave: Boolean = false,
    /** 回收站保留天数；0 表示不自动清理 */
    val trashRetentionDays: Int = 30,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val gridView: Boolean = false,
    val sort: DocumentSort = DocumentSort.UPDATED
)

/** 应用设置（SharedPreferences 持久化 + StateFlow 供 Compose 订阅）。 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("offscan_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        write(next)
        _settings.value = next
    }

    private fun read(): AppSettings = AppSettings(
        defaultFilter = prefs.getString(KEY_FILTER, null)
            ?.let { runCatching { FilterMode.valueOf(it) }.getOrNull() }
            ?: FilterMode.ENHANCE,
        exportQuality = prefs.getInt(KEY_QUALITY, 92).coerceIn(40, 100),
        pdfPaper = prefs.getString(KEY_PDF_PAPER, null)
            ?.let { runCatching { PdfPaperSize.valueOf(it) }.getOrNull() }
            ?: PdfPaperSize.A4,
        pdfMaxImageDimension = prefs.getInt(KEY_PDF_MAX_DIM, 3000).coerceIn(1000, 6000),
        autoOcrOnSave = prefs.getBoolean(KEY_AUTO_OCR, false),
        trashRetentionDays = prefs.getInt(KEY_TRASH_DAYS, 30).coerceIn(0, 365),
        themeMode = prefs.getString(KEY_THEME, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        gridView = prefs.getBoolean(KEY_GRID, false),
        sort = prefs.getString(KEY_SORT, null)
            ?.let { runCatching { DocumentSort.valueOf(it) }.getOrNull() }
            ?: DocumentSort.UPDATED
    )

    private fun write(s: AppSettings) {
        prefs.edit()
            .putString(KEY_FILTER, s.defaultFilter.name)
            .putInt(KEY_QUALITY, s.exportQuality)
            .putString(KEY_PDF_PAPER, s.pdfPaper.name)
            .putInt(KEY_PDF_MAX_DIM, s.pdfMaxImageDimension)
            .putBoolean(KEY_AUTO_OCR, s.autoOcrOnSave)
            .putInt(KEY_TRASH_DAYS, s.trashRetentionDays)
            .putString(KEY_THEME, s.themeMode.name)
            .putBoolean(KEY_GRID, s.gridView)
            .putString(KEY_SORT, s.sort.name)
            .apply()
    }

    private companion object {
        const val KEY_FILTER = "default_filter"
        const val KEY_QUALITY = "export_quality"
        const val KEY_PDF_PAPER = "pdf_paper"
        const val KEY_PDF_MAX_DIM = "pdf_max_dim"
        const val KEY_AUTO_OCR = "auto_ocr_on_save"
        const val KEY_TRASH_DAYS = "trash_retention_days"
        const val KEY_THEME = "theme_mode"
        const val KEY_GRID = "grid_view"
        const val KEY_SORT = "sort"
    }
}
