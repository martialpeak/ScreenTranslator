package com.screenreader.translator.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "custom_glossary",
    indices = [
        Index(value = ["original_term"], unique = true)
    ]
)
data class GlossaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "original_term")
    val originalTerm: String,

    @ColumnInfo(name = "custom_translation")
    val customTranslation: String,

    @ColumnInfo(name = "category")
    val category: String = "عمومی",

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
