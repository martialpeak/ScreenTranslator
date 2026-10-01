package com.screenreader.translator.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_dictionary",
    indices = [
        Index(value = ["word"], unique = true)
    ]
)
data class OfflineDictionaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "word")
    val word: String,

    @ColumnInfo(name = "persian_meanings")
    val persianMeanings: String,

    @ColumnInfo(name = "phonetic")
    val phonetic: String? = null,

    @ColumnInfo(name = "part_of_speech")
    val partOfSpeech: String? = null
)
