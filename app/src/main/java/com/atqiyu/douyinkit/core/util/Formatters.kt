package com.atqiyu.douyinkit.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return "未知大小"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1024 -> String.format(Locale.CHINA, "%.2f GB", mb / 1024.0)
        mb >= 1 -> String.format(Locale.CHINA, "%.1f MB", mb)
        else -> String.format(Locale.CHINA, "%.0f KB", kb)
    }
}

fun formatCount(value: Long?): String {
    if (value == null) return "-"
    return when {
        value >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", value / 100_000_000.0)
        value >= 10_000 -> String.format(Locale.CHINA, "%.1f万", value / 10_000.0)
        else -> value.toString()
    }
}

fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.CHINA, "%d:%02d", minutes, seconds)
    }
}

fun formatCreateTime(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return ""
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(seconds * 1000))
    }.getOrDefault("")
}

fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return ""
    val mb = bytesPerSecond / (1024.0 * 1024.0)
    return if (mb >= 1) {
        String.format(Locale.CHINA, "%.1f MB/s", mb)
    } else {
        "${bytesPerSecond / 1024} KB/s"
    }
}

fun formatElapsedMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"
}

fun sanitizeFileName(input: String, fallback: String = "douyin"): String {
    val withoutIllegal = input
        .replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trim('.', ' ')
    val limited = withoutIllegal.take(60).trim()
    val resolved = limited.ifBlank { fallback }
    val reserved = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )
    return if (resolved.uppercase(Locale.US) in reserved) "${resolved}_file" else resolved
}
