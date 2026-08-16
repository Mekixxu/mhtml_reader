package com.html_reader

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.html_reader.files.BrowseSource
import com.html_reader.files.BrowserEntry
import com.html_reader.files.FilesEntryDetailsBuilder
import com.html_reader.files.FilesFavoritePathBuilder
import com.html_reader.files.FilesFtpDiagnosticBuilder
import core.database.entity.enums.NetworkProtocol
import core.database.entity.enums.SourceType
import core.fileops.model.FileOpRequest
import core.vfs.model.VfsPath
import java.io.File
import kotlinx.coroutines.launch

internal fun FilesFragment.promptText(
        title: String,
        hint: String,
        initialValue: String,
        onSubmit: (String) -> Unit
    ) {
        val input = EditText(requireContext()).apply {
            setText(initialValue)
            setSelection(text.length)
            this.hint = hint
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text?.toString()?.trim().orEmpty()
                if (value.isNotBlank()) {
                    onSubmit(value)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }


internal fun FilesFragment.promptSmbCreateAction() {
        val options = arrayOf(
            getString(R.string.files_create_folder_title),
            getString(R.string.action_upload_file)
        )
        AlertDialog.Builder(requireContext())
            .setItems(options) { _, which ->
                if (which == 0) {
                    promptText(
                        title = getString(R.string.files_create_folder_title),
                        hint = getString(R.string.files_input_hint_folder_name),
                        initialValue = ""
                    ) { folderName ->
                        remoteController.createSmbFolder(folderName)
                    }
                } else {
                    smbUploadLauncher.launch(arrayOf("*/*"))
                }
            }
            .show()
    }


internal fun FilesFragment.addEntryToFavorites(entry: BrowserEntry) {
        val favoritePath = FilesFavoritePathBuilder.buildPath(
            source = browseSource,
            entry = entry,
            ftpConfig = remoteController.ftpConfig,
            smbConfig = remoteController.smbConfig,
            ftpCharsetProvider = { remoteController.ftpEffectiveCharset(it) }
        ) ?: return
        val sourceType = FilesFavoritePathBuilder.sourceTypeFor(browseSource)
        viewLifecycleOwner.lifecycleScope.launch {
            if (entry.isDirectory) {
                favoritesRepository.addDirectory(
                    parentId = null,
                    name = entry.name,
                    path = favoritePath,
                    sourceType = sourceType
                )
            } else {
                favoritesRepository.addFile(
                    parentId = null,
                    name = entry.name,
                    path = favoritePath,
                    sourceType = sourceType
                )
            }
            val messageRes = if (sourceType == SourceType.FTP || sourceType == SourceType.SMB) {
                R.string.favorites_added_network_credential_note
            } else {
                R.string.favorites_added
            }
            Toast.makeText(requireContext(), getString(messageRes), Toast.LENGTH_SHORT).show()
        }
    }


internal fun FilesFragment.showEntryDetails(entry: BrowserEntry) {
        val message = FilesEntryDetailsBuilder.buildMessage(requireContext(), browseSource, entry)
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.files_action_details))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }


internal fun FilesFragment.showDiagnosticDialog(entry: BrowserEntry) {
        val rawBytes = entry.rawNameBytes
        if (rawBytes == null) {
            Toast.makeText(requireContext(), getString(R.string.files_msg_no_raw_bytes), Toast.LENGTH_SHORT).show()
            return
        }
        val currentEncoding = remoteController.ftpConfig?.encoding ?: "Auto"
        val message = FilesFtpDiagnosticBuilder.buildMessage(rawBytes, currentEncoding)

        AlertDialog.Builder(requireContext())
            .setTitle("Encoding Diagnosis")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton("Copy") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Diagnostic Info", message)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), getString(R.string.common_copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }
            .show()
    }


internal fun FilesFragment.promptRename(entry: BrowserEntry) {
        promptText(
            title = getString(R.string.action_rename),
            hint = getString(R.string.files_input_hint_new_name),
            initialValue = entry.name
        ) { newName ->
            if (browseSource == BrowseSource.SMB) {
                remoteController.renameSmbEntry(entry, newName)
            } else if (browseSource == BrowseSource.LOCAL) {
                val localFile = entry.localFile
                if (localFile == null) {
                    updateStatus(getString(R.string.files_select_item_first), isError = true)
                    return@promptText
                }
                runOperation(
                    FileOpRequest.Rename(
                        target = VfsPath.LocalFile(localFile.absolutePath),
                        newName = newName
                    )
                )
            }
        }
    }


internal fun FilesFragment.promptDelete(entry: BrowserEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_delete)
            .setMessage(getString(R.string.files_dialog_delete_message, entry.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (browseSource == BrowseSource.SMB) {
                    remoteController.deleteSmbEntry(entry)
                } else if (browseSource == BrowseSource.LOCAL) {
                    val localFile = entry.localFile
                    if (localFile == null) {
                        updateStatus(getString(R.string.files_select_item_first), isError = true)
                        return@setPositiveButton
                    }
                    runOperation(
                        FileOpRequest.Delete(
                            target = VfsPath.LocalFile(localFile.absolutePath)
                        )
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }


internal fun File.toVfsPath(): VfsPath.LocalFile = VfsPath.LocalFile(absolutePath)

internal fun FilesFragment.showEntryActions(entry: BrowserEntry) {
    selectedEntry = entry
    renderEntries() // Update selection UI

    val isFile = !entry.isDirectory
    val options = mutableListOf<String>()

    if (isFile) {
        options.add(getString(R.string.files_action_open_new_tab))
    }
    options.add(getString(R.string.files_action_add_favorite))
    options.add(getString(R.string.files_action_details))

    // Check capability instead of just browseSource
    val isLocal = browseSource == BrowseSource.LOCAL
    val isSmb = browseSource == BrowseSource.SMB && entry.smbPath != null

    if (isLocal || isSmb) {
        options.add(getString(R.string.action_rename))
        options.add(getString(R.string.action_delete))
    }

    if (browseSource == BrowseSource.FTP) {
        options.add(getString(R.string.files_action_diagnose_encoding))
    }

    AlertDialog.Builder(requireContext())
        .setItems(options.toTypedArray()) { _, which ->
            val selectedOption = options[which]
            when (selectedOption) {
                getString(R.string.files_action_open_new_tab) -> {
                    if (browseSource == BrowseSource.FTP) {
                        remoteController.openFtpFile(entry, isBackground = true)
                    } else if (browseSource == BrowseSource.SMB) {
                        remoteController.openSmbFile(entry, isBackground = true)
                    } else {
                        entry.localFile?.let { openFileInBackground(it, entry.name) }
                    }
                }
                getString(R.string.files_action_add_favorite) -> addEntryToFavorites(entry)
                getString(R.string.files_action_details) -> showEntryDetails(entry)
                getString(R.string.action_rename) -> promptRename(entry)
                getString(R.string.action_delete) -> promptDelete(entry)
                getString(R.string.files_action_diagnose_encoding) -> showDiagnosticDialog(entry)
            }
        }
        .show()
}
