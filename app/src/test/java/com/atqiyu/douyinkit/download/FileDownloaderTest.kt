package com.atqiyu.douyinkit.download

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var directory: File
    private lateinit var downloader: FileDownloader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        directory = Files.createTempDirectory("douyinkit-download").toFile()
        downloader = FileDownloader(
            client = OkHttpClient(),
            urlPolicy = { true },
            urlNormalizer = { it },
        )
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        directory.deleteRecursively()
    }

    @Test
    fun resumesPartialMp4WithRangeRequest() = runBlocking {
        val full = mp4Bytes(2048)
        val destination = File(directory, "video.mp4")
        destination.writeBytes(full.copyOfRange(0, 4))
        val remaining = full.copyOfRange(4, full.size)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Type", "video/mp4")
                .addHeader("Content-Range", "bytes 4-${full.size - 1}/${full.size}")
                .setBody(Buffer().write(remaining)),
        )

        val result = downloader.download(
            candidates = listOf(server.url("/video.mp4").toString()),
            destination = destination,
            expectedSize = full.size.toLong(),
            kind = MediaKind.VIDEO,
        )

        val request = server.takeRequest()
        assertEquals("bytes=4-", request.getHeader("Range"))
        assertEquals("mp4", result.extension)
        assertArrayEquals(full, destination.readBytes())
    }

    @Test
    fun mapsForbiddenMirrorToExpired() {
        server.enqueue(MockResponse().setResponseCode(403))
        val error = assertThrows(DownloadException::class.java) {
            runBlocking {
                downloader.download(
                    candidates = listOf(server.url("/video.mp4").toString()),
                    destination = File(directory, "video.mp4"),
                    expectedSize = 100,
                    kind = MediaKind.VIDEO,
                )
            }
        }
        assertEquals(DownloadFailure.EXPIRED, error.failure)
    }

    @Test
    fun rejectsHtmlResponse() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html>blocked</html>"),
        )
        val error = assertThrows(DownloadException::class.java) {
            runBlocking {
                downloader.download(
                    candidates = listOf(server.url("/video.mp4").toString()),
                    destination = File(directory, "video.mp4"),
                    expectedSize = null,
                    kind = MediaKind.VIDEO,
                )
            }
        }
        assertEquals(DownloadFailure.NOT_MEDIA, error.failure)
    }

    @Test
    fun rejectsInvalidMagicBytes() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "video/mp4")
                .setBody("not an mp4 file"),
        )
        val error = assertThrows(DownloadException::class.java) {
            runBlocking {
                downloader.download(
                    candidates = listOf(server.url("/video.mp4").toString()),
                    destination = File(directory, "video.mp4"),
                    expectedSize = null,
                    kind = MediaKind.VIDEO,
                )
            }
        }
        assertEquals(DownloadFailure.NOT_MEDIA, error.failure)
        assertTrue(!File(directory, "video.mp4").exists())
    }

    private fun mp4Bytes(size: Int): ByteArray {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            0x66, 0x74, 0x79, 0x70,
            0x6D, 0x70, 0x34, 0x32,
        )
        val payload = ByteArray((size - header.size).coerceAtLeast(0)) { index ->
            (index % 251).toByte()
        }
        return header + payload
    }
}
