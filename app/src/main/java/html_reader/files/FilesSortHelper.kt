package com.html_reader.files

import java.util.Locale

object FilesSortHelper {
    fun sortEntriesWithinGroup(entries: List<BrowserEntry>, sortIndex: Int): List<BrowserEntry> {
        val comparator = when (sortIndex) {
            0 -> compareBy<BrowserEntry> { it.name.lowercase(Locale.getDefault()) }
                .thenBy { it.modifiedEpochMs ?: Long.MIN_VALUE }
                .thenBy { it.sizeBytes }
            1 -> compareByDescending<BrowserEntry> { it.name.lowercase(Locale.getDefault()) }
                .thenByDescending { it.modifiedEpochMs ?: Long.MIN_VALUE }
                .thenByDescending { it.sizeBytes }
            2 -> compareByDescending<BrowserEntry> { it.modifiedEpochMs ?: Long.MIN_VALUE }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
                .thenByDescending { it.sizeBytes }
            3 -> compareByDescending<BrowserEntry> { it.sizeBytes }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
                .thenByDescending { it.modifiedEpochMs ?: Long.MIN_VALUE }
            4 -> compareBy<BrowserEntry> { it.sizeBytes }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
                .thenByDescending { it.modifiedEpochMs ?: Long.MIN_VALUE }
            else -> compareBy<BrowserEntry> { it.name.lowercase(Locale.getDefault()) }
                .thenByDescending { it.modifiedEpochMs ?: Long.MIN_VALUE }
                .thenByDescending { it.sizeBytes }
        }
        return entries.sortedWith(comparator)
    }
}
