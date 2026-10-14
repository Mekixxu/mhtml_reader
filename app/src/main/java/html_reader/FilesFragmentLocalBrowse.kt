package com.html_reader

import android.view.View
import androidx.lifecycle.lifecycleScope
import com.html_reader.files.BrowseSource
import com.html_reader.files.BrowserEntry
import com.html_reader.files.FilesFtpCodec
import com.html_reader.files.FilesLocalEntries
import com.html_reader.files.LocalBrowseRequest
import com.html_reader.files.FilesSortHelper
import kotlinx.coroutines.CancellationException
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
    scrollRestoreGeneration++
    // 每次加载都使旧本地请求失效：source/session/path 任一变化后，旧 IO 结果不得回写 UI。
    localLoadJob?.cancel()
    localLoadJob = null
    remoteController.cancelRemoteLoad()
    val browseToken = filesBrowseRequestGuard.begin()

    if (browseSource == BrowseSource.FTP) {
        // 远程加载开始时先隐藏空态，加载完成后由 renderEntries 按结果决定
        emptyLabel.visibility = View.GONE
        remoteController.loadFtpEntries()
        return
    }
    if (browseSource == BrowseSource.SMB) {
        emptyLabel.visibility = View.GONE
        remoteController.loadSmbEntries()
        return
    }
    if (currentDir == null) {
        currentDir = requireContext().filesDir.parentFile ?: requireContext().filesDir
    }
    val dir = currentDirFile()
    updateCurrentDirLabel()
    val request = LocalBrowseRequest(
        generation = browseToken,
        sessionId = currentSessionId,
        source = browseSource,
        directory = dir
    )
    localLoadJob = viewLifecycleOwner.lifecycleScope.launch {
        try {
            val displayable = withContext(Dispatchers.IO) {
                val listed = dir.listFiles()?.toList().orEmpty()
                FilesLocalEntries.mapDisplayableEntries(listed, supportedExtensions)
            }
            if (view == null || !isCurrentLocalBrowseRequest(request)) return@launch
            applyLocalBrowseEntries(displayable)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            if (view != null && isCurrentLocalBrowseRequest(request)) {
                updateStatus(getString(R.string.files_status_invalid_start_path), isError = true)
            }
        }
    }
}

private fun FilesFragment.applyLocalBrowseEntries(displayable: List<BrowserEntry>) {
    allEntries.clear()
    allEntries.addAll(displayable)
    setLocalActionButtonsEnabled(true)
    actionCreateButton.text = getString(R.string.action_new_folder)
    actionCreateButton.contentDescription = getString(R.string.action_new_folder)
    renderEntries()
    restoreScrollState()
}

private fun FilesFragment.isCurrentLocalBrowseRequest(request: LocalBrowseRequest): Boolean =
    filesBrowseRequestGuard.isCurrent(request.generation) &&
        request.matches(
            source = browseSource,
            sessionId = currentSessionId,
            directory = currentDir
        )

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
    // 空态提示：目录为空或搜索无匹配时给出明确反馈，避免「空白 = 状态未知」
    emptyLabel.visibility = if (displayedEntries.isEmpty()) View.VISIBLE else View.GONE
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


