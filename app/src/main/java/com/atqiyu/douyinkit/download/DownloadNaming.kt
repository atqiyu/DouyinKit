package com.atqiyu.douyinkit.download

import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.VideoGear
import com.atqiyu.douyinkit.core.util.sanitizeFileName
import java.util.Locale

object DownloadNaming {
    fun safeTitle(aweme: Aweme): String {
        return sanitizeFileName(aweme.displayTitle, "douyin")
            .take(40)
            .trim()
            .ifBlank { "douyin" }
    }

    fun imageFolder(aweme: Aweme): String = "${safeTitle(aweme)}_${aweme.awemeId}"

    fun videoDisplayName(aweme: Aweme, gear: VideoGear): String {
        val codec = gear.codecLabel.replace(".", "")
        return "${safeTitle(aweme)}_${gear.resolutionLabel}_${codec}_${aweme.awemeId}.mp4"
    }

    fun imageDisplayName(index: Int, extension: String): String {
        return String.format(Locale.US, "%02d.%s", index + 1, extension.lowercase())
    }

    fun liveDisplayName(index: Int): String {
        return String.format(Locale.US, "%02d_live.mp4", index + 1)
    }

    fun musicDisplayName(aweme: Aweme, extension: String): String {
        return "${safeTitle(aweme)}_bgm_${aweme.awemeId}.${extension.lowercase()}"
    }

    fun zipDisplayName(aweme: Aweme): String = "${safeTitle(aweme)}_${aweme.awemeId}.zip"

    fun coverDisplayName(aweme: Aweme, extension: String): String {
        return "${safeTitle(aweme)}_cover_${aweme.awemeId}.${extension.lowercase()}"
    }
}
