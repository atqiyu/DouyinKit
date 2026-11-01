package com.atqiyu.douyinkit.floating

import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.data.prefs.AppSettings
import com.atqiyu.douyinkit.download.DownloadKind
import com.atqiyu.douyinkit.download.DownloadRequest
import com.atqiyu.douyinkit.download.VideoGearSelector

/**
 * Turns the existing download defaults into one request for 连播模式. Video downloads the
 * selected default quality; image posts follow the saved LivePhoto/BGM/ZIP switches.
 */
fun buildFloatingDownloadRequest(
    aweme: Aweme,
    settings: AppSettings,
): DownloadRequest? = when (aweme.kind) {
    AwemeKind.VIDEO -> {
        val gear = VideoGearSelector.select(
            aweme = aweme,
            preferH264 = settings.preferH264,
            preferredHeight = settings.defaultQualityHeight,
        )
        gear?.let {
            DownloadRequest(
                aweme = aweme,
                kind = DownloadKind.VIDEO,
                gear = it,
            )
        }
    }

    AwemeKind.IMAGES -> {
        if (aweme.images.isEmpty() && aweme.music == null) {
            null
        } else {
            DownloadRequest(
                aweme = aweme,
                kind = DownloadKind.IMAGES,
                includeLivePhotos = settings.includeLivePhotos,
                includeMusic = settings.includeMusic && aweme.music != null,
                createZip = settings.createZip,
            )
        }
    }
}
