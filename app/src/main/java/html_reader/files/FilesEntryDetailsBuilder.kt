package com.html_reader.files

import android.content.Context
import com.html_reader.R
import java.text.DateFormat
import java.util.Date

object FilesEntryDetailsBuilder {
    fun buildMessage(context: Context, source: BrowseSource, entry: BrowserEntry): String {
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
        val typeLabel = if (entry.isDirectory) "DIR" else "FILE"
        return context.getString(
            R.string.files_entry_details_template,
            entry.name,
            typeLabel,
            path,
            size,
            modified,
            sourceLabel
        )
    }
}
