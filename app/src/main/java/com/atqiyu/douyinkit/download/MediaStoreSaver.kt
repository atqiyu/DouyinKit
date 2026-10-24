package com.atqiyu.douyinkit.download

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class MediaStoreSaver(private val context: Context) {

    fun saveVideo(
        source: File,
        displayName: String,
        folder: String = "DouyinKit",
    ): SavedFile = save(
        source = source,
        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        legacyBaseDir = Environment.DIRECTORY_MOVIES,
        relativeFolder = folder,
        mimeType = "video/mp4",
        displayName = displayName,
    )

    fun saveLivePhotoVideo(
        source: File,
        displayName: String,
        folder: String,
    ): SavedFile = save(
        source = source,
        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        legacyBaseDir = Environment.DIRECTORY_PICTURES,
        relativeFolder = folder,
        mimeType = "video/mp4",
        displayName = displayName,
    )

    fun saveImage(
        source: File,
        displayName: String,
        mimeType: String,
        folder: String = "DouyinKit",
    ): SavedFile = save(
        source = source,
        collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        legacyBaseDir = Environment.DIRECTORY_PICTURES,
        relativeFolder = folder,
        mimeType = mimeType,
        displayName = displayName,
    )

    fun saveAudio(
        source: File,
        displayName: String,
        mimeType: String,
        folder: String = "DouyinKit",
    ): SavedFile = save(
        source = source,
        collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        legacyBaseDir = Environment.DIRECTORY_MUSIC,
        relativeFolder = folder,
        mimeType = mimeType,
        displayName = displayName,
    )

    fun saveDownload(
        source: File,
        displayName: String,
        folder: String = "DouyinKit",
    ): SavedFile {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveDownloadScoped(source, displayName, folder)
        }
        return saveLegacyDownload(source, displayName, folder)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveDownloadScoped(source: File, displayName: String, folder: String): SavedFile {
        return save(
            source = source,
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            legacyBaseDir = Environment.DIRECTORY_DOWNLOADS,
            relativeFolder = folder,
            mimeType = "application/zip",
            displayName = displayName,
        )
    }

    private fun saveLegacyDownload(source: File, displayName: String, folder: String): SavedFile {
        if (!source.exists() || source.length() <= 0) {
            throw DownloadException(DownloadFailure.EMPTY, "下载文件为空")
        }
        return saveLegacy(
            source = source,
            baseDir = Environment.DIRECTORY_DOWNLOADS,
            relativeFolder = folder,
            mimeType = "application/zip",
            displayName = displayName,
        )
    }

    private fun save(
        source: File,
        collection: Uri,
        legacyBaseDir: String,
        relativeFolder: String,
        mimeType: String,
        displayName: String,
    ): SavedFile {
        if (!source.exists() || source.length() <= 0) {
            throw DownloadException(DownloadFailure.EMPTY, "下载文件为空")
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(source, collection, legacyBaseDir, relativeFolder, mimeType, displayName)
        } else {
            saveLegacy(source, legacyBaseDir, relativeFolder, mimeType, displayName)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(
        source: File,
        collection: Uri,
        baseDir: String,
        relativeFolder: String,
        mimeType: String,
        displayName: String,
    ): SavedFile {
        val relative = buildRelativePath(baseDir, relativeFolder)
        val uniqueName = uniqueScopedName(collection, relative, displayName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw DownloadException(DownloadFailure.STORAGE, "无法在相册中创建文件")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input ->
                    input.copyTo(output, COPY_BUFFER_SIZE)
                }
            } ?: throw DownloadException(DownloadFailure.STORAGE, "无法写入相册")
            val done = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
            return SavedFile(
                uri = uri,
                path = null,
                displayName = uniqueName,
                mimeType = mimeType,
                bytes = source.length(),
            )
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            if (error is DownloadException) throw error
            throw DownloadException(DownloadFailure.STORAGE, "保存到相册失败：${error.message}", cause = error)
        }
    }

    private fun saveLegacy(
        source: File,
        baseDir: String,
        relativeFolder: String,
        mimeType: String,
        displayName: String,
    ): SavedFile {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw DownloadException(DownloadFailure.PERMISSION, "需要存储权限才能保存文件")
        }

        val base = Environment.getExternalStoragePublicDirectory(baseDir)
        val dir = if (relativeFolder.isBlank()) base else File(base, relativeFolder)
        if (!dir.exists() && !dir.mkdirs()) {
            throw DownloadException(DownloadFailure.STORAGE, "无法创建保存目录")
        }
        val target = uniqueFile(dir, displayName)
        try {
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, COPY_BUFFER_SIZE)
                }
            }
        } catch (error: Exception) {
            target.delete()
            throw DownloadException(DownloadFailure.STORAGE, "保存文件失败：${error.message}", cause = error)
        }

        runCatching {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(target.absolutePath),
                arrayOf(mimeType),
                null,
            )
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
        )
        return SavedFile(
            uri = uri,
            path = target.absolutePath,
            displayName = target.name,
            mimeType = mimeType,
            bytes = target.length(),
        )
    }

    private fun buildRelativePath(baseDir: String, folder: String): String {
        val clean = folder.trim('/').trim()
        return if (clean.isBlank()) baseDir else "$baseDir/$clean"
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun uniqueScopedName(collection: Uri, relative: String, displayName: String): String {
        var candidate = displayName
        var index = 1
        while (scopedNameExists(collection, relative, candidate) && index < 100) {
            candidate = appendSuffix(displayName, index)
            index += 1
        }
        return candidate
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun scopedNameExists(collection: Uri, relative: String, displayName: String): Boolean {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val relativeVariants = arrayOf(relative, "$relative/")
        var exists = false
        for (variant in relativeVariants) {
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            resolver.query(collection, projection, selection, arrayOf(variant, displayName), null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) exists = true
                }
            if (exists) break
        }
        return exists
    }

    private fun uniqueFile(dir: File, displayName: String): File {
        var candidate = File(dir, displayName)
        var index = 1
        while (candidate.exists() && index < 100) {
            candidate = File(dir, appendSuffix(displayName, index))
            index += 1
        }
        return candidate
    }

    private fun appendSuffix(displayName: String, index: Int): String {
        val dot = displayName.lastIndexOf('.')
        return if (dot > 0) {
            "${displayName.substring(0, dot)}_$index${displayName.substring(dot)}"
        } else {
            "${displayName}_$index"
        }
    }

    companion object {
        private const val COPY_BUFFER_SIZE = 64 * 1024
    }
}
