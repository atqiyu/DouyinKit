package com.atqiyu.douyinkit.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class FloatingMode { STANDARD, AUTO }

enum class FloatingPanelStyle { GLASS, CLEAR, TRANSPARENT, SOLID }

/** How the 连播 queue treats a link that the floating assistant captured. */
enum class FloatingQueueMode {
    PARSE_AND_DOWNLOAD,
    PARSE_ONLY,
    RECORD_ONLY,
}

enum class FloatingPanelColor(val argb: Long) {
    DARK(0xFF10141C),
    BLACK(0xFF000000),
    SLATE(0xFF1C2430),
    PLUM(0xFF241A2E),
    FOREST(0xFF102019),
}

data class AppSettings(
    val defaultQualityHeight: Int = 1080,
    val preferH264: Boolean = true,
    val includeLivePhotos: Boolean = true,
    val includeMusic: Boolean = true,
    val createZip: Boolean = false,
    val clipboardDetection: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val historyRetentionDays: Int = 90,
    val downloadsGridView: Boolean = true,
    val downloadsSortMode: DownloadSortMode = DownloadSortMode.LATEST,
    val onboardingAccepted: Boolean = false,
    val floatingBallEnabled: Boolean = false,
    val floatingMode: FloatingMode = FloatingMode.STANDARD,
    val floatingQueueMode: FloatingQueueMode = FloatingQueueMode.PARSE_AND_DOWNLOAD,
    val floatingHidePanel: Boolean = false,
    val floatingOrbSizeDp: Int = 48,
    val floatingOrbAlpha: Float = 0.88f,
    val floatingPanelOpacity: Float = 0.72f,
    val floatingPanelStyle: FloatingPanelStyle = FloatingPanelStyle.GLASS,
    val floatingPanelColor: FloatingPanelColor = FloatingPanelColor.DARK,
    val floatingPanelBlur: Int = 18,
    val floatingShowClipboard: Boolean = true,
    val floatingTapShowsPanel: Boolean = true,
    val floatingActionDistanceDp: Int = 8,
    val floatingActionAngleOffset: Int = 0,
    val floatingPanelMinWidthDp: Int = 160,
    val floatingPanelMinHeightDp: Int = 64,
    val floatingIdleTimeoutSeconds: Int = 45,
    val floatingQueueIntervalSeconds: Int = 5,
    val floatingPanelPaddingLeftDp: Int = 8,
    val floatingPanelPaddingTopDp: Int = 4,
    val floatingPanelPaddingRightDp: Int = 8,
    val floatingPanelPaddingBottomDp: Int = 6,
    val floatingPanelDragHandleTopDp: Int = 2,
    val floatingOrbXFraction: Float = -1f,
    val floatingOrbYFraction: Float = -1f,
    val floatingPanelXFraction: Float = -1f,
    val floatingPanelYFraction: Float = -1f,
    val floatingPanelWidthDp: Int = 0,
    val floatingPanelHeightDp: Int = 0,
    val previewOverlayOpacity: Float = 0.86f,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "douyinkit_settings",
)

