package com.atqiyu.douyinkit.di

import android.content.Context
import com.atqiyu.douyinkit.DouyinKitApp
import com.atqiyu.douyinkit.core.net.HttpClients
import com.atqiyu.douyinkit.data.DownloadHistoryRepository
import com.atqiyu.douyinkit.data.HistoryRepository
import com.atqiyu.douyinkit.data.db.AppDatabase
import com.atqiyu.douyinkit.data.prefs.SettingsRepository
import com.atqiyu.douyinkit.parser.DouyinDetailNormalizer
import com.atqiyu.douyinkit.parser.ShortLinkResolver

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.build(appContext)
    val historyRepository: HistoryRepository = HistoryRepository(database.awemeDao())
    val downloadHistoryRepository: DownloadHistoryRepository =
        DownloadHistoryRepository(database.downloadHistoryDao())
    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    fun newShortLinkResolver(): ShortLinkResolver = ShortLinkResolver(HttpClients.shortLink())

    fun newNormalizer(): DouyinDetailNormalizer = DouyinDetailNormalizer()
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DouyinKitApp).container
