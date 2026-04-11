package com.html_reader.files

import java.text.DateFormat
import java.util.Date

object FilesEntryDetailsBuilder {
    fun buildMessage(source: BrowseSource, entry: BrowserEntry): String {
        val sourceLabel = when (source) {
            BrowseSource.LOCAL -> "LOCAL"
            BrowseSource.FTP -> "FTP"
            BrowseSource.SMB -> "SMB"
        }
        val path = entry.localFile?.absolutePath ?: entry.ftpPath ?: entry.smbPath ?: "-"
        val size = if (entry.isDirectory) "-" else entry.sizeBytes.toString()
        val modified = entry.modifiedText
            ?: entry.modifiedEpochMs?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
            ?: "-"
        return "Name: ${entry.name}\nType: ${if (entry.isDirectory) "DIR" else "FILE"}\nPath: $path\nSize: $size\nModified: $modified\nSource: $sourceLabel"
    }
}
