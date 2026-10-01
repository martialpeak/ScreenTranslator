package com.screenreader.translator.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.screenreader.translator.data.local.entity.TranslationCacheEntity

@Dao
interface TranslationCacheDao {

    @Query("SELECT * FROM translation_cache WHERE source_hash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): TranslationCacheEntity?

    @Query("SELECT * FROM translation_cache WHERE original_text = :text LIMIT 1")
    suspend fun getByOriginalText(text: String): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: TranslationCacheEntity): Long

    @Query("UPDATE translation_cache SET hit_count = hit_count + 1, updated_at = :timestamp WHERE source_hash = :hash")
    suspend fun incrementHitCount(hash: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun getCacheCount(): Int

    @Query("DELETE FROM translation_cache WHERE updated_at < :olderThanTimestamp")
    suspend fun pruneOldEntries(olderThanTimestamp: Long): Int

    @Query("SELECT * FROM translation_cache ORDER BY hit_count DESC")
    suspend fun getAllCache(): List<TranslationCacheEntity>

    @Query("DELETE FROM translation_cache WHERE source_hash = :hash")
    suspend fun deleteByHash(hash: String)

    @Query("DELETE FROM translation_cache WHERE original_text = :text")
    suspend fun deleteByOriginalText(text: String)

    @Query("DELETE FROM translation_cache")
    suspend fun clearCache()
}
