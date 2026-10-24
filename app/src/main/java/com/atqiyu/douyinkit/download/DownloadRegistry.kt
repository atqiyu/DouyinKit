package com.atqiyu.douyinkit.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object DownloadRegistry {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun add(task: DownloadTask) {
        _tasks.update { current ->
            current.filterNot { it.id == task.id } + task
        }
    }

    fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { current ->
            current.map { task ->
                if (task.id == id) transform(task) else task
            }
        }
    }

    fun get(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    fun remove(id: String) {
        _tasks.update { current -> current.filterNot { it.id == id } }
    }

    fun clearFinished() {
        _tasks.update { current ->
            current.filter {
                it.status != DownloadStatus.COMPLETED &&
                    it.status != DownloadStatus.PARTIAL &&
                    it.status != DownloadStatus.FAILED &&
                    it.status != DownloadStatus.CANCELLED
            }
        }
    }

    fun clearAll() {
        _tasks.value = emptyList()
    }
}
