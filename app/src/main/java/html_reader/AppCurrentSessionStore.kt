package com.html_reader

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppCurrentSessionStore {
    private val currentId = MutableStateFlow<Long?>(null)

    fun observe(): Flow<Long?> = currentId.asStateFlow()

    suspend fun set(sessionId: Long?) {
        currentId.value = sessionId
    }

    fun get(): Long? = currentId.value
}
