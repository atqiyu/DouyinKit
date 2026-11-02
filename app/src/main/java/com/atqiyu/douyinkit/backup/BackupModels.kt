package com.atqiyu.douyinkit.backup

import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.data.prefs.DownloadSortMode
import com.atqiyu.douyinkit.data.prefs.FloatingMode
import com.atqiyu.douyinkit.data.prefs.FloatingPanelColor
import com.atqiyu.douyinkit.data.prefs.FloatingPanelStyle
import com.atqiyu.douyinkit.data.prefs.ThemeMode
import kotlinx.serialization.Serializable

enum class BackupPhase { PREPARING, READING, WRITING, DONE, ERROR }

data class BackupProgress(
    val phase: BackupPhase,
    val done: Int = 0,
    val total: Int = 0,
    val message: String = "",
    val errors: Int = 0,
) {
    val fraction: Float?
        get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
}

data class HistoryImportResult(
    val historyImported: Int,
    val downloadsImported: Int,
    val skipped: Int,
    val errors: List<String>,
)

class BackupException(message: String) : Exception(message)

@Serializable
data class ConfigFile(
    val format: String = "douyinkit-config",
    val schemaVersion: Int = 1,
    val appVersionCode: Int = 0,
    val appVersionName: String = "",
    val exportedAt: Long = 0,
    val settings: ConfigSettings = ConfigSettings(),
)

