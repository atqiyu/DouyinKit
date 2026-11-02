package com.atqiyu.douyinkit.backup

import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.data.prefs.DownloadSortMode
import com.atqiyu.douyinkit.data.prefs.FloatingMode
import com.atqiyu.douyinkit.data.prefs.FloatingPanelColor
import com.atqiyu.douyinkit.data.prefs.FloatingPanelStyle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupModelsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun configRoundTripKeepsAllCurrentSettings() {
        val settings = AppSettings(
            defaultQualityHeight = 2160,
            preferH264 = false,
            includeLivePhotos = false,
            includeMusic = false,
            createZip = true,
            clipboardDetection = false,
            themeMode = com.atqiyu.douyinkit.data.prefs.ThemeMode.DARK,
            dynamicColor = true,
            historyRetentionDays = 30,
            downloadsGridView = false,
            downloadsSortMode = DownloadSortMode.AUTHOR,
            onboardingAccepted = true,
            floatingBallEnabled = true,
            floatingMode = FloatingMode.AUTO,
            floatingAutoDownload = false,
            floatingOrbSizeDp = 60,
            floatingOrbAlpha = 0.66f,
            floatingPanelOpacity = 0.42f,
            floatingPanelStyle = FloatingPanelStyle.TRANSPARENT,
            floatingPanelColor = FloatingPanelColor.PLUM,
            floatingPanelBlur = 8,
            floatingShowClipboard = false,
            floatingActionDistanceDp = 14,
            floatingActionAngleOffset = 20,
            floatingPanelMinWidthDp = 180,
            floatingPanelMinHeightDp = 80,
            floatingIdleTimeoutSeconds = 60,
        )
        val encoded = json.encodeToString(
            ConfigFile.serializer(),
            ConfigFile(settings = ConfigSettings.from(settings)),
        )
        val decoded = json.decodeFromString(ConfigFile.serializer(), encoded)
        assertEquals(settings, decoded.settings.toSettings())
    }

    @Test
    fun missingOptionalFieldsUseCompatibleDefaults() {
        val decoded = json.decodeFromString(
            ConfigFile.serializer(),
            """{"schemaVersion":1,"settings":{"defaultQualityHeight":720}}""",
        )
        val settings = decoded.settings.toSettings()
        assertEquals(720, settings.defaultQualityHeight)
        assertEquals(90, settings.historyRetentionDays)
        assertEquals(FloatingMode.STANDARD, settings.floatingMode)
        assertEquals(FloatingPanelStyle.GLASS, settings.floatingPanelStyle)
        assertTrue(settings.floatingShowClipboard)
    }

    @Test
    fun invalidEnumValuesFallBackWithoutCrashing() {
        val decoded = json.decodeFromString(
            ConfigFile.serializer(),
            """{"schemaVersion":1,"settings":{"themeMode":"FUTURE","floatingPanelColor":"NEON"}}""",
        )
        val settings = decoded.settings.toSettings()
        assertEquals(com.atqiyu.douyinkit.data.prefs.ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(FloatingPanelColor.DARK, settings.floatingPanelColor)
    }
}