class SettingsRepository(private val context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                defaultQualityHeight = prefs[KEY_DEFAULT_QUALITY] ?: 1080,
                preferH264 = prefs[KEY_PREFER_H264] ?: true,
                includeLivePhotos = prefs[KEY_LIVE_PHOTOS] ?: true,
                includeMusic = prefs[KEY_MUSIC] ?: true,
                createZip = prefs[KEY_CREATE_ZIP] ?: false,
                clipboardDetection = prefs[KEY_CLIPBOARD] ?: true,
                themeMode = runCatching {
                    ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name)
                }.getOrDefault(ThemeMode.SYSTEM),
                dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
                historyRetentionDays = prefs[KEY_RETENTION_DAYS] ?: 90,
                downloadsGridView = prefs[KEY_DOWNLOADS_GRID] ?: true,
                downloadsSortMode = runCatching {
                    DownloadSortMode.valueOf(
                        prefs[KEY_DOWNLOADS_SORT] ?: DownloadSortMode.LATEST.name,
                    )
                }.getOrDefault(DownloadSortMode.LATEST),
                onboardingAccepted = prefs[KEY_ONBOARDING] ?: false,
                floatingBallEnabled = prefs[KEY_FLOATING_ENABLED] ?: false,
                floatingMode = runCatching {
                    FloatingMode.valueOf(
                        prefs[KEY_FLOATING_MODE] ?: FloatingMode.STANDARD.name,
                    )
                }.getOrDefault(FloatingMode.STANDARD),
                floatingQueueMode = runCatching {
                    FloatingQueueMode.valueOf(
                        prefs[KEY_FLOATING_QUEUE_MODE] ?: "",
                    )
                }.getOrNull() ?: if (prefs[KEY_FLOATING_AUTO_DOWNLOAD] == false) {
                    FloatingQueueMode.PARSE_ONLY
                } else {
                    FloatingQueueMode.PARSE_AND_DOWNLOAD
                },
                floatingHidePanel = prefs[KEY_FLOATING_HIDE_PANEL] ?: false,
                floatingOrbSizeDp = (prefs[KEY_FLOATING_ORB_SIZE] ?: 48).coerceIn(38, 72),
                floatingOrbAlpha = (prefs[KEY_FLOATING_ORB_ALPHA] ?: 0.88f).coerceIn(0.35f, 1f),
                floatingPanelOpacity = (prefs[KEY_FLOATING_PANEL_OPACITY] ?: 0.72f)
                    .coerceIn(0.2f, 1f),
                floatingPanelStyle = runCatching {
                    FloatingPanelStyle.valueOf(
                        prefs[KEY_FLOATING_PANEL_STYLE] ?: FloatingPanelStyle.GLASS.name,
                    )
                }.getOrDefault(FloatingPanelStyle.GLASS),
                floatingPanelColor = runCatching {
                    FloatingPanelColor.valueOf(
                        prefs[KEY_FLOATING_PANEL_COLOR] ?: FloatingPanelColor.DARK.name,
                    )
                }.getOrDefault(FloatingPanelColor.DARK),
                floatingPanelBlur = (prefs[KEY_FLOATING_PANEL_BLUR] ?: 18).coerceIn(0, 25),
                floatingShowClipboard = prefs[KEY_FLOATING_SHOW_CLIPBOARD] ?: true,
                floatingTapShowsPanel = prefs[KEY_FLOATING_TAP_PANEL] ?: true,
                floatingActionDistanceDp = (prefs[KEY_FLOATING_ACTION_DISTANCE] ?: 8)
                    .coerceIn(0, 24),
                floatingActionAngleOffset = (prefs[KEY_FLOATING_ACTION_ANGLE] ?: 0)
                    .coerceIn(-180, 180),
                floatingPanelMinWidthDp = (prefs[KEY_FLOATING_PANEL_MIN_WIDTH] ?: 160)
                    .coerceIn(150, 260),
                floatingPanelMinHeightDp = (prefs[KEY_FLOATING_PANEL_MIN_HEIGHT] ?: 64)
                    .coerceIn(64, 160),
                floatingIdleTimeoutSeconds = (prefs[KEY_FLOATING_IDLE_TIMEOUT] ?: 45)
                    .coerceIn(0, 180),
                floatingQueueIntervalSeconds = (prefs[KEY_FLOATING_QUEUE_INTERVAL] ?: 5)
                    .coerceIn(0, 60),
                floatingPanelPaddingLeftDp = (prefs[KEY_FLOATING_PANEL_PADDING_LEFT] ?: 8)
                    .coerceIn(0, 24),
                floatingPanelPaddingTopDp = (prefs[KEY_FLOATING_PANEL_PADDING_TOP] ?: 4)
                    .coerceIn(0, 24),
                floatingPanelPaddingRightDp = (prefs[KEY_FLOATING_PANEL_PADDING_RIGHT] ?: 8)
                    .coerceIn(0, 24),
                floatingPanelPaddingBottomDp = (prefs[KEY_FLOATING_PANEL_PADDING_BOTTOM] ?: 6)
                    .coerceIn(0, 24),
                floatingPanelDragHandleTopDp = (prefs[KEY_FLOATING_PANEL_DRAG_TOP] ?: 2)
                    .coerceIn(0, 20),
                floatingOrbXFraction = (prefs[KEY_FLOATING_ORB_X] ?: -1f).coerceIn(-1f, 1f),
                floatingOrbYFraction = (prefs[KEY_FLOATING_ORB_Y] ?: -1f).coerceIn(-1f, 1f),
                floatingPanelXFraction = (prefs[KEY_FLOATING_PANEL_X] ?: -1f)
                    .coerceIn(-1f, 1f),
                floatingPanelYFraction = (prefs[KEY_FLOATING_PANEL_Y] ?: -1f)
                    .coerceIn(-1f, 1f),
                floatingPanelWidthDp = (prefs[KEY_FLOATING_PANEL_WIDTH] ?: 0)
                    .coerceIn(0, 1000),
                floatingPanelHeightDp = (prefs[KEY_FLOATING_PANEL_HEIGHT] ?: 0)
                    .coerceIn(0, 1000),
                previewOverlayOpacity = (prefs[KEY_PREVIEW_OVERLAY_OPACITY] ?: 0.86f)
                    .coerceIn(0.5f, 1f),
            )
        }

    suspend fun setDefaultQualityHeight(height: Int) {
        dataStore.edit { it[KEY_DEFAULT_QUALITY] = height }
    }

    suspend fun setPreferH264(enabled: Boolean) {
        dataStore.edit { it[KEY_PREFER_H264] = enabled }
    }

    suspend fun setIncludeLivePhotos(enabled: Boolean) {
        dataStore.edit { it[KEY_LIVE_PHOTOS] = enabled }
    }

    suspend fun setIncludeMusic(enabled: Boolean) {
        dataStore.edit { it[KEY_MUSIC] = enabled }
    }

    suspend fun setCreateZip(enabled: Boolean) {
        dataStore.edit { it[KEY_CREATE_ZIP] = enabled }
    }

    suspend fun setClipboardDetection(enabled: Boolean) {
        dataStore.edit { it[KEY_CLIPBOARD] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setHistoryRetentionDays(days: Int) {
        dataStore.edit { it[KEY_RETENTION_DAYS] = days }
    }

    suspend fun setDownloadsGridView(enabled: Boolean) {
        dataStore.edit { it[KEY_DOWNLOADS_GRID] = enabled }
    }

    suspend fun setDownloadsSortMode(mode: DownloadSortMode) {
        dataStore.edit { it[KEY_DOWNLOADS_SORT] = mode.name }
    }

    suspend fun setOnboardingAccepted(accepted: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING] = accepted }
    }

    suspend fun setFloatingBallEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_FLOATING_ENABLED] = enabled }
    }

    suspend fun setFloatingMode(mode: FloatingMode) {
        dataStore.edit { it[KEY_FLOATING_MODE] = mode.name }
    }

    suspend fun setFloatingQueueMode(mode: FloatingQueueMode) {
        dataStore.edit {
            it[KEY_FLOATING_QUEUE_MODE] = mode.name
            // Keep the legacy boolean in sync so older config files and backups remain
            // meaningful when opened by a previous version.
            it[KEY_FLOATING_AUTO_DOWNLOAD] = mode == FloatingQueueMode.PARSE_AND_DOWNLOAD
        }
    }

    suspend fun setFloatingAutoDownload(enabled: Boolean) {
        setFloatingQueueMode(
            if (enabled) FloatingQueueMode.PARSE_AND_DOWNLOAD else FloatingQueueMode.PARSE_ONLY,
        )
    }

    suspend fun setFloatingHidePanel(hide: Boolean) {
        dataStore.edit { it[KEY_FLOATING_HIDE_PANEL] = hide }
    }

    suspend fun setFloatingOrbSize(sizeDp: Int) {
        dataStore.edit { it[KEY_FLOATING_ORB_SIZE] = sizeDp.coerceIn(38, 72) }
    }

    suspend fun setFloatingOrbAlpha(alpha: Float) {
        dataStore.edit { it[KEY_FLOATING_ORB_ALPHA] = alpha.coerceIn(0.35f, 1f) }
    }

    suspend fun setFloatingPanelOpacity(opacity: Float) {
        dataStore.edit { it[KEY_FLOATING_PANEL_OPACITY] = opacity.coerceIn(0.2f, 1f) }
    }

    suspend fun setFloatingPanelStyle(style: FloatingPanelStyle) {
        dataStore.edit { it[KEY_FLOATING_PANEL_STYLE] = style.name }
    }

    suspend fun setFloatingPanelColor(color: FloatingPanelColor) {
        dataStore.edit { it[KEY_FLOATING_PANEL_COLOR] = color.name }
    }

    suspend fun setFloatingPanelBlur(radius: Int) {
        dataStore.edit { it[KEY_FLOATING_PANEL_BLUR] = radius.coerceIn(0, 25) }
    }

    suspend fun setFloatingShowClipboard(show: Boolean) {
        dataStore.edit { it[KEY_FLOATING_SHOW_CLIPBOARD] = show }
    }

    suspend fun setFloatingTapShowsPanel(show: Boolean) {
        dataStore.edit { it[KEY_FLOATING_TAP_PANEL] = show }
    }

    suspend fun setFloatingActionDistance(distanceDp: Int) {
        dataStore.edit { it[KEY_FLOATING_ACTION_DISTANCE] = distanceDp.coerceIn(0, 24) }
    }

    suspend fun setFloatingActionAngleOffset(degrees: Int) {
        dataStore.edit { it[KEY_FLOATING_ACTION_ANGLE] = degrees.coerceIn(-180, 180) }
    }

    suspend fun setFloatingPanelMinWidth(widthDp: Int) {
        dataStore.edit { it[KEY_FLOATING_PANEL_MIN_WIDTH] = widthDp.coerceIn(150, 260) }
    }

    suspend fun setFloatingPanelMinHeight(heightDp: Int) {
        dataStore.edit { it[KEY_FLOATING_PANEL_MIN_HEIGHT] = heightDp.coerceIn(64, 160) }
    }

    suspend fun setFloatingIdleTimeout(seconds: Int) {
        dataStore.edit { it[KEY_FLOATING_IDLE_TIMEOUT] = seconds.coerceIn(0, 180) }
    }

    suspend fun setFloatingQueueInterval(seconds: Int) {
        dataStore.edit { it[KEY_FLOATING_QUEUE_INTERVAL] = seconds.coerceIn(0, 60) }
    }

    suspend fun setFloatingPanelPadding(left: Int, top: Int, right: Int, bottom: Int) {
        dataStore.edit {
            it[KEY_FLOATING_PANEL_PADDING_LEFT] = left.coerceIn(0, 24)
            it[KEY_FLOATING_PANEL_PADDING_TOP] = top.coerceIn(0, 24)
            it[KEY_FLOATING_PANEL_PADDING_RIGHT] = right.coerceIn(0, 24)
            it[KEY_FLOATING_PANEL_PADDING_BOTTOM] = bottom.coerceIn(0, 24)
        }
    }

    suspend fun setFloatingPanelDragHandleTop(topDp: Int) {
        dataStore.edit { it[KEY_FLOATING_PANEL_DRAG_TOP] = topDp.coerceIn(0, 20) }
    }

    suspend fun setFloatingOrbPosition(xFraction: Float, yFraction: Float) {
        dataStore.edit {
            it[KEY_FLOATING_ORB_X] = xFraction.coerceIn(0f, 1f)
            it[KEY_FLOATING_ORB_Y] = yFraction.coerceIn(0f, 1f)
        }
    }

    suspend fun setFloatingPanelGeometry(
        xFraction: Float,
        yFraction: Float,
        widthDp: Int,
        heightDp: Int,
    ) {
        dataStore.edit {
            it[KEY_FLOATING_PANEL_X] = xFraction.coerceIn(0f, 1f)
            it[KEY_FLOATING_PANEL_Y] = yFraction.coerceIn(0f, 1f)
            it[KEY_FLOATING_PANEL_WIDTH] = widthDp.coerceIn(0, 1000)
            it[KEY_FLOATING_PANEL_HEIGHT] = heightDp.coerceIn(0, 1000)
        }
    }

    suspend fun resetFloatingGeometry() {
        dataStore.edit {
            it[KEY_FLOATING_ORB_X] = -1f
            it[KEY_FLOATING_ORB_Y] = -1f
            it[KEY_FLOATING_PANEL_X] = -1f
            it[KEY_FLOATING_PANEL_Y] = -1f
            it[KEY_FLOATING_PANEL_WIDTH] = 0
            it[KEY_FLOATING_PANEL_HEIGHT] = 0
        }
    }

    suspend fun setPreviewOverlayOpacity(opacity: Float) {
        dataStore.edit { it[KEY_PREVIEW_OVERLAY_OPACITY] = opacity.coerceIn(0.5f, 1f) }
    }

    suspend fun replaceAll(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_QUALITY] = settings.defaultQualityHeight
            prefs[KEY_PREFER_H264] = settings.preferH264
            prefs[KEY_LIVE_PHOTOS] = settings.includeLivePhotos
            prefs[KEY_MUSIC] = settings.includeMusic
            prefs[KEY_CREATE_ZIP] = settings.createZip
            prefs[KEY_CLIPBOARD] = settings.clipboardDetection
            prefs[KEY_THEME] = settings.themeMode.name
            prefs[KEY_DYNAMIC_COLOR] = settings.dynamicColor
            prefs[KEY_RETENTION_DAYS] = settings.historyRetentionDays
            prefs[KEY_DOWNLOADS_GRID] = settings.downloadsGridView
            prefs[KEY_DOWNLOADS_SORT] = settings.downloadsSortMode.name
            prefs[KEY_ONBOARDING] = settings.onboardingAccepted
            prefs[KEY_FLOATING_ENABLED] = settings.floatingBallEnabled
            prefs[KEY_FLOATING_MODE] = settings.floatingMode.name
            prefs[KEY_FLOATING_QUEUE_MODE] = settings.floatingQueueMode.name
            prefs[KEY_FLOATING_AUTO_DOWNLOAD] =
                settings.floatingQueueMode == FloatingQueueMode.PARSE_AND_DOWNLOAD
            prefs[KEY_FLOATING_HIDE_PANEL] = settings.floatingHidePanel
            prefs[KEY_FLOATING_ORB_SIZE] = settings.floatingOrbSizeDp
            prefs[KEY_FLOATING_ORB_ALPHA] = settings.floatingOrbAlpha
            prefs[KEY_FLOATING_PANEL_OPACITY] = settings.floatingPanelOpacity
            prefs[KEY_FLOATING_PANEL_STYLE] = settings.floatingPanelStyle.name
            prefs[KEY_FLOATING_PANEL_COLOR] = settings.floatingPanelColor.name
            prefs[KEY_FLOATING_PANEL_BLUR] = settings.floatingPanelBlur
            prefs[KEY_FLOATING_SHOW_CLIPBOARD] = settings.floatingShowClipboard
            prefs[KEY_FLOATING_TAP_PANEL] = settings.floatingTapShowsPanel
            prefs[KEY_FLOATING_ACTION_DISTANCE] = settings.floatingActionDistanceDp
            prefs[KEY_FLOATING_ACTION_ANGLE] = settings.floatingActionAngleOffset
            prefs[KEY_FLOATING_PANEL_MIN_WIDTH] = settings.floatingPanelMinWidthDp
            prefs[KEY_FLOATING_PANEL_MIN_HEIGHT] = settings.floatingPanelMinHeightDp
            prefs[KEY_FLOATING_IDLE_TIMEOUT] = settings.floatingIdleTimeoutSeconds
            prefs[KEY_FLOATING_QUEUE_INTERVAL] = settings.floatingQueueIntervalSeconds
            prefs[KEY_FLOATING_PANEL_PADDING_LEFT] = settings.floatingPanelPaddingLeftDp
            prefs[KEY_FLOATING_PANEL_PADDING_TOP] = settings.floatingPanelPaddingTopDp
            prefs[KEY_FLOATING_PANEL_PADDING_RIGHT] = settings.floatingPanelPaddingRightDp
            prefs[KEY_FLOATING_PANEL_PADDING_BOTTOM] = settings.floatingPanelPaddingBottomDp
            prefs[KEY_FLOATING_PANEL_DRAG_TOP] = settings.floatingPanelDragHandleTopDp
            prefs[KEY_FLOATING_ORB_X] = settings.floatingOrbXFraction
            prefs[KEY_FLOATING_ORB_Y] = settings.floatingOrbYFraction
            prefs[KEY_FLOATING_PANEL_X] = settings.floatingPanelXFraction
            prefs[KEY_FLOATING_PANEL_Y] = settings.floatingPanelYFraction
            prefs[KEY_FLOATING_PANEL_WIDTH] = settings.floatingPanelWidthDp
            prefs[KEY_FLOATING_PANEL_HEIGHT] = settings.floatingPanelHeightDp
            prefs[KEY_PREVIEW_OVERLAY_OPACITY] = settings.previewOverlayOpacity
        }
    }

    private companion object {
        val KEY_DEFAULT_QUALITY = intPreferencesKey("default_quality_height")
        val KEY_PREFER_H264 = booleanPreferencesKey("prefer_h264")
        val KEY_LIVE_PHOTOS = booleanPreferencesKey("include_live_photos")
        val KEY_MUSIC = booleanPreferencesKey("include_music")
        val KEY_CREATE_ZIP = booleanPreferencesKey("create_zip")
        val KEY_CLIPBOARD = booleanPreferencesKey("clipboard_detection")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_RETENTION_DAYS = intPreferencesKey("history_retention_days")
        val KEY_DOWNLOADS_GRID = booleanPreferencesKey("downloads_grid_view")
        val KEY_DOWNLOADS_SORT = stringPreferencesKey("downloads_sort_mode")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_accepted")
        val KEY_FLOATING_ENABLED = booleanPreferencesKey("floating_ball_enabled")
        val KEY_FLOATING_MODE = stringPreferencesKey("floating_mode")
        val KEY_FLOATING_AUTO_DOWNLOAD = booleanPreferencesKey("floating_auto_download")
        val KEY_FLOATING_ORB_SIZE = intPreferencesKey("floating_orb_size")
        val KEY_FLOATING_ORB_ALPHA = floatPreferencesKey("floating_orb_alpha")
        val KEY_FLOATING_PANEL_OPACITY = floatPreferencesKey("floating_panel_opacity")
        val KEY_FLOATING_PANEL_STYLE = stringPreferencesKey("floating_panel_style")
        val KEY_FLOATING_PANEL_COLOR = stringPreferencesKey("floating_panel_color")
        val KEY_FLOATING_PANEL_BLUR = intPreferencesKey("floating_panel_blur")
        val KEY_FLOATING_SHOW_CLIPBOARD = booleanPreferencesKey("floating_show_clipboard")
        val KEY_FLOATING_TAP_PANEL = booleanPreferencesKey("floating_tap_shows_panel")
        val KEY_FLOATING_ACTION_DISTANCE = intPreferencesKey("floating_action_distance")
        val KEY_FLOATING_ACTION_ANGLE = intPreferencesKey("floating_action_angle")
        val KEY_FLOATING_PANEL_MIN_WIDTH = intPreferencesKey("floating_panel_min_width")
        val KEY_FLOATING_PANEL_MIN_HEIGHT = intPreferencesKey("floating_panel_min_height")
        val KEY_FLOATING_IDLE_TIMEOUT = intPreferencesKey("floating_idle_timeout")
        val KEY_FLOATING_QUEUE_INTERVAL = intPreferencesKey("floating_queue_interval")
        val KEY_FLOATING_QUEUE_MODE = stringPreferencesKey("floating_queue_mode")
        val KEY_FLOATING_HIDE_PANEL = booleanPreferencesKey("floating_hide_panel")
        val KEY_FLOATING_PANEL_PADDING_LEFT = intPreferencesKey("floating_panel_padding_left")
        val KEY_FLOATING_PANEL_PADDING_TOP = intPreferencesKey("floating_panel_padding_top")
        val KEY_FLOATING_PANEL_PADDING_RIGHT = intPreferencesKey("floating_panel_padding_right")
        val KEY_FLOATING_PANEL_PADDING_BOTTOM = intPreferencesKey("floating_panel_padding_bottom")
        val KEY_FLOATING_PANEL_DRAG_TOP = intPreferencesKey("floating_panel_drag_top")
        val KEY_FLOATING_ORB_X = floatPreferencesKey("floating_orb_x_fraction")
        val KEY_FLOATING_ORB_Y = floatPreferencesKey("floating_orb_y_fraction")
        val KEY_FLOATING_PANEL_X = floatPreferencesKey("floating_panel_x_fraction")
        val KEY_FLOATING_PANEL_Y = floatPreferencesKey("floating_panel_y_fraction")
        val KEY_FLOATING_PANEL_WIDTH = intPreferencesKey("floating_panel_width_dp")
        val KEY_FLOATING_PANEL_HEIGHT = intPreferencesKey("floating_panel_height_dp")
        val KEY_PREVIEW_OVERLAY_OPACITY = floatPreferencesKey("preview_overlay_opacity")
    }
}
