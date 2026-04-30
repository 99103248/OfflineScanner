package com.scanner.offline.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["docId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("docId")]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val docId: Long,
    val index: Int,
    val originalPath: String,
    val processedPath: String,
    val thumbnailPath: String,
    val ocrText: String? = null,
    val ocrLanguage: String? = null
)
