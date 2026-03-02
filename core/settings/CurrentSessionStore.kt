package core.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 非持久化version，真实APP中可用DataStore/Preferences实现持久记录当前会话id。
 */
class CurrentSessionStore {
    private val currentId = MutableStateFlow<Long?>(null)
    fun observe(): Flow<Long?> = currentId.asStateFlow()
    suspend fun set(sessionId: Long?) { currentId.value = sessionId }
    fun get(): Long? = currentId.value
}
