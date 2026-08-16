package com.html_reader

import androidx.lifecycle.lifecycleScope
import com.html_reader.files.BrowseSource
import com.html_reader.files.BrowserEntry
import com.html_reader.files.FilesFtpCodec
import com.html_reader.files.FilesLocalEntries
import com.html_reader.files.FilesSortHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

internal fun FilesFragment.formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

internal fun FilesFragment.loadEntries() {
    remoteController.cancelRemoteLoad()
    if (browseSource == BrowseSource.FTP) {
        remoteController.loadFtpEntries()
        return
    }
    if (browseSource == BrowseSource.SMB) {
        remoteController.loadSmbEntries()
        return
    }
    if (currentDir == null) {
        currentDir = requireContext().filesDir.parentFile ?: requireContext().filesDir
    }
    val dir = currentDirFile()
    currentDirLabel.text = buildCurrentDirText(dir)
    viewLifecycleOwner.lifecycleScope.launch {
        val displayable = withContext(Dispatchers.IO) {
            val listed = dir.listFiles()?.toList().orEmpty()
            FilesLocalEntries.mapDisplayableEntries(listed, supportedExtensions)
        }
        allEntries.clear()
        allEntries.addAll(displayable)
        displayTitleByPath.clear()
        setLocalActionButtonsEnabled(true)
        actionCreateButton.text = getString(R.string.action_new_folder)
        renderEntries()
        refreshTitlesAsync()
    }
}

internal fun FilesFragment.renderEntries() {
    val query = queryInput.text?.toString()?.trim().orEmpty().lowercase(Locale.getDefault())
    val filtered = allEntries.filter {
        query.isBlank() || matchesQuery(it, query)
    }
    val directories = filtered.filter { it.isDirectory }
    val files = filtered.filter { !it.isDirectory }
    val sorted = sortEntriesWithinGroup(directories) + sortEntriesWithinGroup(files)
    displayedEntries.clear()
    displayedEntries.addAll(sorted)
    adapter.notifyDataSetChanged()
}

internal fun FilesFragment.matchesQuery(entry: BrowserEntry, query: String): Boolean {
    if (entry.name.lowercase(Locale.getDefault()).contains(query)) {
        return true
    }
    if (browseSource != BrowseSource.FTP) {
        return false
    }
    val raw = entry.rawNameBytes ?: return false
    val charsets = listOfNotNull(
        remoteController.configuredFtpCharsetName(remoteController.ftpConfig),
        remoteController.ftpResolvedCharset,
        "UTF-8",
        "GBK",
        "Big5",
        "Shift_JIS"
    ).distinct()
    return charsets.any { cs ->
        FilesFtpCodec.decodeForSearch(raw, cs, remoteController.ftpDecodeCache)
            ?.lowercase(Locale.getDefault())?.contains(query) == true
    }
}

internal fun FilesFragment.sortEntriesWithinGroup(entries: List<BrowserEntry>): List<BrowserEntry> {
    return FilesSortHelper.sortEntriesWithinGroup(entries, sortSpinner.selectedItemPosition)
}

internal fun FilesFragment.refreshTitlesAsync() {
    if (browseSource != BrowseSource.LOCAL) {
        return
    }
    titleRefreshJob?.cancel()
    val snapshot = allEntries.toList()
    titleRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
        val localFiles = snapshot.filter { !it.isDirectory }.mapNotNull { it.localFile }
        filesTitleRefresher.refreshLocalTitles(
            files = localFiles,
            onCachedTitle = { path, title ->
                displayTitleByPath[path] = title
            },
            onResolvedTitle = { path, title ->
                displayTitleByPath[path] = title
            }
        )
        renderEntries()
    }
}
