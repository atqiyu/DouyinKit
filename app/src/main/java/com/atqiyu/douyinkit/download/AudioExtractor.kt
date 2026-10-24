package com.atqiyu.douyinkit.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Remuxes the first audio track of a downloaded video into an .m4a file. This is used when the
 * detail payload does not expose a direct BGM URL. The audio is copied without re-encoding.
 */
class AudioExtractor {

    fun extractAudio(
        videoFile: File,
        outputFile: File,
        onProgress: (currentUs: Long, durationUs: Long) -> Unit = { _, _ -> },
    ): File {
        if (!videoFile.exists() || videoFile.length() <= 0) {
            throw DownloadException(DownloadFailure.EMPTY, "视频文件不存在")
        }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            extractor.setDataSource(videoFile.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = index
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) {
                throw DownloadException(DownloadFailure.NO_SOURCE, "该视频没有音频轨道")
            }

            extractor.selectTrack(audioTrackIndex)
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrack = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            val maxInputSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                0
            }
            val bufferSize = maxOf(maxInputSize, 256 * 1024)
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val info = MediaCodec.BufferInfo()
            var samples = 0

            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                buffer.position(0)
                buffer.limit(size)
                muxer.writeSampleData(outputTrack, buffer, info)
                samples += 1
                onProgress(info.presentationTimeUs.coerceAtLeast(0), durationUs)
                if (!extractor.advance()) break
            }

            if (samples == 0) {
                throw DownloadException(DownloadFailure.NO_SOURCE, "没有读到音频数据")
            }
        } catch (error: DownloadException) {
            throw error
        } catch (error: Exception) {
            throw DownloadException(
                DownloadFailure.NO_SOURCE,
                "提取音频失败：${error.message}",
                cause = error,
            )
        } finally {
            runCatching { extractor.release() }
            if (muxer != null) {
                if (muxerStarted) runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
        }

        if (!outputFile.exists() || outputFile.length() <= 0) {
            throw DownloadException(DownloadFailure.EMPTY, "音频文件为空")
        }
        return outputFile
    }
}
