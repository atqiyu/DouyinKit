package com.atqiyu.douyinkit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Lightweight row used by list screens that do not need the full snapshot JSON. */
data class AwemeMeta(
    val awemeId: String,
    val title: String,
    val authorName: String,
    val desc: String,
    val kind: String,
    val coverUrl: String?,
    val sourceUrl: String?,
    val createTimeSeconds: Long?,
    val isFavorite: Boolean,
    val tags: String,
    val parsedAt: Long,
)

@Dao
interface AwemeDao {
    @Query("SELECT * FROM awemes ORDER BY parsedAt DESC")
    fun observeAll(): Flow<List<AwemeEntity>>

    @Query("SELECT * FROM awemes ORDER BY parsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AwemeEntity>>

    @Query(
        "SELECT awemeId, title, authorName, desc, kind, coverUrl, sourceUrl, " +
            "createTimeSeconds, isFavorite, tags, parsedAt " +
            "FROM awemes ORDER BY parsedAt DESC",
    )
    fun observeMeta(): Flow<List<AwemeMeta>>

    @Query("SELECT * FROM awemes WHERE isFavorite = 1 ORDER BY parsedAt DESC")
    fun observeFavorites(): Flow<List<AwemeEntity>>

    @Query("SELECT * FROM awemes WHERE awemeId = :awemeId LIMIT 1")
    fun observeById(awemeId: String): Flow<AwemeEntity?>

    @Query("SELECT * FROM awemes WHERE awemeId = :awemeId LIMIT 1")
    suspend fun getById(awemeId: String): AwemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AwemeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AwemeEntity>)

    @Query("SELECT * FROM awemes ORDER BY parsedAt DESC")
    suspend fun getAll(): List<AwemeEntity>

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
