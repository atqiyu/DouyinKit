package com.atqiyu.douyinkit.download

import java.io.File
import java.io.FileInputStream

object MimeSniffer {
    fun imageExtension(file: File): String? {
        val head = readHead(file, 16) ?: return null
        val ascii = { start: Int, end: Int -> String(head, start, end - start, Charsets.ISO_8859_1) }
        return when {
            ascii(0, 4) == "RIFF" && ascii(8, 12) == "WEBP" -> "webp"
            head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte() -> "jpg"
            head.size >= 8 &&
                head[0] == 0x89.toByte() && head[1] == 0x50.toByte() &&
                head[2] == 0x4E.toByte() && head[3] == 0x47.toByte() -> "png"
            ascii(0, 4) == "GIF8" -> "gif"
            ascii(0, 2) == "BM" -> "bmp"
            head.size >= 12 && ascii(4, 8) == "ftyp" -> {
                when (ascii(8, 12).trim()) {
                    "avif", "avis" -> "avif"
                    "heic", "heix", "hevc", "hevx", "mif1", "msf1" -> "heic"
                    else -> null
                }
            }
            else -> null
        }
    }

    fun imageMime(extension: String): String = when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "avif" -> "image/avif"
        "heic" -> "image/heic"
        "bmp" -> "image/bmp"
        else -> "image/webp"
    }

    fun audioExtension(file: File): String? {
        val head = readHead(file, 16) ?: return null
        val ascii = { start: Int, end: Int -> String(head, start, end - start, Charsets.ISO_8859_1) }
        return when {
            ascii(0, 3) == "ID3" -> "mp3"
            head.size >= 2 &&
                head[0] == 0xFF.toByte() &&
                (head[1].toInt() and 0xE0) == 0xE0 -> "mp3"
            head.size >= 12 && ascii(4, 8) == "ftyp" -> "m4a"
            head.size >= 2 &&
                head[0] == 0xFF.toByte() &&
                (head[1].toInt() and 0xF0) == 0xF0 -> "aac"
            ascii(0, 4) == "RIFF" && ascii(8, 12) == "WAVE" -> "wav"
            else -> null
        }
    }

    fun audioMime(extension: String): String = when (extension.lowercase()) {
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        else -> "audio/mpeg"
    }

    fun isMp4(file: File): Boolean {
        val head = readHead(file, 12) ?: return false
        if (head.size < 8) return false
        return String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp"
    }

    fun isZip(file: File): Boolean {
        val head = readHead(file, 4) ?: return false
        return head.size >= 4 &&
            head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
    }

    private fun readHead(file: File, count: Int): ByteArray? {
        if (!file.exists() || file.length() <= 0) return null
        return runCatching {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(count)
                val read = input.read(buffer)
                if (read <= 0) null else buffer.copyOf(read)
            }
        }.getOrNull()
    }
}
