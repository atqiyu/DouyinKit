package com.atqiyu.douyinkit.ui.downloads

import com.atqiyu.douyinkit.core.model.Aweme
import com.atqiyu.douyinkit.core.model.AwemeKind
import com.atqiyu.douyinkit.download.DownloadKind
import com.atqiyu.douyinkit.download.DownloadRequest
import com.atqiyu.douyinkit.download.DownloadStatus
import com.atqiyu.douyinkit.download.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadGroupsTest {

    private fun aweme(id: String): Aweme = Aweme(
        awemeId = id,
        kind = AwemeKind.VIDEO,
        desc = "作品 $id",
    )

    private fun task(
        id: String,
        awemeId: String,
        status: DownloadStatus,
        createdAt: Long,
        kind: DownloadKind = DownloadKind.VIDEO,
    ): DownloadTask = DownloadTask(
        id = id,
        request = DownloadRequest(aweme = aweme(awemeId), kind = kind),
        status = status,
        createdAt = createdAt,
    )

    @Test
    fun groupsTasksByAwemeAndSortsGroupsByNewestTask() {
        val groups = buildDownloadGroups(
            listOf(
                task("a", "1", DownloadStatus.COMPLETED, 100),
                task("b", "2", DownloadStatus.COMPLETED, 300),
                task("c", "1", DownloadStatus.COMPLETED, 200),
            ),
        )

        assertEquals(listOf("2", "1"), groups.map { it.awemeId })
        assertEquals(2, groups.single { it.awemeId == "1" }.tasks.size)
        assertEquals(1, groups.single { it.awemeId == "2" }.tasks.size)
    }

    @Test
    fun activeDownloadsMakeTheGroupActive() {
        val group = buildDownloadGroups(
            listOf(
                task("done", "1", DownloadStatus.COMPLETED, 100),
                task("running", "1", DownloadStatus.RUNNING, 200, DownloadKind.MUSIC),
            ),
        ).single()

        assertTrue(group.isActive)
        assertEquals("正在下载 1 个任务", group.statusLine)
        assertTrue(group.failedTasks.isEmpty())
    }

    @Test
    fun completedAndFailedTasksProducePartialStatus() {
        val group = buildDownloadGroups(
            listOf(
                task("done", "1", DownloadStatus.COMPLETED, 100),
                task("failed", "1", DownloadStatus.FAILED, 200),
            ),
        ).single()

        assertFalse(group.isActive)
        assertEquals("部分完成", group.statusLine)
        assertEquals(1, group.failedTasks.size)
    }

    @Test
    fun taskTypeLabelIncludesSelectedVideoQuality() {
        val music = task("music", "1", DownloadStatus.COMPLETED, 100, DownloadKind.MUSIC)
        assertEquals("BGM", music.typeLabel())
    }
}
