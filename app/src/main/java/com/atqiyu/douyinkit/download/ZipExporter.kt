package com.atqiyu.douyinkit.download

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ZipEntrySource(val file: File, val entryName: String)

class ZipExporter {

    fun zip(
        sources: List<ZipEntrySource>,
        outputFile: File,
        infoJson: String? = null,
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): File {
        if (sources.isEmpty() && infoJson == null) {
            throw DownloadException(DownloadFailure.NO_SOURCE, "没有可打包的文件")
        }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val infoBytes = infoJson?.toByteArray(Charsets.UTF_8)
        val total = sources.sumOf { it.file.length() } + (infoBytes?.size?.toLong() ?: 0L)
        var written = 0L
        val usedNames = mutableSetOf<String>()

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zip ->
                if (infoBytes != null) {
                    val name = uniqueEntryName("info.json", usedNames)
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(infoBytes)
                    zip.closeEntry()
                    written += infoBytes.size
                    onProgress(written, total)
                }

                sources.forEach { source ->
                    if (!source.file.exists()) return@forEach
                    val name = uniqueEntryName(sanitizeEntryName(source.entryName), usedNames)
                    zip.putNextEntry(ZipEntry(name))
                    source.file.inputStream().use { input ->
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            zip.write(buffer, 0, read)
                            written += read
                            onProgress(written, total)
                        }
                    }
                    zip.closeEntry()
                }
                zip.finish()
            }
        } catch (error: Exception) {
            outputFile.delete()
            throw DownloadException(DownloadFailure.STORAGE, "打包失败：${error.message}", cause = error)
        }

        if (!outputFile.exists() || outputFile.length() <= 0) {
            throw DownloadException(DownloadFailure.EMPTY, "打包结果为空")
        }
        return outputFile
    }

    private fun sanitizeEntryName(name: String): String {
        val cleaned = name.replace('\\', '/').trim('/')
        val parts = cleaned.split('/').filter { it.isNotBlank() && it != "." && it != ".." }
        val result = parts.joinToString("/")
        return result.ifBlank { "file" }
    }

    private fun uniqueEntryName(name: String, used: MutableSet<String>): String {
        var candidate = name
        var index = 1
        while (!used.add(candidate)) {
            val dot = candidate.lastIndexOf('.')
            candidate = if (dot > 0) {
                "${candidate.substring(0, dot)}_$index${candidate.substring(dot)}"
            } else {
                "${candidate}_$index"
            }
            index += 1
        }
        return candidate
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 64 * 1024
    }
}
