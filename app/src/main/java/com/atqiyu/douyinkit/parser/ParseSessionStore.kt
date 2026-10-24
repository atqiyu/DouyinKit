package com.atqiyu.douyinkit.parser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-scoped handoff between ParseActivity and the main UI. The WebView cannot live in a
 * ViewModel, so the parse screen finishes and publishes a plain result here.
 */
object ParseSessionStore {
    private val _lastResult = MutableStateFlow<ParseBatchResult?>(null)
    val lastResult: StateFlow<ParseBatchResult?> = _lastResult.asStateFlow()

    fun publish(result: ParseBatchResult) {
        _lastResult.value = result
    }

    fun consume(): ParseBatchResult? {
        val result = _lastResult.value
        _lastResult.value = null
        return result
    }

    fun clear() {
        _lastResult.value = null
    }
}
