package com.atqiyu.douyinkit.download

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ZipExporterTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("douyinkit-zip").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun writesEntriesAndInfo() {
        val a = File(dir, "a.txt").apply { writeText("alpha") }
        val b = File(dir, "b.txt").apply { writeText("beta") }
        val output = File(dir, "post.zip")
        ZipExporter().zip(
            sources = listOf(
                ZipEntrySource(a, "01.txt"),
                ZipEntrySource(b, "../evil.txt"),
            ),
            outputFile = output,
            infoJson = """{"awemeId":"1"}""",
        )

        ZipFile(output).use { zip ->
            assertEquals("alpha", zip.getInputStream(zip.getEntry("01.txt")).bufferedReader().readText())
            assertEquals("beta", zip.getInputStream(zip.getEntry("evil.txt")).bufferedReader().readText())
            assertTrue(zip.getEntry("info.json") != null)
            assertTrue(zip.getEntry("../evil.txt") == null)
        }
    }
}
