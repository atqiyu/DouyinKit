package com.atqiyu.douyinkit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AwemeDao {
    @Query("SELECT * FROM awemes ORDER BY parsedAt DESC")
    fun observeAll(): Flow<List<AwemeEntity>>

    @Query("SELECT * FROM awemes WHERE isFavorite = 1 ORDER BY parsedAt DESC")
    fun observeFavorites(): Flow<List<AwemeEntity>>

    @Query("SELECT * FROM awemes WHERE awemeId = :awemeId LIMIT 1")
    fun observeById(awemeId: String): Flow<AwemeEntity?>

    @Query("SELECT * FROM awemes WHERE awemeId = :awemeId LIMIT 1")
    suspend fun getById(awemeId: String): AwemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AwemeEntity)

    @Query("UPDATE awemes SET isFavorite = :favorite WHERE awemeId = :awemeId")
    suspend fun setFavorite(awemeId: String, favorite: Boolean)

    @Query("UPDATE awemes SET tags = :tags WHERE awemeId = :awemeId")
    suspend fun setTags(awemeId: String, tags: String)

    @Query("DELETE FROM awemes WHERE awemeId = :awemeId")
    suspend fun delete(awemeId: String)

    @Query("DELETE FROM awemes WHERE isFavorite = 0 AND parsedAt < :cutoff")
    suspend fun deleteOldNonFavorites(cutoff: Long)

    @Query("DELETE FROM awemes")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM awemes")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM awemes WHERE isFavorite = 1")
    suspend fun favoriteCount(): Int
}
