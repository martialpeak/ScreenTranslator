package com.screenreader.translator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.screenreader.translator.data.local.entity.GlossaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GlossaryDao {

    @Query("SELECT * FROM custom_glossary ORDER BY original_term ASC")
    fun getAllGlossaryFlow(): Flow<List<GlossaryEntity>>

    @Query("SELECT * FROM custom_glossary ORDER BY original_term ASC")
    suspend fun getAllList(): List<GlossaryEntity>

    @Query("SELECT * FROM custom_glossary WHERE is_active = 1")
    suspend fun getActiveGlossaryList(): List<GlossaryEntity>

    @Query("SELECT * FROM custom_glossary WHERE LOWER(TRIM(original_term)) = LOWER(TRIM(:term)) AND is_active = 1 LIMIT 1")
    suspend fun findCustomTranslation(term: String): GlossaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: GlossaryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GlossaryEntity>)

    @Query("SELECT COUNT(*) FROM custom_glossary")
    suspend fun count(): Int

    @Update
    suspend fun update(item: GlossaryEntity)

    @Delete
    suspend fun delete(item: GlossaryEntity)

    @Query("DELETE FROM custom_glossary WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE custom_glossary SET is_active = :isActive WHERE id = :id")
    suspend fun setStatus(id: Long, isActive: Boolean)

    @Query("DELETE FROM custom_glossary WHERE LOWER(TRIM(original_term)) = LOWER(TRIM(:term))")
    suspend fun deleteByTerm(term: String)

    @Query("DELETE FROM custom_glossary WHERE category = :category")
    suspend fun deleteByCategory(category: String): Int
}
