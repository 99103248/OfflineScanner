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
    val updatedAt: Long,
    val isFavorite: Boolean = false,
    /** 非 null 表示已进回收站（软删除时间戳） */
    val deletedAt: Long? = null
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

/**
 * 文档库列表的轻量投影。
 *
 * 列表页不需要把每一页都读出来，只需要页数和封面缩略图；
 * 用一条带子查询的 SQL 一次取回，避免 N+1 查询。
 */
data class DocumentSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean,
    val deletedAt: Long?,
    val pageCount: Int,
    val coverPath: String?
)
