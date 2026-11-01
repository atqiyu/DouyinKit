package com.atqiyu.douyinkit.backup

import android.content.Context
import android.net.Uri
import com.atqiyu.douyinkit.BuildConfig
import com.atqiyu.douyinkit.di.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ConfigBackup {
    private const val SCHEMA_VERSION = 1
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun export(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val settings = context.appContainer.settingsRepository.settings.first()
        val file = ConfigFile(
            schemaVersion = SCHEMA_VERSION,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
            exportedAt = System.currentTimeMillis(),
            settings = ConfigSettings.from(settings),
        )
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            output.write(json.encodeToString(file).toByteArray(Charsets.UTF_8))
        } ?: throw BackupException("无法写入配置文件")
    }

    suspend fun import(context: Context, uri: Uri): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: throw BackupException("无法读取配置文件")
            val decoded = runCatching { json.decodeFromString<ConfigFile>(text) }
                .getOrElse { throw BackupException("配置文件格式不正确") }
            if (decoded.format != "douyinkit-config") {
                throw BackupException("这不是 DouyinKit 配置备份")
            }
            if (decoded.schemaVersion > SCHEMA_VERSION) {
                throw BackupException(
                    "配置来自更高版本（schema ${decoded.schemaVersion}），当前仅支持 $SCHEMA_VERSION",
                )
            }
            val settings = decoded.settings.toSettings()
            context.appContainer.settingsRepository.replaceAll(settings)
            decoded.appVersionCode to decoded.appVersionName
        }
}
