package com.html_reader.files

import java.io.File
import java.util.Locale

object FilesLocalEntries {
    fun mapDisplayableEntries(listed: List<File>, supportedExtensions: Set<String>): List<BrowserEntry> {
        return listed
            .filter { it.isDirectory || hasSupportedReaderExtension(it, supportedExtensions) }
            .map {
                BrowserEntry(
                    localFile = it,
                    name = it.name,
                    isDirectory = it.isDirectory,
                    sizeBytes = if (it.isDirectory) 0L else it.length(),
                    modifiedEpochMs = it.lastModified(),
                    modifiedText = null
                )
            }
    }

    private fun hasSupportedReaderExtension(file: File, supportedExtensions: Set<String>): Boolean {
        if (file.isDirectory) return true
        val ext = file.name.substringAfterLast('.', "").lowercase(Locale.getDefault())
        return ext in supportedExtensions
    }
}
