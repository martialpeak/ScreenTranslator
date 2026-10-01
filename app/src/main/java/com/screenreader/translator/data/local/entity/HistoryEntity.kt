package com.screenreader.translator.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "translation_history",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["is_favorite"])
    ]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "original_text")
    val originalText: String,

    @ColumnInfo(name = "translated_text")
    val translatedText: String,

    @ColumnInfo(name = "source_app")
    val sourceApp: String? = null,

    @ColumnInfo(name = "source_lang")
    val sourceLang: String = "en",

    @ColumnInfo(name = "target_lang")
    val targetLang: String = "fa",

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
