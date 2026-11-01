package com.atqiyu.douyinkit.backup

import android.content.Context
import android.net.Uri
import com.atqiyu.douyinkit.BuildConfig
import com.atqiyu.douyinkit.data.db.AwemeEntity
import com.atqiyu.douyinkit.data.db.DownloadHistoryEntity
import com.atqiyu.douyinkit.di.appContainer
import com.atqiyu.douyinkit.download.DownloadStatus
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object HistoryBackup {
    private const val SCHEMA_VERSION = 1
    private const val FORMAT = "douyinkit-history"
    private const val MANIFEST = "manifest.json"
    private const val AWEMES = "awemes.jsonl"
    private const val DOWNLOADS = "downloads.jsonl"
    private const val BATCH = 50

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    suspend fun export(
        context: Context,
        uri: Uri,
        onProgress: (BackupProgress) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        onProgress(BackupProgress(BackupPhase.PREPARING, message = "正在读取本机记录…"))
        val history = context.appContainer.historyRepository.allEntities()
        val downloads = context.appContainer.downloadHistoryRepository.allEntities()
        val total = history.size + downloads.size
        val manifest = HistoryManifest(
            format = FORMAT,
            schemaVersion = SCHEMA_VERSION,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
            exportedAt = System.currentTimeMillis(),
            historyCount = history.size,
            downloadCount = downloads.size,
        )
        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: throw BackupException("无法创建备份文件")
        var done = 0
        try {
            BufferedOutputStream(output).use { buffered ->
                ZipOutputStream(buffered).use { zip ->
                    zip.putNextEntry(ZipEntry(MANIFEST))
                    zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry(AWEMES))
                    val awemeWriter = OutputStreamWriter(zip, Charsets.UTF_8)
                    history.forEach { entity ->
                        awemeWriter.write(json.encodeToString(entity.toBackup()))
                        awemeWriter.write("\n")
                        done++
                        if (done % 20 == 0) {
                            awemeWriter.flush()
                            onProgress(
                                BackupProgress(
                                    BackupPhase.WRITING,
                                    done,
                                    total,
                                    "正在导出解析记录…",
                                ),
                            )
                        }
                    }
                    awemeWriter.flush()
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry(DOWNLOADS))
                    val downloadWriter = OutputStreamWriter(zip, Charsets.UTF_8)
                    downloads.forEach { entity ->
                        downloadWriter.write(json.encodeToString(entity.toBackup()))
                        downloadWriter.write("\n")
                        done++
                        if (done % 20 == 0) {
                            downloadWriter.flush()
                            onProgress(
                                BackupProgress(
                                    BackupPhase.WRITING,
                                    done,
                                    total,
                                    "正在导出下载记录…",
                                ),
                            )
                        }
                    }
                    downloadWriter.flush()
                    zip.closeEntry()
                }
            }
        } catch (error: Exception) {
            throw if (error is BackupException) error
            else BackupException("导出失败：${error.message ?: "写入错误"}")
        }
        onProgress(
            BackupProgress(
                BackupPhase.DONE,
                done,
                total,
                "已导出 $done 条记录",
            ),
        )
        total
    }

    suspend fun import(
        context: Context,
        uri: Uri,
        onProgress: (BackupProgress) -> Unit,
    ): HistoryImportResult = withContext(Dispatchers.IO) {
        onProgress(BackupProgress(BackupPhase.PREPARING, message = "正在检查压缩包…"))
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BackupException("无法读取备份文件")
        var manifest: HistoryManifest? = null
        val errors = mutableListOf<String>()
        var skipped = 0
        var historyImported = 0
        var downloadsImported = 0
        var done = 0
        var total = 0
        try {
            BufferedInputStream(input).use { buffered ->
                ZipInputStream(buffered).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        when (entry.name) {
                            MANIFEST -> {
                                val text = zip.readBytes().toString(Charsets.UTF_8)
                                val parsed = runCatching {
                                    json.decodeFromString<HistoryManifest>(text)
                                }.getOrElse {
                                    throw BackupException("备份清单格式不正确")
                                }
                                if (parsed.format != FORMAT) {
                                    throw BackupException("这不是 DouyinKit 记录备份")
                                }
                                if (parsed.schemaVersion > SCHEMA_VERSION) {
                                    throw BackupException(
                                        "备份版本过高（schema ${parsed.schemaVersion}）",
                                    )
                                }
                                manifest = parsed
                                total = parsed.historyCount + parsed.downloadCount
                                onProgress(
                                    BackupProgress(
                                        BackupPhase.READING,
                                        0,
                                        total,
                                        "备份版本 ${parsed.appVersionName.ifBlank { "未知" }}，准备导入…",
                                    ),
                                )
                            }

                            AWEMES -> {
                                val batch = ArrayList<AwemeEntity>(BATCH)
                                val reader = BufferedReader(InputStreamReader(zip, Charsets.UTF_8))
                                var lineNo = 0
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    lineNo++
                                    if (line.isBlank()) continue
                                    try {
                                        val record = json.decodeFromString<AwemeBackupRecord>(line)
                                        val entity = record.toEntity()
                                        if (entity == null) {
                                            skipped++
                                            addError(errors, "awemes 第 $lineNo 行缺少 ID 或快照")
                                        } else {
                                            batch += entity
                                            if (batch.size >= BATCH) {
                                                context.appContainer.historyRepository
                                                    .importEntities(batch)
                                                historyImported += batch.size
                                                done += batch.size
                                                batch.clear()
                                                onProgress(
                                                    BackupProgress(
                                                        BackupPhase.READING,
                                                        done,
                                                        total.coerceAtLeast(done),
                                                        "正在导入解析记录…",
                                                        errors.size,
                                                    ),
                                                )
                                            }
                                        }
                                    } catch (error: Exception) {
                                        skipped++
                                        addError(errors, "awemes 第 $lineNo 行无法解析")
                                    }
                                }
                                if (batch.isNotEmpty()) {
                                    context.appContainer.historyRepository.importEntities(batch)
                                    historyImported += batch.size
                                    done += batch.size
                                }
                            }

                            DOWNLOADS -> {
                                val batch = ArrayList<DownloadHistoryEntity>(BATCH)
                                val reader = BufferedReader(InputStreamReader(zip, Charsets.UTF_8))
                                var lineNo = 0
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    lineNo++
                                    if (line.isBlank()) continue
                                    try {
                                        val record = json.decodeFromString<DownloadBackupRecord>(line)
                                        val entity = record.toEntity()
                                        if (entity == null) {
                                            skipped++
                                            addError(errors, "downloads 第 $lineNo 行缺少记录字段")
                                        } else {
                                            batch += entity
                                            if (batch.size >= BATCH) {
                                                context.appContainer.downloadHistoryRepository
                                                    .importEntities(batch)
                                                downloadsImported += batch.size
                                                done += batch.size
                                                batch.clear()
                                                onProgress(
                                                    BackupProgress(
                                                        BackupPhase.READING,
                                                        done,
                                                        total.coerceAtLeast(done),
                                                        "正在导入下载记录…",
                                                        errors.size,
                                                    ),
                                                )
                                            }
                                        }
                                    } catch (error: Exception) {
                                        skipped++
                                        addError(errors, "downloads 第 $lineNo 行无法解析")
                                    }
                                }
                                if (batch.isNotEmpty()) {
                                    context.appContainer.downloadHistoryRepository
                                        .importEntities(batch)
                                    downloadsImported += batch.size
                                    done += batch.size
                                }
                            }

                            else -> Unit
                        }
                        zip.closeEntry()
                    }
                }
            }
        } catch (error: Exception) {
            throw if (error is BackupException) error
            else BackupException("导入失败：${error.message ?: "压缩包损坏"}")
        }
        if (manifest == null) throw BackupException("备份缺少 manifest.json")
        onProgress(
            BackupProgress(
                BackupPhase.DONE,
                done,
                total.coerceAtLeast(done),
                "导入完成：解析 $historyImported 条，下载 $downloadsImported 条，跳过 $skipped 条",
                errors.size,
            ),
        )
        HistoryImportResult(
            historyImported = historyImported,
            downloadsImported = downloadsImported,
            skipped = skipped,
            errors = errors.toList(),
        )
    }

    private fun addError(errors: MutableList<String>, message: String) {
        if (errors.size < 6) errors += message
    }

    private fun AwemeEntity.toBackup() = AwemeBackupRecord(
        awemeId = awemeId,
        title = title,
        authorName = authorName,
        desc = desc,
        kind = kind,
        coverUrl = coverUrl,
        sourceUrl = sourceUrl,
        snapshotJson = snapshotJson,
        isFavorite = isFavorite,
        tags = tags,
        parsedAt = parsedAt,
        createTimeSeconds = createTimeSeconds,
    )

    private fun AwemeBackupRecord.toEntity(): AwemeEntity? {
        if (awemeId.isBlank() || snapshotJson.isBlank()) return null
        return AwemeEntity(
            awemeId = awemeId,
            title = title,
            authorName = authorName,
            desc = desc,
            kind = kind.ifBlank { "VIDEO" },
            coverUrl = coverUrl,
            sourceUrl = sourceUrl,
            snapshotJson = snapshotJson,
            isFavorite = isFavorite,
            tags = tags,
            parsedAt = parsedAt,
            createTimeSeconds = createTimeSeconds,
        )
    }

    private fun DownloadHistoryEntity.toBackup() = DownloadBackupRecord(
        taskId = taskId,
        awemeId = awemeId,
        title = title,
        kind = kind,
        status = status,
        message = message,
        requestJson = requestJson,
        filesJson = filesJson,
        zipJson = zipJson,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun DownloadBackupRecord.toEntity(): DownloadHistoryEntity? {
        if (taskId.isBlank() || awemeId.isBlank() ||
            requestJson.isBlank() || filesJson.isBlank()
        ) {
            return null
        }
        val statusValue = runCatching { DownloadStatus.valueOf(status) }
            .getOrDefault(DownloadStatus.FAILED)
        val safeStatus = if (statusValue == DownloadStatus.QUEUED ||
            statusValue == DownloadStatus.RUNNING
        ) {
            DownloadStatus.FAILED
        } else {
            statusValue
        }
        return DownloadHistoryEntity(
            taskId = taskId,
            awemeId = awemeId,
            title = title,
            kind = kind,
            status = safeStatus.name,
            message = if (safeStatus == DownloadStatus.FAILED && message.isNullOrBlank()) {
                "从备份导入的未完成任务，可重试"
            } else {
                message
            },
            requestJson = requestJson,
            filesJson = filesJson,
            zipJson = zipJson,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
