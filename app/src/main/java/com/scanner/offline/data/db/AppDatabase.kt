package com.scanner.offline.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentEntity::class, PageEntity::class],
    version = 3,
    // Room 2.8.x 在 KSP1 编译器类路径下反序列化已存在的 schema JSON 时会与内置
    // kotlinx-serialization 版本冲突（AbstractMethodError: FieldBundle$$serializer），故保持 false
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
