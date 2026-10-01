package com.screenreader.translator.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.screenreader.translator.data.local.entity.OfflineDictionaryEntity

@Dao
interface OfflineDictionaryDao {

    @Query("SELECT * FROM offline_dictionary WHERE LOWER(word) = LOWER(:word) LIMIT 1")
    suspend fun lookupWord(word: String): OfflineDictionaryEntity?

    @Query("SELECT * FROM offline_dictionary WHERE word LIKE :prefix || '%' ORDER BY LENGTH(word) ASC LIMIT 10")
    suspend fun suggestWords(prefix: String): List<OfflineDictionaryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<OfflineDictionaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(words: List<OfflineDictionaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(word: OfflineDictionaryEntity): Long

    @Query("SELECT * FROM offline_dictionary ORDER BY word ASC")
    suspend fun getAllWords(): List<OfflineDictionaryEntity>

    @Query("SELECT COUNT(*) FROM offline_dictionary")
    suspend fun getWordCount(): Int

    @Query("DELETE FROM offline_dictionary WHERE LOWER(TRIM(word)) = LOWER(TRIM(:word)) AND part_of_speech = :partOfSpeech")
    suspend fun deleteAutoLearnedWord(word: String, partOfSpeech: String = "مانهوا (آموزش‌دیده)")

    @Query("DELETE FROM offline_dictionary WHERE part_of_speech = :partOfSpeech")
    suspend fun deleteByPartOfSpeech(partOfSpeech: String): Int
}
