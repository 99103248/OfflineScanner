package com.scanner.offline.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "scanner.db")
            // Room 2.9 弃用了无参版本：必须显式说明无 schema 时是否清空所有表。
            // 我们 schema 还在迭代期，还没生产用户数据需要保留 → true（drop all）
            .fallbackToDestructiveMigration(dropAllTables = true)
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
