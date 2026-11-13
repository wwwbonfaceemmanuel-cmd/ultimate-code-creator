package com.ultimate.codecreator.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY favorite DESC, updatedAt DESC")
    fun observeAll(): Flow<List<SnippetEntity>>

    @Query(
        "SELECT * FROM snippets WHERE title LIKE '%' || :query || '%' " +
            "OR code LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' " +
            "ORDER BY favorite DESC, updatedAt DESC"
    )
    fun observeSearch(query: String): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE id = :id")
    suspend fun findById(id: String): SnippetEntity?

    @Query("SELECT COUNT(*) FROM snippets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SnippetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SnippetEntity>)

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM snippets")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<SnippetEntity>) {
        deleteAll()
        upsertAll(entities)
    }
}
