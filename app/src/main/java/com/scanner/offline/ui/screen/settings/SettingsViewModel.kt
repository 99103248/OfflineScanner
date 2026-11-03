package com.scanner.offline.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.scanner.offline.data.settings.SettingsRepository
import com.scanner.offline.data.settings.ThemeMode
import com.scanner.offline.domain.model.DocumentSort
import com.scanner.offline.domain.model.FilterMode
import com.scanner.offline.domain.model.PdfPaperSize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<com.scanner.offline.data.settings.AppSettings> = repo.settings

    fun setDefaultFilter(value: FilterMode) = repo.update { it.copy(defaultFilter = value) }
    fun setExportQuality(value: Int) = repo.update { it.copy(exportQuality = value.coerceIn(40, 100)) }
    fun setPdfPaper(value: PdfPaperSize) = repo.update { it.copy(pdfPaper = value) }
    fun setPdfMaxDimension(value: Int) = repo.update { it.copy(pdfMaxImageDimension = value.coerceIn(1000, 6000)) }
    fun setAutoOcr(value: Boolean) = repo.update { it.copy(autoOcrOnSave = value) }
    fun setTrashRetentionDays(value: Int) = repo.update { it.copy(trashRetentionDays = value.coerceIn(0, 365)) }
    fun setThemeMode(value: ThemeMode) = repo.update { it.copy(themeMode = value) }
    fun setGridView(value: Boolean) = repo.update { it.copy(gridView = value) }
    fun setSort(value: DocumentSort) = repo.update { it.copy(sort = value) }
}
