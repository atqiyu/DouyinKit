package com.atqiyu.douyinkit.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressTrackerTest {

    private val megabyte = 1024L * 1024L

    @Test
    fun reportsDownloadedBytesWithoutDoublingThem() {
        val updates = mutableListOf<DownloadProgress>()
        var now = 0L
        val tracker = DownloadEngine.DownloadProgressTracker(
            callback = { updates += it },
            now = { now },
        )

        tracker.start()
        tracker.totalItems = 1
        tracker.totalBytes = 10 * megabyte
        now = 400
        tracker.updateBytes(5 * megabyte, 10 * megabyte)

        assertEquals(5 * megabyte, updates.last().bytes)
        assertEquals(10 * megabyte, updates.last().total)

        now = 800
        tracker.updateBytes(10 * megabyte, 10 * megabyte)
        assertEquals(10 * megabyte, updates.last().bytes)

        tracker.itemDone()
        assertEquals(10 * megabyte, updates.last().bytes)
        assertEquals(1, updates.last().itemsDone)
    }

    @Test
    fun secondItemStartsAtThePreviousItemTotal() {
        val updates = mutableListOf<DownloadProgress>()
        var now = 0L
        val tracker = DownloadEngine.DownloadProgressTracker(
            callback = { updates += it },
            now = { now },
        )

        tracker.start()
        tracker.totalItems = 2
        now = 400
        tracker.updateBytes(5 * megabyte)
        tracker.itemDone()

        now = 800
        tracker.updateBytes(3 * megabyte)

        assertEquals(8 * megabyte, updates.last().bytes)
        assertEquals(1, updates.last().itemsDone)
    }

    @Test
    fun totalIsNeverSmallerThanDownloadedBytes() {
        val updates = mutableListOf<DownloadProgress>()
        var now = 0L
        val tracker = DownloadEngine.DownloadProgressTracker(
            callback = { updates += it },
            now = { now },
        )

        tracker.start()
        tracker.totalBytes = 9 * megabyte
        now = 400
        tracker.updateBytes(10 * megabyte, 9 * megabyte)

        val progress = updates.last()
        assertTrue(progress.total!! >= progress.bytes)
        assertEquals(10 * megabyte, progress.displayBytes)
        assertEquals(10 * megabyte, progress.total)
    }
}
