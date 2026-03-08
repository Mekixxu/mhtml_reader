package com.html_reader

import android.content.Context
import android.net.Uri
import core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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

    init {
        scope.launch {
            state.value = load()
        }
    }

    fun observe(): Flow<List<AuthorizedDir>> = state.asStateFlow()

    fun getAll(): List<AuthorizedDir> = state.value

    fun upsert(treeUri: Uri, displayName: String) {
        val uriText = treeUri.toString()
        scope.launch {
            val current = if (state.value.isEmpty()) load() else state.value
            val merged = current.filterNot { it.treeUri == uriText } + AuthorizedDir(uriText, displayName)
            save(merged)
        }
    }

    fun remove(treeUri: String) {
        scope.launch {
            val current = if (state.value.isEmpty()) load() else state.value
            val next = current.filterNot { it.treeUri == treeUri }
            save(next)
        }
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
}
