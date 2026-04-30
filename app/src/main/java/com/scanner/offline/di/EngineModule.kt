package com.scanner.offline.di

import com.scanner.offline.engine.export.DocumentExporter
import com.scanner.offline.engine.image.EdgeDetector
import com.scanner.offline.engine.image.ImageFilter
import com.scanner.offline.engine.image.PerspectiveCorrector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 图像处理 / 导出引擎的注入入口。
 *
 * - OCR 引擎（MlKitOcrEngine / PaddleOcrEngine / OcrEngineFactory）
 *   都已用 `@Inject constructor` + `@Singleton` 注解，Hilt 自动接管，无需在这里手动 provide。
 * - 这里只声明那些没有合适注入点的简单工具类。
 */
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides @Singleton
    fun provideEdgeDetector(): EdgeDetector = EdgeDetector()

    @Provides @Singleton
    fun providePerspectiveCorrector(): PerspectiveCorrector = PerspectiveCorrector()

    @Provides @Singleton
    fun provideImageFilter(): ImageFilter = ImageFilter()

    @Provides @Singleton
    fun provideDocumentExporter(): DocumentExporter = DocumentExporter()
}
