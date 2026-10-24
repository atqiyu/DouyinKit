package com.atqiyu.douyinkit

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.atqiyu.douyinkit.core.net.DouyinUserAgent
import com.atqiyu.douyinkit.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class DouyinKitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.downloadHistoryRepository.markInterrupted() }
        }

        val imageClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host.lowercase()
                val builder = request.newBuilder()
                if (host.endsWith("douyinpic.com") ||
                    host.endsWith("douyinvod.com") ||
                    host.endsWith("douyinstatic.com") ||
                    host.endsWith("douyin.com")
                ) {
                    builder
                        .header("Referer", DouyinUserAgent.REFERER)
                        .header("User-Agent", DouyinUserAgent.DESKTOP_FALLBACK)
                }
                chain.proceed(builder.build())
            }
            .build()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
                }
                .build()
        }
    }
}
