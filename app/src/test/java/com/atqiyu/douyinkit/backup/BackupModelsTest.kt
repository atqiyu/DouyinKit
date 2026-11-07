package com.atqiyu.douyinkit.backup

import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.data.prefs.DownloadSortMode
import com.atqiyu.douyinkit.data.prefs.FloatingMode
import com.atqiyu.douyinkit.data.prefs.FloatingPanelColor
import com.atqiyu.douyinkit.data.prefs.FloatingPanelStyle
import com.atqiyu.douyinkit.data.prefs.FloatingQueueMode
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
            floatingQueueMode = FloatingQueueMode.RECORD_ONLY,
            floatingHidePanel = true,
            floatingOrbSizeDp = 60,
            floatingOrbAlpha = 0.66f,
            floatingPanelOpacity = 0.42f,
            floatingPanelStyle = FloatingPanelStyle.TRANSPARENT,
            floatingPanelColor = FloatingPanelColor.PLUM,
            floatingPanelBlur = 8,
            floatingShowClipboard = false,
            floatingTapShowsPanel = false,
            floatingActionDistanceDp = 14,
            floatingActionAngleOffset = 170,
            floatingPanelMinWidthDp = 180,
            floatingPanelMinHeightDp = 80,
            floatingIdleTimeoutSeconds = 60,
            floatingQueueIntervalSeconds = 23,
            floatingPanelPaddingLeftDp = 2,
            floatingPanelPaddingTopDp = 0,
            floatingPanelPaddingRightDp = 11,
            floatingPanelPaddingBottomDp = 5,
            floatingPanelDragHandleTopDp = 0,
            floatingOrbXFraction = 0.2f,
            floatingOrbYFraction = 0.8f,
            floatingPanelXFraction = 0.1f,
            floatingPanelYFraction = 0.4f,
            floatingPanelWidthDp = 300,
            floatingPanelHeightDp = 140,
            previewOverlayOpacity = 0.72f,
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
        assertTrue(settings.floatingTapShowsPanel)
        assertEquals(0, settings.floatingActionAngleOffset)
        assertEquals(5, settings.floatingQueueIntervalSeconds)
        assertEquals(FloatingQueueMode.PARSE_AND_DOWNLOAD, settings.floatingQueueMode)
        assertEquals(false, settings.floatingHidePanel)
        assertEquals(8, settings.floatingPanelPaddingLeftDp)
        assertEquals(4, settings.floatingPanelPaddingTopDp)
        assertEquals(2, settings.floatingPanelDragHandleTopDp)
        assertEquals(0.86f, settings.previewOverlayOpacity)
    }

    @Test
    fun legacyAutoDownloadBooleanMapsToQueueMode() {
        val disabled = json.decodeFromString(
            ConfigFile.serializer(),
            """{"schemaVersion":1,"settings":{"floatingAutoDownload":false}}""",
        )
        assertEquals(
            FloatingQueueMode.PARSE_ONLY,
            disabled.settings.toSettings().floatingQueueMode,
        )
        val enabled = json.decodeFromString(
            ConfigFile.serializer(),
            """{"schemaVersion":1,"settings":{"floatingAutoDownload":true}}""",
        )
        assertEquals(
            FloatingQueueMode.PARSE_AND_DOWNLOAD,
            enabled.settings.toSettings().floatingQueueMode,
        )
    }

    @Test
    fun queueIntervalIsClamped() {
        val high = json.decodeFromString(
            ConfigFile.serializer(),
            """{"schemaVersion":1,"settings":{"floatingQueueIntervalSeconds":120}}""",
        )
        assertEquals(60, high.settings.toSettings().floatingQueueIntervalSeconds)

        val low = json.decodeFromString(
            ConfigFile.serializer(),
            """{"schemaVersion":1,"settings":{"floatingQueueIntervalSeconds":-3}}""",
        )
        assertEquals(0, low.settings.toSettings().floatingQueueIntervalSeconds)
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
