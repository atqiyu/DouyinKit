package com.atqiyu.douyinkit.data

import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.data.db.AwemeDao
import com.atqiyu.douyinkit.data.db.AwemeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class HistoryRepository(
    private val dao: AwemeDao,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun observeAll(): Flow<List<AwemeEntity>> = dao.observeAll()

    fun observeFavorites(): Flow<List<AwemeEntity>> = dao.observeFavorites()

    fun observe(awemeId: String): Flow<AwemeEntity?> = dao.observeById(awemeId)

    suspend fun get(awemeId: String): AwemeEntity? = dao.getById(awemeId)

    suspend fun save(aweme: Aweme) {
        val existing = dao.getById(aweme.awemeId)
        val snapshot = json.encodeToString(Aweme.serializer(), aweme)
        dao.upsert(
            AwemeEntity(
                awemeId = aweme.awemeId,
                title = aweme.displayTitle,
                authorName = aweme.author.displayName,
                desc = aweme.desc,
                kind = aweme.kind.name,
                coverUrl = aweme.primaryCoverUrl,
                sourceUrl = aweme.sourceUrl ?: existing?.sourceUrl,
                snapshotJson = snapshot,
                isFavorite = existing?.isFavorite ?: false,
                tags = existing?.tags.orEmpty(),
                parsedAt = System.currentTimeMillis(),
                createTimeSeconds = aweme.createTimeSeconds,
            ),
        )
    }

    suspend fun saveAll(awemes: List<Aweme>) {
        awemes.forEach { save(it) }
    }

    suspend fun setFavorite(awemeId: String, favorite: Boolean) {
        dao.setFavorite(awemeId, favorite)
    }

    suspend fun setTags(awemeId: String, tags: List<String>) {
        dao.setTags(awemeId, normalizeTags(tags))
    }

    suspend fun delete(awemeId: String) {
        dao.delete(awemeId)
    }

    suspend fun cleanup(retentionDays: Int) {
        if (retentionDays <= 0) return
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        dao.deleteOldNonFavorites(cutoff)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    fun decode(entity: AwemeEntity): Aweme? {
        return runCatching {
            json.decodeFromString(Aweme.serializer(), entity.snapshotJson)
        }.getOrNull()
    }

    fun tagsOf(entity: AwemeEntity): List<String> {
        return entity.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun normalizeTags(tags: List<String>): String {
        return tags
            .flatMap { it.split(',', '，', '、') }
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 20 }
            .distinct()
            .take(12)
            .joinToString(",")
    }
}
