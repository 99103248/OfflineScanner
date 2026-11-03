package com.scanner.offline.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scanner.offline.data.db.AppDatabase
import com.scanner.offline.data.db.DocumentDao
import com.scanner.offline.data.repository.DocumentRepositoryImpl
import com.scanner.offline.domain.repository.DocumentRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** v1 → v2：documents 表新增收藏与回收站字段（保留既有数据） */
    private val MIGRATION_1_2: Migration = Migration1To2()

    private class Migration1To2 : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE documents ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE documents ADD COLUMN deletedAt INTEGER"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "scanner.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository
}
