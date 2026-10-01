package com.screenreader.translator.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "translation_cache",
    indices = [
        Index(value = ["source_hash"], unique = true),
        Index(value = ["source_lang", "target_lang"])
    ]
)
data class TranslationCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "source_hash")
    val sourceHash: String,

    @ColumnInfo(name = "original_text")
    val originalText: String,

    @ColumnInfo(name = "translated_text")
    val translatedText: String,

    @ColumnInfo(name = "source_lang")
    val sourceLang: String = "en",

    @ColumnInfo(name = "target_lang")
    val targetLang: String = "fa",

    @ColumnInfo(name = "hit_count")
    val hitCount: Int = 1,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
