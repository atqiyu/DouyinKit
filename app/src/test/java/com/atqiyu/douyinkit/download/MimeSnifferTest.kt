package com.atqiyu.douyinkit.download

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MimeSnifferTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("douyinkit-mime").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun detectsImageMagic() {
        assertEquals("webp", MimeSniffer.imageExtension(file("a.webp", webp())))
        assertEquals("jpg", MimeSniffer.imageExtension(file("b", bytes(0xFF, 0xD8, 0xFF, 0x00))))
        assertEquals("png", MimeSniffer.imageExtension(file("c", png())))
        assertEquals("gif", MimeSniffer.imageExtension(file("d", "GIF89a".toByteArray())))
        assertEquals("image/webp", MimeSniffer.imageMime("webp"))
        assertEquals("image/jpeg", MimeSniffer.imageMime("jpg"))
    }

    @Test
    fun detectsAudioAndVideoMagic() {
        assertEquals("mp3", MimeSniffer.audioExtension(file("e", "ID3".toByteArray())))
        assertEquals("m4a", MimeSniffer.audioExtension(file("f", m4a())))
        assertTrue(MimeSniffer.isMp4(file("g", mp4())))
        assertTrue(MimeSniffer.isZip(file("h", bytes(0x50, 0x4B, 0x03, 0x04))))
        assertNull(MimeSniffer.imageExtension(file("i", "hello".toByteArray())))
    }

    private fun file(name: String, content: ByteArray): File {
        return File(dir, name).apply { writeBytes(content) }
    }

    private fun webp(): ByteArray = "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray()

    private fun png(): ByteArray = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun m4a(): ByteArray = ByteArray(4) + "ftypM4A ".toByteArray()

    private fun mp4(): ByteArray = ByteArray(4) + "ftypmp42".toByteArray()

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
