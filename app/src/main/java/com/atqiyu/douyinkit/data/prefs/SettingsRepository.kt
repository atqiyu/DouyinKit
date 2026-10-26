package com.atqiyu.douyinkit.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

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
    }
}
