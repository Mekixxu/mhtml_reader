package com.html_reader.files

import android.content.Context
import java.io.IOException
import java.util.UUID

internal interface FilesUploadAttempt {
    val remoteWriteMayHaveStarted: Boolean

    fun setRemoteTarget(description: String)

    fun markRemoteWriteMayHaveStarted()

    fun markCompleted()
}

internal class FilesPendingUploadStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun newAttempt(): FilesUploadAttempt {
        val id = UUID.randomUUID().toString()
        return object : FilesUploadAttempt {
            private var remoteTarget: String? = null

            @Volatile
            override var remoteWriteMayHaveStarted: Boolean = false
                private set

            override fun setRemoteTarget(description: String) {
                remoteTarget = description
                    .replace(Regex("[\\r\\n\\t]"), " ")
                    .take(MAX_DESCRIPTION_LENGTH)
            }

            override fun markRemoteWriteMayHaveStarted() {
                if (remoteWriteMayHaveStarted) return
                val persisted = synchronized(LOCK) {
                    val target = checkNotNull(remoteTarget) { "Remote upload target was not recorded" }
                    val attempts = pendingIds().apply { add(id) }
                    preferences.edit()
                        .putStringSet(PENDING_IDS_KEY, attempts)
                        .putString(recordKey(id), target)
                        .commit()
                }
                if (!persisted) throw IOException("Unable to persist pending upload state")
                remoteWriteMayHaveStarted = true
            }

            override fun markCompleted() {
                if (!remoteWriteMayHaveStarted) return
                val cleared = synchronized(LOCK) {
                    val attempts = pendingIds().apply { remove(id) }
                    preferences.edit()
                        .putStringSet(PENDING_IDS_KEY, attempts)
                        .remove(recordKey(id))
                        .commit()
                }
                if (cleared) remoteWriteMayHaveStarted = false
            }
        }
    }

    fun hasPendingUploads(): Boolean = synchronized(LOCK) { pendingIds().isNotEmpty() }

    fun pendingDescriptions(): List<String> = synchronized(LOCK) {
        pendingIds().mapNotNull { preferences.getString(recordKey(it), null) }.sorted()
    }

    fun acknowledgePendingUploads(): Boolean = synchronized(LOCK) {
        val editor = preferences.edit().remove(PENDING_IDS_KEY)
        pendingIds().forEach { editor.remove(recordKey(it)) }
        editor.commit()
    }

    private fun pendingIds(): MutableSet<String> =
        preferences.getStringSet(PENDING_IDS_KEY, emptySet()).orEmpty().toMutableSet()

    private fun recordKey(id: String): String = "$RECORD_PREFIX$id"

    private companion object {
        const val PREFERENCES_NAME = "pending_uploads"
        const val PENDING_IDS_KEY = "attempt_ids"
        const val RECORD_PREFIX = "attempt_"
        const val MAX_DESCRIPTION_LENGTH = 512
        val LOCK = Any()
    }
}
