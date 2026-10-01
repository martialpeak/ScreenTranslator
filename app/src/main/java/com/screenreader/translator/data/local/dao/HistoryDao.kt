package com.screenreader.translator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.screenreader.translator.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM translation_history ORDER BY created_at DESC")
    fun getAllHistoryFlow(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM translation_history WHERE is_favorite = 1 ORDER BY created_at DESC")
    fun getFavoritesFlow(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM translation_history WHERE original_text LIKE '%' || :query || '%' OR translated_text LIKE '%' || :query || '%' ORDER BY created_at DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: HistoryEntity): Long

    @Update
    suspend fun update(history: HistoryEntity)

    @Delete
    suspend fun delete(history: HistoryEntity)

    @Query("DELETE FROM translation_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM translation_history WHERE is_favorite = 0")
    suspend fun clearNonFavorites()

    @Query("DELETE FROM translation_history")
    suspend fun clearAll()

    @Query("UPDATE translation_history SET is_favorite = NOT is_favorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)
}
