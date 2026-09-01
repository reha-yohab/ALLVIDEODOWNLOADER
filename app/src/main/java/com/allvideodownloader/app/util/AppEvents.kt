package com.allvideodownloader.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-way channel for background components (the publish worker) to surface a message to
 * whatever UI happens to be on screen. Everything runs in a single process, so a plain
 * object-scoped flow is enough — no need for a bound service or a database.
 */
object AppEvents {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun post(message: String) {
        _messages.tryEmit(message)
    }
}
