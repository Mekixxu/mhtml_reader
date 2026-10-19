package com.html_reader

import android.content.Context
import android.net.Uri
import core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthorizedDir(
    val treeUri: String,
    val displayName: String
)

class AppAuthorizedDirStore(
    context: Context,
    private val dispatcherProvider: DispatcherProvider
) {
    private val prefs = context.applicationContext.getSharedPreferences("app_authorized_dirs", Context.MODE_PRIVATE)
    private val state = MutableStateFlow<List<AuthorizedDir>>(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    private val mutations = Channel<Mutation>(Channel.UNLIMITED)

    init {
        // 单消费者 actor：按调用顺序串行化读改写，避免并发 launch 的乱序覆盖。
        scope.launch {
            state.value = load()
            for (mutation in mutations) {
                when (mutation) {
                    is Mutation.Upsert -> {
                        val current = if (state.value.isEmpty()) load() else state.value
                        save(current.filterNot { it.treeUri == mutation.treeUri } + mutation.entry)
                    }
                    is Mutation.Remove -> {
                        val current = if (state.value.isEmpty()) load() else state.value
                        save(current.filterNot { it.treeUri == mutation.treeUri })
                    }
                }
            }
        }
    }

    fun observe(): Flow<List<AuthorizedDir>> = state.asStateFlow()

    fun getAll(): List<AuthorizedDir> = state.value

    fun upsert(treeUri: Uri, displayName: String) {
        val uriText = treeUri.toString()
        mutations.trySend(Mutation.Upsert(uriText, AuthorizedDir(uriText, displayName)))
    }

    fun remove(treeUri: String) {
        mutations.trySend(Mutation.Remove(treeUri))
    }

    private fun load(): List<AuthorizedDir> {
        val raw = prefs.getStringSet("entries", emptySet()).orEmpty()
        return raw.mapNotNull { item ->
            val i = item.indexOf('|')
            if (i <= 0 || i >= item.length - 1) {
                null
            } else {
                AuthorizedDir(item.substring(0, i), item.substring(i + 1))
            }
        }.sortedBy { it.displayName.lowercase() }
    }

    private fun save(list: List<AuthorizedDir>) {
        val normalized = list.distinctBy { it.treeUri }.sortedBy { it.displayName.lowercase() }
        prefs.edit().putStringSet("entries", normalized.map { "${it.treeUri}|${it.displayName}" }.toSet()).apply()
        state.value = normalized
    }

    private sealed interface Mutation {
        data class Upsert(val treeUri: String, val entry: AuthorizedDir) : Mutation
        data class Remove(val treeUri: String) : Mutation
    }
}