@Serializable
data class ConfigSettings(
    val defaultQualityHeight: Int = 1080,
    val preferH264: Boolean = true,
    val includeLivePhotos: Boolean = true,
    val includeMusic: Boolean = true,
    val createZip: Boolean = false,
    val clipboardDetection: Boolean = true,
    val themeMode: String = "SYSTEM",
    val dynamicColor: Boolean = false,
    val historyRetentionDays: Int = 90,
    val downloadsGridView: Boolean = true,
    val downloadsSortMode: String = "LATEST",
    val onboardingAccepted: Boolean = false,
    val floatingBallEnabled: Boolean = false,
    val floatingMode: String = "STANDARD",
    val floatingAutoDownload: Boolean = true,
    val floatingOrbSizeDp: Int = 48,
    val floatingOrbAlpha: Float = 0.88f,
    val floatingPanelOpacity: Float = 0.72f,
    val floatingPanelStyle: String = "GLASS",
    val floatingPanelColor: String = "DARK",
    val floatingPanelBlur: Int = 18,
    val floatingShowClipboard: Boolean = true,
    val floatingActionDistanceDp: Int = 8,
    val floatingActionAngleOffset: Int = 0,
    val floatingPanelMinWidthDp: Int = 160,
    val floatingPanelMinHeightDp: Int = 64,
    val floatingIdleTimeoutSeconds: Int = 45,
) {
    fun toSettings(): AppSettings = AppSettings(
        defaultQualityHeight = defaultQualityHeight.coerceIn(0, 2160),
        preferH264 = preferH264,
        includeLivePhotos = includeLivePhotos,
        includeMusic = includeMusic,
        createZip = createZip,
        clipboardDetection = clipboardDetection,
        themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = dynamicColor,
        historyRetentionDays = historyRetentionDays.coerceIn(0, 3650),
        downloadsGridView = downloadsGridView,
        downloadsSortMode = runCatching { DownloadSortMode.valueOf(downloadsSortMode) }
            .getOrDefault(DownloadSortMode.LATEST),
        onboardingAccepted = onboardingAccepted,
        floatingBallEnabled = floatingBallEnabled,
        floatingMode = runCatching { FloatingMode.valueOf(floatingMode) }
            .getOrDefault(FloatingMode.STANDARD),
        floatingAutoDownload = floatingAutoDownload,
        floatingOrbSizeDp = floatingOrbSizeDp.coerceIn(38, 72),
        floatingOrbAlpha = floatingOrbAlpha.coerceIn(0.35f, 1f),
        floatingPanelOpacity = floatingPanelOpacity.coerceIn(0.2f, 1f),
        floatingPanelStyle = runCatching { FloatingPanelStyle.valueOf(floatingPanelStyle) }
            .getOrDefault(FloatingPanelStyle.GLASS),
        floatingPanelColor = runCatching { FloatingPanelColor.valueOf(floatingPanelColor) }
            .getOrDefault(FloatingPanelColor.DARK),
        floatingPanelBlur = floatingPanelBlur.coerceIn(0, 25),
        floatingShowClipboard = floatingShowClipboard,
        floatingActionDistanceDp = floatingActionDistanceDp.coerceIn(0, 24),
        floatingActionAngleOffset = floatingActionAngleOffset.coerceIn(-60, 60),
        floatingPanelMinWidthDp = floatingPanelMinWidthDp.coerceIn(150, 260),
        floatingPanelMinHeightDp = floatingPanelMinHeightDp.coerceIn(64, 160),
        floatingIdleTimeoutSeconds = floatingIdleTimeoutSeconds.coerceIn(0, 180),
    )

    companion object {
        fun from(settings: AppSettings): ConfigSettings = ConfigSettings(
            defaultQualityHeight = settings.defaultQualityHeight,
            preferH264 = settings.preferH264,
            includeLivePhotos = settings.includeLivePhotos,
            includeMusic = settings.includeMusic,
            createZip = settings.createZip,
            clipboardDetection = settings.clipboardDetection,
            themeMode = settings.themeMode.name,
            dynamicColor = settings.dynamicColor,
            historyRetentionDays = settings.historyRetentionDays,
            downloadsGridView = settings.downloadsGridView,
            downloadsSortMode = settings.downloadsSortMode.name,
            onboardingAccepted = settings.onboardingAccepted,
            floatingBallEnabled = settings.floatingBallEnabled,
            floatingMode = settings.floatingMode.name,
            floatingAutoDownload = settings.floatingAutoDownload,
            floatingOrbSizeDp = settings.floatingOrbSizeDp,
            floatingOrbAlpha = settings.floatingOrbAlpha,
            floatingPanelOpacity = settings.floatingPanelOpacity,
            floatingPanelStyle = settings.floatingPanelStyle.name,
            floatingPanelColor = settings.floatingPanelColor.name,
            floatingPanelBlur = settings.floatingPanelBlur,
            floatingShowClipboard = settings.floatingShowClipboard,
            floatingActionDistanceDp = settings.floatingActionDistanceDp,
            floatingActionAngleOffset = settings.floatingActionAngleOffset,
            floatingPanelMinWidthDp = settings.floatingPanelMinWidthDp,
            floatingPanelMinHeightDp = settings.floatingPanelMinHeightDp,
            floatingIdleTimeoutSeconds = settings.floatingIdleTimeoutSeconds,
        )
    }
}

@Serializable
data class HistoryManifest(
    val format: String = "douyinkit-history",
    val schemaVersion: Int = 1,
    val appVersionCode: Int = 0,
    val appVersionName: String = "",
    val exportedAt: Long = 0,
    val historyCount: Int = 0,
    val downloadCount: Int = 0,
)

@Serializable
data class AwemeBackupRecord(
    val awemeId: String,
    val title: String = "",
    val authorName: String = "",
    val desc: String = "",
    val kind: String = "VIDEO",
    val coverUrl: String? = null,
    val sourceUrl: String? = null,
    val snapshotJson: String,
    val isFavorite: Boolean = false,
    val tags: String = "",
    val parsedAt: Long,
    val createTimeSeconds: Long? = null,
)

@Serializable
data class DownloadBackupRecord(
    val taskId: String,
    val awemeId: String,
    val title: String = "",
    val kind: String = "VIDEO",
    val status: String = "COMPLETED",
    val message: String? = null,
    val requestJson: String,
    val filesJson: String,
    val zipJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
