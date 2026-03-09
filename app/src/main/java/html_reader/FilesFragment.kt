package com.html_reader

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import core.common.DefaultDispatcherProvider
import core.data.repo.FavoritesRepository
import core.data.repo.NetworkConfigRepository
import core.data.repo.TitleCacheRepository
import core.database.entity.NetworkConfigEntity
import core.database.entity.TitleCacheEntity
import core.database.entity.enums.FileType
import core.database.entity.enums.NetworkProtocol
import core.database.entity.enums.SourceType
import core.fileops.model.ConflictStrategy
import core.fileops.model.FileOpRequest
import core.fileops.model.FileOpState
import core.fileops.usecase.ExecuteFileOpUseCase
import core.fileops.util.NameConflictResolver
import core.session.repo.FolderSessionRepository
import core.title.impl.HtmlTitleExtractor
import core.vfs.local.LocalFileSystem
import core.vfs.model.VfsPath
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import jcifs.CIFSContext
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilesFragment : Fragment() {
    private enum class BrowseSource {
        LOCAL,
        FTP,
        SMB
    }

    private data class BrowserEntry(
        val localFile: File? = null,
        val ftpPath: String? = null,
        val smbPath: String? = null,
        val name: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val modifiedEpochMs: Long?,
        val modifiedText: String? = null,
        val rawNameBytes: ByteArray? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BrowserEntry

            if (localFile != other.localFile) return false
            if (ftpPath != other.ftpPath) return false
            if (smbPath != other.smbPath) return false
            if (name != other.name) return false
            if (isDirectory != other.isDirectory) return false
            if (sizeBytes != other.sizeBytes) return false
            if (modifiedEpochMs != other.modifiedEpochMs) return false
            if (modifiedText != other.modifiedText) return false
            if (rawNameBytes != null) {
                if (other.rawNameBytes == null) return false
                if (!rawNameBytes.contentEquals(other.rawNameBytes)) return false
            } else if (other.rawNameBytes != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = localFile?.hashCode() ?: 0
            result = 31 * result + (ftpPath?.hashCode() ?: 0)
            result = 31 * result + (smbPath?.hashCode() ?: 0)
            result = 31 * result + name.hashCode()
            result = 31 * result + isDirectory.hashCode()
            result = 31 * result + sizeBytes.hashCode()
            result = 31 * result + (modifiedEpochMs?.hashCode() ?: 0)
            result = 31 * result + (modifiedText?.hashCode() ?: 0)
            result = 31 * result + (rawNameBytes?.contentHashCode() ?: 0)
            return result
        }
    }

    private lateinit var queryInput: EditText
    private lateinit var sortSpinner: Spinner
    private lateinit var currentDirLabel: TextView
    private lateinit var operationStatusLabel: TextView
    private lateinit var operationProgress: ProgressBar
    private lateinit var actionUpButton: Button
    private lateinit var actionCreateButton: Button
    private lateinit var actionRenameButton: Button
    private lateinit var actionDeleteButton: Button
    private lateinit var actionDuplicateButton: Button
    private lateinit var actionMoveParentButton: Button
    private lateinit var actionNewSessionButton: Button
    private lateinit var listView: android.widget.ListView
    private lateinit var adapter: ArrayAdapter<BrowserEntry>
    private lateinit var executeFileOpUseCase: ExecuteFileOpUseCase
    private lateinit var folderSessionRepository: FolderSessionRepository
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var networkConfigRepository: NetworkConfigRepository
    private lateinit var titleCacheRepository: TitleCacheRepository
    private lateinit var currentSessionStore: AppCurrentSessionStore
    private lateinit var sessionSourceStore: AppSessionSourceStore
    private lateinit var htmlTitleExtractor: HtmlTitleExtractor

    private val allEntries = mutableListOf<BrowserEntry>()
    private val displayedEntries = mutableListOf<BrowserEntry>()
    private var currentDir: File? = null
    private var selectedEntry: BrowserEntry? = null
    private var operationRunning = false
    private var currentSessionId: Long? = null
    private var initialNetworkConfigId: Long? = null
    private var initialStartPath: String? = null
    private var initialSafTreeUri: String? = null
    private var currentNetworkLabel: String? = null
    private var browseSource: BrowseSource = BrowseSource.LOCAL
    private var ftpConfig: NetworkConfigEntity? = null
    private var ftpCurrentPath: String = "/"
    private var smbConfig: NetworkConfigEntity? = null
    private var smbCurrentPath: String = "/"
    private var ftpLoadToken: Long = 0L
    private var titleRefreshJob: Job? = null
    private val supportedExtensions = setOf("mht", "mhtml", "pdf", "html", "htm")
    private val displayTitleByPath = mutableMapOf<String, String>()
    private val ftpUploadLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            uploadDocumentToFtp(uri)
        }
    }
    private val smbUploadLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            uploadDocumentToSmb(uri)
        }
    }

    companion object {
        private const val ARG_NETWORK_CONFIG_ID = "arg_network_config_id"
        private const val ARG_START_PATH = "arg_start_path"
        private const val ARG_SAF_TREE_URI = "arg_saf_tree_uri"

        fun newInstance(networkConfigId: Long): FilesFragment {
            return FilesFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_NETWORK_CONFIG_ID, networkConfigId)
                }
            }
        }

        fun newInstanceForNetworkPath(networkConfigId: Long, path: String): FilesFragment {
            return FilesFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_NETWORK_CONFIG_ID, networkConfigId)
                    putString(ARG_START_PATH, path)
                }
            }
        }

        fun newInstanceForPath(path: String): FilesFragment {
            return FilesFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_START_PATH, path)
                }
            }
        }

        fun newInstanceForSafTree(treeUri: String): FilesFragment {
            return FilesFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SAF_TREE_URI, treeUri)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_files, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        queryInput = view.findViewById(R.id.files_query_input)
        sortSpinner = view.findViewById(R.id.files_sort_spinner)
        currentDirLabel = view.findViewById(R.id.files_current_dir)
        operationStatusLabel = view.findViewById(R.id.files_operation_status)
        operationProgress = view.findViewById(R.id.files_operation_progress)
        actionUpButton = view.findViewById(R.id.files_action_up)
        actionCreateButton = view.findViewById(R.id.files_action_create)
        actionRenameButton = view.findViewById(R.id.files_action_rename)
        actionDeleteButton = view.findViewById(R.id.files_action_delete)
        actionDuplicateButton = view.findViewById(R.id.files_action_duplicate)
        actionMoveParentButton = view.findViewById(R.id.files_action_move_parent)
        actionNewSessionButton = view.findViewById(R.id.files_action_new_session)
        listView = view.findViewById(R.id.files_list)

        adapter = object : ArrayAdapter<BrowserEntry>(requireContext(), android.R.layout.simple_list_item_2, displayedEntries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false)
                val item = getItem(position) ?: return view
                val text1 = view.findViewById<TextView>(android.R.id.text1)
                val text2 = view.findViewById<TextView>(android.R.id.text2)

                val key = item.pathKey()
                val displayTitle = if (key == null) null else displayTitleByPath[key]
                val namePart = if (displayTitle.isNullOrBlank() || displayTitle == item.name) item.name else displayTitle

                val typeLabel = if (item.isDirectory) "[DIR]" else "[FILE]"
                val sizeLabel = if (item.isDirectory) "" else formatSize(item.sizeBytes)
                val timeLabel = item.modifiedText ?: item.modifiedEpochMs?.let { DateFormat.getDateTimeInstance().format(Date(it)) }.orEmpty()
                
                val metaPart = listOf(sizeLabel, timeLabel).filter { it.isNotBlank() }.joinToString("  •  ")

                val selectedPrefix = if (item.isSelected(selectedEntry)) "▶ " else ""
                
                text1.text = "$selectedPrefix$typeLabel $namePart"
                text2.text = metaPart
                return view
            }
        }
        listView.adapter = adapter

        val dispatcherProvider = DefaultDispatcherProvider()
        val fileSystem = LocalFileSystem(requireContext().applicationContext, dispatcherProvider)
        val nameConflictResolver = NameConflictResolver(fileSystem, dispatcherProvider)
        executeFileOpUseCase = ExecuteFileOpUseCase(fileSystem, nameConflictResolver, dispatcherProvider)
        folderSessionRepository = FilesRuntime.folderSessionRepository(requireContext())
        favoritesRepository = FilesRuntime.favoritesRepository(requireContext())
        networkConfigRepository = FilesRuntime.networkConfigRepository(requireContext())
        titleCacheRepository = FilesRuntime.titleCacheRepository(requireContext())
        currentSessionStore = FilesRuntime.currentSessionStore(requireContext())
        sessionSourceStore = FilesRuntime.sessionSourceStore(requireContext())
        htmlTitleExtractor = HtmlTitleExtractor(DefaultDispatcherProvider())
        initialNetworkConfigId = arguments?.getLong(ARG_NETWORK_CONFIG_ID)?.takeIf { it > 0L }
        initialStartPath = arguments?.getString(ARG_START_PATH)?.trim()?.takeIf { it.isNotBlank() }
        initialSafTreeUri = arguments?.getString(ARG_SAF_TREE_URI)?.trim()?.takeIf { it.isNotBlank() }

        sortSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.sort_name_asc),
                getString(R.string.sort_name_desc),
                getString(R.string.sort_modified_desc),
                getString(R.string.sort_size_desc)
            )
        )

        sortSpinner.setSelection(2, false)
        sortSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                renderEntries()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        })

        queryInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                renderEntries()
            }
        })

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val item = displayedEntries.getOrNull(position) ?: return@OnItemClickListener
            if (item.isDirectory) {
                if (browseSource == BrowseSource.FTP) {
                    ftpCurrentPath = item.ftpPath ?: ftpCurrentPath
                    selectedEntry = null
                    loadEntries()
                    persistCurrentDir()
                    return@OnItemClickListener
                }
                if (browseSource == BrowseSource.SMB) {
                    smbCurrentPath = item.smbPath ?: smbCurrentPath
                    selectedEntry = null
                    loadEntries()
                    persistCurrentDir()
                    return@OnItemClickListener
                }
                currentDir = item.localFile
                selectedEntry = null
                loadEntries()
                persistCurrentDir()
            } else {
                // Direct open logic
                selectedEntry = null
                if (browseSource == BrowseSource.FTP) {
                    openFtpFile(item)
                } else if (browseSource == BrowseSource.SMB) {
                    openSmbFile(item)
                } else {
                    val path = item.localFile?.absolutePath ?: return@OnItemClickListener
                    (activity as? MainActivity)?.showReaderModeWithPath(path)
                }
            }
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val item = displayedEntries.getOrNull(position) ?: return@OnItemLongClickListener true
            selectedEntry = item
            renderEntries() // Update selection UI
            
            val isFile = !item.isDirectory
            val options = mutableListOf<String>()
            options.add(getString(R.string.files_action_select)) // Keep "Select" as an option if needed, or just rely on the visual selection update
            if (isFile) {
                options.add("Open in new tab")
            }
            options.add(getString(R.string.files_action_add_favorite))
            options.add(getString(R.string.files_action_details))
            if (browseSource == BrowseSource.FTP) {
                options.add("Diagnose Encoding")
            }

            AlertDialog.Builder(requireContext())
                .setItems(options.toTypedArray()) { _, which ->
                    val selectedOption = options[which]
                    when (selectedOption) {
                        "Open in new tab" -> {
                            if (browseSource == BrowseSource.FTP) {
                                openFtpFile(item)
                            } else if (browseSource == BrowseSource.SMB) {
                                openSmbFile(item)
                            } else {
                                val path = item.localFile?.absolutePath
                                if (path != null) {
                                    (activity as? MainActivity)?.showReaderModeWithPath(path)
                                }
                            }
                        }
                        getString(R.string.files_action_add_favorite) -> addEntryToFavorites(item)
                        getString(R.string.files_action_details) -> showEntryDetails(item)
                        "Diagnose Encoding" -> showDiagnosticDialog(item)
                    }
                }
                .show()
            true
        }

        actionUpButton.setOnClickListener {
            navigateUp()
        }

        actionCreateButton.setOnClickListener {
            if (browseSource == BrowseSource.FTP) {
                ftpUploadLauncher.launch(arrayOf("*/*"))
                return@setOnClickListener
            }
            if (browseSource == BrowseSource.SMB) {
                promptSmbCreateAction()
                return@setOnClickListener
            }
            promptText(
                title = getString(R.string.files_create_folder_title),
                hint = getString(R.string.files_input_hint_folder_name),
                initialValue = ""
            ) { folderName ->
                runOperation(
                    FileOpRequest.CreateFolder(
                        parentDir = currentDirFile().toVfsPath(),
                        name = folderName
                    )
                )
            }
        }

        actionRenameButton.setOnClickListener {
            if (browseSource == BrowseSource.SMB) {
                val selected = selectedEntry
                if (selected == null || selected.smbPath.isNullOrBlank()) {
                    Toast.makeText(requireContext(), getString(R.string.files_select_item_first), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                promptText(
                    title = getString(R.string.files_rename_title),
                    hint = getString(R.string.files_input_hint_new_name),
                    initialValue = selected.name
                ) { newName ->
                    renameSmbEntry(selected, newName)
                }
                return@setOnClickListener
            }
            val selected = requireLocalSelection() ?: return@setOnClickListener
            promptText(
                title = getString(R.string.files_rename_title),
                hint = getString(R.string.files_input_hint_new_name),
                initialValue = selected.localFile?.name.orEmpty()
            ) { newName ->
                val local = selected.localFile ?: return@promptText
                runOperation(FileOpRequest.Rename(local.toVfsPath(), newName))
            }
        }

        actionDeleteButton.setOnClickListener {
            if (browseSource == BrowseSource.SMB) {
                val selected = selectedEntry
                if (selected == null || selected.smbPath.isNullOrBlank()) {
                    Toast.makeText(requireContext(), getString(R.string.files_select_item_first), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                deleteSmbEntry(selected)
                return@setOnClickListener
            }
            val selected = requireLocalSelection() ?: return@setOnClickListener
            val local = selected.localFile ?: return@setOnClickListener
            runOperation(FileOpRequest.Delete(local.toVfsPath(), recursive = true))
        }

        actionDuplicateButton.setOnClickListener {
            if (browseSource != BrowseSource.LOCAL) {
                Toast.makeText(requireContext(), getString(R.string.files_status_action_not_supported), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selected = requireLocalSelection() ?: return@setOnClickListener
            val local = selected.localFile ?: return@setOnClickListener
            runOperation(
                FileOpRequest.Copy(
                    from = local.toVfsPath(),
                    toDir = currentDirFile().toVfsPath(),
                    conflict = ConflictStrategy.AUTO_RENAME
                )
            )
        }

        actionMoveParentButton.setOnClickListener {
            if (browseSource != BrowseSource.LOCAL) {
                Toast.makeText(requireContext(), getString(R.string.files_status_action_not_supported), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selected = requireLocalSelection() ?: return@setOnClickListener
            val local = selected.localFile ?: return@setOnClickListener
            val parentDir = currentDirFile().parentFile
            if (parentDir == null || !parentDir.exists() || !parentDir.isDirectory) {
                updateStatus(getString(R.string.files_status_done), isError = true)
                return@setOnClickListener
            }
            runOperation(
                FileOpRequest.Move(
                    from = local.toVfsPath(),
                    toDir = parentDir.toVfsPath(),
                    conflict = ConflictStrategy.AUTO_RENAME
                )
            )
        }
        actionNewSessionButton.setOnClickListener {
            val selected = selectedEntry
            if (selected == null || !selected.isDirectory) {
                Toast.makeText(requireContext(), getString(R.string.files_select_folder_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val targetPath = selected.localFile?.absolutePath ?: selected.ftpPath
                    ?: selected.smbPath
                if (targetPath.isNullOrBlank()) {
                    Toast.makeText(requireContext(), getString(R.string.files_select_folder_first), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val sessionName = selected.name.ifBlank { targetPath }
                val sessionId = folderSessionRepository.add(sessionName, targetPath)
                val networkConfigId = if (browseSource != BrowseSource.LOCAL) {
                    currentSessionId?.let { sessionSourceStore.getNetworkConfigId(it) }
                } else {
                    null
                }
                sessionSourceStore.setNetworkConfigId(sessionId, networkConfigId)
                currentSessionStore.set(sessionId)
                updateStatus(getString(R.string.files_session_created), isError = false)
            }
        }

        operationStatusLabel.text = getString(R.string.files_status_idle)
        viewLifecycleOwner.lifecycleScope.launch {
            ensureDefaultSession()
            openFromNetworkIfNeeded()
            openFromPathIfNeeded()
            openFromSafTreeIfNeeded()
            val active = currentSessionStore.get()
            if (active != null) {
                switchToSession(active)
            } else {
                loadEntries()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            currentSessionStore.observe().collect { sessionId ->
                if (sessionId != null && sessionId != currentSessionId) {
                    switchToSession(sessionId)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Permission Required")
                    .setMessage("This app needs access to all files to function properly. Please grant the permission.")
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${requireContext().packageName}")
                        startActivity(intent)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    fun navigateUp(): Boolean {
        if (browseSource == BrowseSource.FTP) {
            val parent = ftpParentPath(ftpCurrentPath)
            if (parent == ftpCurrentPath || (ftpCurrentPath == "/" && parent == "/")) {
                return false
            }
            ftpCurrentPath = parent
            selectedEntry = null
            loadEntries()
            persistCurrentDir()
            return true
        }
        if (browseSource == BrowseSource.SMB) {
            val parent = smbParentPath(smbCurrentPath)
            if (parent == smbCurrentPath || (smbCurrentPath == "/" && parent == "/")) {
                return false
            }
            smbCurrentPath = parent
            selectedEntry = null
            loadEntries()
            persistCurrentDir()
            return true
        }
        val current = currentDirFile()
        val parent = current.parentFile
        if (parent != null && parent.exists() && parent.isDirectory) {
            // Check if we are at the root of the allowed scope?
            // For now, standard file system up.
            // If current is root (e.g. /), parent might be null.
            if (parent.listFiles() == null) {
                 // Cannot access parent, treat as root reached?
                 return false
            }
            currentDir = parent
            selectedEntry = null
            loadEntries()
            persistCurrentDir()
            return true
        }
        return false
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    private fun loadEntries() {
        if (browseSource == BrowseSource.FTP) {
            loadFtpEntries()
            return
        }
        if (browseSource == BrowseSource.SMB) {
            loadSmbEntries()
            return
        }
        if (currentDir == null) {
            currentDir = requireContext().filesDir.parentFile ?: requireContext().filesDir
        }
        val dir = currentDirFile()
        currentDirLabel.text = buildCurrentDirText(dir)
        val listed = dir.listFiles()?.toList().orEmpty()
        val displayable = listed
            .filter { it.isDirectory || it.hasSupportedReaderExtension() }
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
        allEntries.clear()
        allEntries.addAll(displayable)
        displayTitleByPath.clear()
        setLocalActionButtonsEnabled(true)
        actionNewSessionButton.isEnabled = true
        actionCreateButton.text = getString(R.string.action_new_folder)
        renderEntries()
        refreshTitlesAsync()
    }

    private fun renderEntries() {
        val query = queryInput.text?.toString()?.trim().orEmpty().lowercase(Locale.getDefault())
        val filtered = allEntries.filter {
            query.isBlank() || it.name.lowercase(Locale.getDefault()).contains(query)
        }
        val sorted = when (sortSpinner.selectedItemPosition) {
            0 -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
            1 -> filtered.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
            2 -> filtered.sortedByDescending { it.modifiedEpochMs ?: 0L }
            3 -> filtered.sortedByDescending { it.sizeBytes }
            else -> filtered
        }
        displayedEntries.clear()
        displayedEntries.addAll(sorted)
        adapter.notifyDataSetChanged()
    }

    private fun File.hasSupportedReaderExtension(): Boolean {
        if (isDirectory) {
            return true
        }
        val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
        return ext in supportedExtensions
    }

    private fun BrowserEntry.isSelected(selected: BrowserEntry?): Boolean {
        val selectedPath = selected?.localFile?.absolutePath ?: selected?.ftpPath ?: selected?.smbPath
        val currentPath = localFile?.absolutePath ?: ftpPath ?: smbPath
        return selectedPath != null && selectedPath == currentPath
    }

    private fun BrowserEntry.pathKey(): String? = localFile?.absolutePath ?: ftpPath ?: smbPath

    private fun currentDirFile(): File = currentDir ?: requireContext().filesDir

    private fun requireLocalSelection(): BrowserEntry? {
        if (browseSource != BrowseSource.LOCAL) {
            Toast.makeText(requireContext(), getString(R.string.files_status_ftp_read_only), Toast.LENGTH_SHORT).show()
            return null
        }
        val selected = selectedEntry
        val local = selected?.localFile
        if (selected == null || local == null || !local.exists()) {
            Toast.makeText(requireContext(), getString(R.string.files_select_item_first), Toast.LENGTH_SHORT).show()
            return null
        }
        return selected
    }

    private fun promptText(
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

    private fun runOperation(request: FileOpRequest) {
        if (operationRunning) {
            return
        }
        operationRunning = true
        setOperationButtonsEnabled(false)
        operationProgress.visibility = View.VISIBLE
        operationProgress.isIndeterminate = true
        operationProgress.progress = 0
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                executeFileOpUseCase.execute(request).collect { state ->
                    when (state) {
                        is FileOpState.Started -> {
                            updateStatus(getString(R.string.files_status_working), isError = false)
                            operationProgress.isIndeterminate = true
                        }
                        is FileOpState.Progress -> {
                            if (state.total > 0) {
                                operationProgress.isIndeterminate = false
                                val total = state.total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                val current = state.current.coerceAtMost(total.toLong()).toInt()
                                operationProgress.max = total
                                operationProgress.progress = current
                            } else {
                                operationProgress.isIndeterminate = true
                            }
                        }
                        is FileOpState.Success -> {
                            updateStatus(getString(R.string.files_status_done), isError = false)
                            val resultPath = state.resultPath
                            if (resultPath is VfsPath.LocalFile) {
                                val file = File(resultPath.filePath)
                                selectedEntry = BrowserEntry(
                                    localFile = file,
                                    name = file.name,
                                    isDirectory = file.isDirectory,
                                    sizeBytes = if (file.isDirectory) 0L else file.length(),
                                    modifiedEpochMs = file.lastModified(),
                                    modifiedText = null
                                )
                            }
                        }
                        is FileOpState.Error -> {
                            val message = state.error.message ?: state.error.javaClass.simpleName
                            updateStatus(message, isError = true)
                        }
                    }
                }
            } finally {
                operationRunning = false
                setOperationButtonsEnabled(true)
                operationProgress.visibility = View.GONE
                loadEntries()
                persistCurrentDir()
            }
        }
    }

    private suspend fun ensureDefaultSession() {
        val sessions = folderSessionRepository.getAll()
        if (sessions.isEmpty()) {
            val root = defaultRootDir().absolutePath
            val id = folderSessionRepository.add("Default", root)
            currentSessionStore.set(id)
            return
        }
        if (currentSessionStore.get() == null) {
            currentSessionStore.set(sessions.first().id)
        }
    }

    private suspend fun switchToSession(sessionId: Long) {
        val session = folderSessionRepository.getById(sessionId) ?: return
        currentSessionId = sessionId
        currentSessionStore.set(sessionId)
        val linkedNetworkConfig = sessionSourceStore.getNetworkConfigId(sessionId)
            ?.let { networkConfigRepository.getById(it) }
        currentNetworkLabel = linkedNetworkConfig?.let { "${it.protocol.name}://${it.host}" }
        ftpConfig = null
        smbConfig = null
        browseSource = BrowseSource.LOCAL
        if (linkedNetworkConfig?.protocol == NetworkProtocol.FTP) {
            browseSource = BrowseSource.FTP
            ftpConfig = linkedNetworkConfig
            ftpCurrentPath = normalizeFtpPath(session.currentPath.ifBlank { linkedNetworkConfig.defaultPath })
            selectedEntry = null
            loadEntries()
            return
        }
        if (linkedNetworkConfig?.protocol == NetworkProtocol.SMB) {
            browseSource = BrowseSource.SMB
            smbConfig = linkedNetworkConfig
            smbCurrentPath = normalizeSmbPath(session.currentPath.ifBlank { linkedNetworkConfig.defaultPath })
            selectedEntry = null
            loadEntries()
            return
        }
        val preferred = File(session.currentPath)
        currentDir = if (preferred.exists() && preferred.isDirectory) {
            preferred
        } else {
            File(session.rootPath).takeIf { it.exists() && it.isDirectory } ?: defaultRootDir()
        }
        selectedEntry = null
        loadEntries()
    }

    private suspend fun openFromNetworkIfNeeded() {
        val networkConfigId = initialNetworkConfigId ?: return
        val config = networkConfigRepository.getById(networkConfigId) ?: return
        val sessionName = "${config.protocol.name}: ${config.name}"
        val initialPath = when (config.protocol) {
            NetworkProtocol.FTP -> normalizeFtpPath(config.defaultPath)
            NetworkProtocol.SMB -> normalizeSmbPath(config.defaultPath)
        }
        val sessionId = folderSessionRepository.add(sessionName, initialPath)
        sessionSourceStore.setNetworkConfigId(sessionId, config.id)
        currentSessionStore.set(sessionId)
        initialNetworkConfigId = null
    }

    private suspend fun openFromPathIfNeeded() {
        val path = initialStartPath ?: return
        val directory = File(path)
        val active = currentSessionStore.get() ?: return
        val hasNetworkBinding = sessionSourceStore.getNetworkConfigId(active) != null
        if (hasNetworkBinding) {
            folderSessionRepository.updateCurrentDir(active, path)
        } else if (directory.exists() && directory.isDirectory) {
            folderSessionRepository.updateCurrentDir(active, directory.absolutePath)
        } else {
            updateStatus(getString(R.string.files_status_invalid_start_path), isError = true)
        }
        initialStartPath = null
    }

    private suspend fun openFromSafTreeIfNeeded() {
        val treeUriText = initialSafTreeUri ?: return
        val uri = Uri.parse(treeUriText)
        val active = currentSessionStore.get() ?: return
        val resolvedPath = resolveSafTreeToLocalPath(uri)
        if (resolvedPath == null) {
            updateStatus(getString(R.string.files_status_invalid_start_path), isError = true)
        } else {
            val directory = File(resolvedPath)
            if (directory.exists() && directory.isDirectory) {
                folderSessionRepository.updateCurrentDir(active, directory.absolutePath)
            } else {
                updateStatus(getString(R.string.files_status_invalid_start_path), isError = true)
            }
        }
        initialSafTreeUri = null
    }

    private fun resolveSafTreeToLocalPath(uri: Uri): String? {
        return runCatching {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            val split = treeDocId.split(":", limit = 2)
            if (split.size != 2) {
                null
            } else {
                val volume = split[0]
                val relative = split[1].trim('/')
                val base = if (volume.equals("primary", ignoreCase = true)) {
                    "/storage/emulated/0"
                } else {
                    "/storage/$volume"
                }
                if (relative.isBlank()) base else "$base/$relative"
            }
        }.getOrNull()
    }

    private fun buildCurrentDirText(dir: File): String {
        if (browseSource == BrowseSource.FTP) {
            val network = currentNetworkLabel ?: "FTP"
            return getString(R.string.files_current_dir_network_template, network, ftpCurrentPath)
        }
        if (browseSource == BrowseSource.SMB) {
            val network = currentNetworkLabel ?: "SMB"
            return getString(R.string.files_current_dir_network_template, network, smbCurrentPath)
        }
        val network = currentNetworkLabel ?: return dir.absolutePath
        return getString(R.string.files_current_dir_network_template, network, dir.absolutePath)
    }

    private fun persistCurrentDir() {
        val sessionId = currentSessionId ?: return
        if (browseSource == BrowseSource.FTP) {
            val path = ftpCurrentPath
            viewLifecycleOwner.lifecycleScope.launch {
                folderSessionRepository.updateCurrentDir(sessionId, path)
            }
            return
        }
        if (browseSource == BrowseSource.SMB) {
            val path = smbCurrentPath
            viewLifecycleOwner.lifecycleScope.launch {
                folderSessionRepository.updateCurrentDir(sessionId, path)
            }
            return
        }
        val dir = currentDir ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            folderSessionRepository.updateCurrentDir(sessionId, dir.absolutePath)
        }
    }

    private fun defaultRootDir(): File = requireContext().filesDir.parentFile ?: requireContext().filesDir

    private fun setOperationButtonsEnabled(enabled: Boolean) {
        actionUpButton.isEnabled = enabled
        actionCreateButton.isEnabled = enabled
        actionRenameButton.isEnabled = enabled
        actionDeleteButton.isEnabled = enabled
        actionDuplicateButton.isEnabled = enabled
        actionMoveParentButton.isEnabled = enabled
        actionNewSessionButton.isEnabled = enabled
    }

    private fun setLocalActionButtonsEnabled(enabled: Boolean) {
        actionCreateButton.isEnabled = enabled
        actionRenameButton.isEnabled = enabled
        actionDeleteButton.isEnabled = enabled
        actionDuplicateButton.isEnabled = enabled
        actionMoveParentButton.isEnabled = enabled
    }

    private fun updateStatus(value: String, isError: Boolean) {
        operationStatusLabel.text = value
        val colorRes = if (isError) android.R.color.holo_red_dark else android.R.color.black
        operationStatusLabel.setTextColor(resources.getColor(colorRes, null))

        if (isError) {
            operationStatusLabel.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Error Details")
                    .setMessage(value)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton("Copy") { _, _ ->
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Error Message", value)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        } else {
            operationStatusLabel.setOnClickListener(null)
            operationStatusLabel.isClickable = false
        }
    }

    private fun loadFtpEntries() {
        val config = ftpConfig
        if (config == null) {
            updateStatus(getString(R.string.files_status_invalid_start_path), isError = true)
            return
        }
        setLocalActionButtonsEnabled(false)
        actionCreateButton.isEnabled = true
        actionCreateButton.text = getString(R.string.action_upload_file)
        titleRefreshJob?.cancel()
        displayTitleByPath.clear()
        updateStatus(getString(R.string.files_status_ftp_loading), isError = false)
        currentDirLabel.text = buildCurrentDirText(defaultRootDir())
        val token = ++ftpLoadToken
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { fetchFtpEntries(config, ftpCurrentPath) }
            if (!isAdded || token != ftpLoadToken) {
                return@launch
            }
            result.onSuccess { entries ->
                allEntries.clear()
                allEntries.addAll(entries)
                renderEntries()
                updateStatus(getString(R.string.files_status_done), isError = false)
            }.onFailure { error ->
                allEntries.clear()
                displayedEntries.clear()
                adapter.clear()
                adapter.notifyDataSetChanged()
                updateStatus(formatNetworkError(error, NetworkProtocol.FTP), isError = true)
            }
        }
    }

    private fun loadSmbEntries() {
        val config = smbConfig
        if (config == null) {
            updateStatus(getString(R.string.files_status_invalid_start_path), isError = true)
            return
        }
        setLocalActionButtonsEnabled(false)
        actionCreateButton.isEnabled = true
        actionRenameButton.isEnabled = true
        actionDeleteButton.isEnabled = true
        actionDuplicateButton.isEnabled = false
        actionMoveParentButton.isEnabled = false
        actionNewSessionButton.isEnabled = true
        actionCreateButton.text = getString(R.string.action_new_folder)
        titleRefreshJob?.cancel()
        displayTitleByPath.clear()
        updateStatus(getString(R.string.files_status_smb_loading), isError = false)
        currentDirLabel.text = buildCurrentDirText(defaultRootDir())
        val token = ++ftpLoadToken
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { fetchSmbEntries(config, smbCurrentPath) }
            if (!isAdded || token != ftpLoadToken) {
                return@launch
            }
            result.onSuccess { entries ->
                allEntries.clear()
                allEntries.addAll(entries)
                renderEntries()
                updateStatus(getString(R.string.files_status_done), isError = false)
            }.onFailure { error ->
                allEntries.clear()
                displayedEntries.clear()
                adapter.clear()
                adapter.notifyDataSetChanged()
                updateStatus(formatNetworkError(error, NetworkProtocol.SMB), isError = true)
            }
        }
    }

    private suspend fun fetchSmbEntries(config: NetworkConfigEntity, path: String): List<BrowserEntry> = withContext(Dispatchers.IO) {
        val dir = SmbFile(buildSmbDirUrl(config, path), smbContext(config))
        val children = dir.listFiles()?.toList().orEmpty()
        val entries = children.map { child ->
            val resolvedName = child.name.trimEnd('/').ifBlank { child.canonicalPath.substringAfterLast('/').trimEnd('/') }
            val isDirectory = child.isDirectory
            val childPath = joinSmbPath(path, resolvedName)
            BrowserEntry(
                localFile = null,
                ftpPath = null,
                smbPath = childPath,
                name = resolvedName,
                isDirectory = isDirectory,
                sizeBytes = if (isDirectory) 0L else child.length(),
                modifiedEpochMs = child.lastModified(),
                modifiedText = null
            )
        }
        val folders = entries.filter { it.isDirectory }.sortedBy { it.name.lowercase(Locale.getDefault()) }
        val files = entries
            .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase(Locale.getDefault()) in supportedExtensions }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
        folders + files
    }

    private fun createSmbFolder(name: String) {
        val config = smbConfig ?: return
        runNetworkSmbOperation {
            withContext(Dispatchers.IO) {
                val targetPath = joinSmbPath(smbCurrentPath, name)
                val target = SmbFile(buildSmbDirUrl(config, targetPath), smbContext(config))
                if (!target.exists()) {
                    target.mkdir()
                }
            }
        }
    }

    private fun promptSmbCreateAction() {
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
                        createSmbFolder(folderName)
                    }
                } else {
                    smbUploadLauncher.launch(arrayOf("*/*"))
                }
            }
            .show()
    }

    private fun renameSmbEntry(entry: BrowserEntry, newName: String) {
        val config = smbConfig ?: return
        val oldPath = entry.smbPath ?: return
        runNetworkSmbOperation {
            withContext(Dispatchers.IO) {
                val parent = smbParentPath(oldPath)
                val newPath = joinSmbPath(parent, newName)
                val from = SmbFile(
                    if (entry.isDirectory) buildSmbDirUrl(config, oldPath) else buildSmbFileUrl(config, oldPath),
                    smbContext(config)
                )
                val to = SmbFile(
                    if (entry.isDirectory) buildSmbDirUrl(config, newPath) else buildSmbFileUrl(config, newPath),
                    smbContext(config)
                )
                from.renameTo(to)
            }
        }
    }

    private fun deleteSmbEntry(entry: BrowserEntry) {
        val config = smbConfig ?: return
        val targetPath = entry.smbPath ?: return
        runNetworkSmbOperation {
            withContext(Dispatchers.IO) {
                val root = SmbFile(
                    if (entry.isDirectory) buildSmbDirUrl(config, targetPath) else buildSmbFileUrl(config, targetPath),
                    smbContext(config)
                )
                deleteSmbRecursively(root)
            }
        }
    }

    private fun deleteSmbRecursively(target: SmbFile) {
        if (target.isDirectory) {
            target.listFiles()?.forEach { child ->
                deleteSmbRecursively(child)
            }
        }
        target.delete()
    }

    private suspend fun fetchFtpEntries(config: NetworkConfigEntity, path: String): List<BrowserEntry> = withContext(Dispatchers.IO) {
        val url = URL(buildFtpUrl(config, path, "d"))
        // Use ISO-8859-1 to preserve raw bytes for manual decoding
        val lines = url.openStream().bufferedReader(Charsets.ISO_8859_1).use { it.readLines() }
        val entries = lines.mapNotNull { parseFtpLine(path, it) }
        val folders = entries.filter { it.isDirectory }.sortedBy { it.name.lowercase(Locale.getDefault()) }
        val files = entries
            .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase(Locale.getDefault()) in supportedExtensions }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
        folders + files
    }

    private fun parseFtpLine(basePath: String, rawLine: String): BrowserEntry? {
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith("total ")) {
            return null
        }
        
        // Try Unix style first
        // -rw-r--r-- 1 user group 1234 Jan 01 12:00 filename
        val unixEntry = parseUnixStyle(basePath, line)
        if (unixEntry != null) {
            return unixEntry
        }

        // Try DOS style
        // 01-01-24 12:00PM <DIR> filename
        // 01-01-24 12:00PM 1234 filename
        val dosEntry = parseDosStyle(basePath, line)
        if (dosEntry != null) {
            return dosEntry
        }

        return null
    }

    private fun parseUnixStyle(basePath: String, line: String): BrowserEntry? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 8) {
             return null 
        }

        // Check permission flag (d or - or l)
        if (!parts[0].startsWith("d") && !parts[0].startsWith("-") && !parts[0].startsWith("l")) {
             return null
        }
        val isDir = parts[0].startsWith("d")
        // Ignore symlinks for now or treat as files? 
        // If it's 'l', we might want to follow or show as file.
        // Let's treat 'l' as file for now, or just ignore to be safe if we can't resolve.
        // But user said "all files not seen".
        
        // Find date parts.
        val months = setOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        var monthIndex = -1
        
        // Scan for month. Usually after permissions, links, owner, group, size.
        // Min index for month is 5 (perms, links, owner, group, size).
        // If group missing: 4.
        for (i in 3 until parts.size - 3) {
            if (parts[i] in months) {
                monthIndex = i
                break
            }
        }

        val size: Long
        val nameStartIndex: Int
        
        if (monthIndex != -1) {
            size = parts.getOrNull(monthIndex - 1)?.toLongOrNull() ?: 0L
            nameStartIndex = monthIndex + 3
        } else {
            // Fallback: assume strict column layout if month not found
            // perms links owner group size month day time name
            // 0     1     2     3     4    5     6   7    8
            size = parts.getOrNull(4)?.toLongOrNull() ?: 0L
            nameStartIndex = 8
        }

        if (nameStartIndex >= parts.size) {
            return null
        }

        // Reconstruct name
        val rawNameString = parts.subList(nameStartIndex, parts.size).joinToString(" ")
        
        // Decoding logic
        val rawBytes = rawNameString.toByteArray(Charsets.ISO_8859_1)
        var name = decodeFtpName(rawBytes)

        if (name == "." || name == "..") return null
        
        val childPath = joinFtpPath(basePath, name)
        
        val dateStr = if (monthIndex != -1 && monthIndex + 2 < parts.size) {
            parts.subList(monthIndex, monthIndex + 3).joinToString(" ")
        } else ""

        return BrowserEntry(
            localFile = null,
            ftpPath = childPath,
            name = name,
            isDirectory = isDir,
            sizeBytes = size,
            modifiedEpochMs = null,
            modifiedText = dateStr,
            rawNameBytes = rawBytes
        )
    }

    private fun parseDosStyle(basePath: String, line: String): BrowserEntry? {
        // 02-11-20  11:42PM       <DIR>          Folder
        // 02-11-20  11:42PM                  123 File.txt
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 4) {
            return null
        }
        
        // Check date format loosely: MM-DD-YY
        if (!parts[0].contains("-")) {
            return null
        }
        
        // Check time format loosely: contain :
        if (!parts[1].contains(":")) {
            return null
        }

        val isDir = parts[2].equals("<DIR>", ignoreCase = true)
        val size = if (isDir) 0L else parts[2].toLongOrNull() ?: 0L
        
        val nameStartIndex = 3
        if (nameStartIndex >= parts.size) return null
        
        val rawNameString = parts.subList(nameStartIndex, parts.size).joinToString(" ")
        
        // Decoding
        val rawBytes = rawNameString.toByteArray(Charsets.ISO_8859_1)
        var name = decodeFtpName(rawBytes)
        
        if (name == "." || name == "..") return null

        val childPath = joinFtpPath(basePath, name)
        val dateStr = "${parts[0]} ${parts[1]}"

        return BrowserEntry(
            localFile = null,
            ftpPath = childPath,
            name = name,
            isDirectory = isDir,
            sizeBytes = size,
            modifiedEpochMs = null,
            modifiedText = dateStr,
            rawNameBytes = rawBytes
        )
    }

    private fun decodeFtpName(bytes: ByteArray): String {
        // 1. Get encoding from config
        val encoding = ftpConfig?.encoding
        if (!encoding.isNullOrBlank() && !encoding.equals("Auto", ignoreCase = true)) {
            try {
                return String(bytes, java.nio.charset.Charset.forName(encoding))
            } catch (e: Exception) {
                // Fallback if invalid charset
            }
        }

        // 2. Try UTF-8
        try {
             // Use decoder to strict check
            val decoder = Charsets.UTF_8.newDecoder()
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            // Not valid UTF-8
        }
        
        // 3. Try GBK (Common for Chinese)
        try {
            return String(bytes, java.nio.charset.Charset.forName("GBK"))
        } catch (e: Exception) {
            // Ignore
        }
        
        // 4. Fallback to ISO-8859-1 (original)
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun openFtpFile(entry: BrowserEntry) {
        val config = ftpConfig ?: return
        val remotePath = entry.ftpPath ?: return
        updateStatus(getString(R.string.files_status_ftp_downloading), isError = false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                downloadFtpToLocal(config, remotePath, entry.name)
            }
            result.onSuccess { local ->
                updateStatus(getString(R.string.files_status_done), isError = false)
                (activity as? MainActivity)?.showReaderModeWithPath(local.absolutePath)
            }.onFailure { error ->
                updateStatus(formatNetworkError(error, NetworkProtocol.FTP), isError = true)
            }
        }
    }

    private fun openSmbFile(entry: BrowserEntry) {
        val config = smbConfig ?: return
        val remotePath = entry.smbPath ?: return
        updateStatus(getString(R.string.files_status_smb_downloading), isError = false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                downloadSmbToLocal(config, remotePath, entry.name)
            }
            result.onSuccess { local ->
                updateStatus(getString(R.string.files_status_done), isError = false)
                (activity as? MainActivity)?.showReaderModeWithPath(local.absolutePath)
            }.onFailure { error ->
                updateStatus(formatNetworkError(error, NetworkProtocol.SMB), isError = true)
            }
        }
    }

    private suspend fun downloadFtpToLocal(config: NetworkConfigEntity, remotePath: String, displayName: String): File = withContext(Dispatchers.IO) {
        val ftpCacheDir = requireContext().cacheDir.resolve("ftp_open")
        if (!ftpCacheDir.exists()) {
            ftpCacheDir.mkdirs()
        }
        val ext = displayName.substringAfterLast('.', "").ifBlank { "bin" }
        val target = File(ftpCacheDir, "ftp_${System.currentTimeMillis()}_${displayName.hashCode()}.$ext")
        val url = URL(buildFtpUrl(config, remotePath, "i"))
        url.openStream().use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output, 64 * 1024)
            }
        }
        target
    }

    private suspend fun downloadSmbToLocal(config: NetworkConfigEntity, remotePath: String, displayName: String): File = withContext(Dispatchers.IO) {
        val cacheDir = requireContext().cacheDir.resolve("smb_open")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val ext = displayName.substringAfterLast('.', "").ifBlank { "bin" }
        val target = File(cacheDir, "smb_${System.currentTimeMillis()}_${displayName.hashCode()}.$ext")
        val source = SmbFile(buildSmbFileUrl(config, remotePath), smbContext(config))
        source.inputStream.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output, 64 * 1024)
            }
        }
        target
    }

    private fun normalizeFtpPath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isBlank()) {
            return "/"
        }
        val normalized = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return normalized.replace(Regex("/+"), "/")
    }

    private fun joinFtpPath(parent: String, child: String): String {
        val p = normalizeFtpPath(parent).trimEnd('/')
        val c = child.trimStart('/')
        return normalizeFtpPath("$p/$c")
    }

    private fun ftpParentPath(path: String): String {
        val normalized = normalizeFtpPath(path).trimEnd('/')
        if (normalized.isBlank() || normalized == "/") {
            return "/"
        }
        val idx = normalized.lastIndexOf('/')
        if (idx <= 0) {
            return "/"
        }
        return normalized.substring(0, idx)
    }

    private fun normalizeSmbPath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isBlank()) {
            return "/"
        }
        val normalized = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return normalized.replace(Regex("/+"), "/")
    }

    private fun joinSmbPath(parent: String, child: String): String {
        val p = normalizeSmbPath(parent).trimEnd('/')
        val c = child.trimStart('/')
        return normalizeSmbPath("$p/$c")
    }

    private fun smbParentPath(path: String): String {
        val normalized = normalizeSmbPath(path).trimEnd('/')
        if (normalized.isBlank() || normalized == "/") {
            return "/"
        }
        val idx = normalized.lastIndexOf('/')
        if (idx <= 0) {
            return "/"
        }
        return normalized.substring(0, idx)
    }

    private fun buildFtpUrl(config: NetworkConfigEntity, path: String, type: String): String {
        val user = config.username.trim().ifBlank { "anonymous" }
        val pass = config.password.ifBlank { "anonymous@" }
        val encodedUser = URLEncoder.encode(user, "UTF-8").replace("+", "%20")
        val encodedPass = URLEncoder.encode(pass, "UTF-8").replace("+", "%20")
        val normalized = normalizeFtpPath(path)
        val encodedPath = normalized
            .split("/")
            .joinToString("/") { segment ->
                if (segment.isBlank()) "" else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
        
        // If listing directory (type=d), ensure trailing slash to force directory listing behavior
        val finalPath = if (type == "d" && !encodedPath.endsWith("/")) "$encodedPath/" else encodedPath
        
        return "ftp://$encodedUser:$encodedPass@${config.host}:${config.port}$finalPath;type=$type"
    }

    private fun buildSmbDirUrl(config: NetworkConfigEntity, path: String): String {
        val encodedPath = normalizeSmbPath(path)
            .split("/")
            .joinToString("/") { segment ->
                if (segment.isBlank()) "" else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
        val url = "smb://${config.host}:${config.port}$encodedPath"
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun buildSmbFileUrl(config: NetworkConfigEntity, path: String): String {
        val encodedPath = normalizeSmbPath(path)
            .split("/")
            .joinToString("/") { segment ->
                if (segment.isBlank()) "" else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
        return "smb://${config.host}:${config.port}$encodedPath"
    }

    private fun smbContext(config: NetworkConfigEntity): CIFSContext {
        val user = config.username.trim().ifBlank { "guest" }
        val pass = config.password
        val auth = NtlmPasswordAuthenticator("", user, pass)
        return SingletonContext.getInstance().withCredentials(auth)
    }

    private fun formatNetworkError(error: Throwable, protocol: NetworkProtocol): String {
        val msg = error.message.orEmpty()
        if (protocol == NetworkProtocol.FTP && msg.contains("530")) {
            return getString(R.string.files_status_ftp_auth_failed)
        }
        if (protocol == NetworkProtocol.SMB && (msg.contains("logon failure", ignoreCase = true) || msg.contains("access denied", ignoreCase = true))) {
            return getString(R.string.files_status_smb_auth_failed)
        }
        if (msg.contains("timed out", ignoreCase = true) || msg.contains("connect", ignoreCase = true)) {
            return if (protocol == NetworkProtocol.FTP) {
                getString(R.string.files_status_ftp_connection_failed)
            } else {
                getString(R.string.files_status_smb_connection_failed)
            }
        }
        return msg.ifBlank { getString(R.string.files_status_invalid_start_path) }
    }

    private fun uploadDocumentToFtp(uri: Uri) {
        val config = ftpConfig ?: return
        updateStatus(getString(R.string.files_status_ftp_uploading), isError = false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val fileName = resolveUploadFileName(uri)
                    val targetPath = joinFtpPath(ftpCurrentPath, fileName)
                    val targetUrl = URL(buildFtpUrl(config, targetPath, "i"))
                    val connection = targetUrl.openConnection().apply { doOutput = true }
                    requireContext().contentResolver.openInputStream(uri).use { input ->
                        if (input == null) {
                            error("open input stream failed")
                        }
                        connection.getOutputStream().use { output ->
                            input.copyTo(output, 64 * 1024)
                        }
                    }
                }
            }
            result.onSuccess {
                updateStatus(getString(R.string.files_status_ftp_uploaded), isError = false)
                loadEntries()
            }.onFailure { error ->
                updateStatus(formatNetworkError(error, NetworkProtocol.FTP), isError = true)
            }
        }
    }

    private fun uploadDocumentToSmb(uri: Uri) {
        val config = smbConfig ?: return
        updateStatus(getString(R.string.files_status_smb_uploading), isError = false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val fileName = resolveUploadFileName(uri)
                    val targetPath = joinSmbPath(smbCurrentPath, fileName)
                    val target = SmbFile(buildSmbFileUrl(config, targetPath), smbContext(config))
                    requireContext().contentResolver.openInputStream(uri).use { input ->
                        if (input == null) {
                            error("open input stream failed")
                        }
                        target.outputStream.use { output ->
                            input.copyTo(output, 64 * 1024)
                        }
                    }
                }
            }
            result.onSuccess {
                updateStatus(getString(R.string.files_status_smb_uploaded), isError = false)
                loadEntries()
            }.onFailure { error ->
                updateStatus(formatNetworkError(error, NetworkProtocol.SMB), isError = true)
            }
        }
    }

    private fun runNetworkSmbOperation(block: suspend () -> Unit) {
        if (operationRunning) {
            return
        }
        operationRunning = true
        setOperationButtonsEnabled(false)
        operationProgress.visibility = View.VISIBLE
        operationProgress.isIndeterminate = true
        operationProgress.progress = 0
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
                updateStatus(getString(R.string.files_status_done), isError = false)
            } catch (error: Throwable) {
                updateStatus(formatNetworkError(error, NetworkProtocol.SMB), isError = true)
            } finally {
                operationRunning = false
                setOperationButtonsEnabled(true)
                operationProgress.visibility = View.GONE
                loadEntries()
                persistCurrentDir()
            }
        }
    }

    private fun resolveUploadFileName(uri: Uri): String {
        val resolver = requireContext().contentResolver
        val fromCursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        val raw = fromCursor ?: uri.lastPathSegment ?: "upload_${System.currentTimeMillis()}.bin"
        return raw.replace("/", "_").ifBlank { "upload_${System.currentTimeMillis()}.bin" }
    }

    private fun addEntryToFavorites(entry: BrowserEntry) {
        val favoritePath = when (browseSource) {
            BrowseSource.LOCAL -> entry.localFile?.absolutePath
            BrowseSource.FTP -> {
                val config = ftpConfig ?: return
                val remote = entry.ftpPath ?: return
                val encodedPath = normalizeFtpPath(remote)
                val user = config.username.trim().ifBlank { "anonymous" }
                val pass = config.password.ifBlank { "anonymous@" }
                val encodedUser = URLEncoder.encode(user, "UTF-8").replace("+", "%20")
                val encodedPass = URLEncoder.encode(pass, "UTF-8").replace("+", "%20")
                "ftp://$encodedUser:$encodedPass@${config.host}:${config.port}$encodedPath"
            }
            BrowseSource.SMB -> {
                val config = smbConfig ?: return
                val remote = entry.smbPath ?: return
                "smb://${config.host}:${config.port}${normalizeSmbPath(remote)}"
            }
        } ?: return
        val sourceType = when (browseSource) {
            BrowseSource.LOCAL -> SourceType.LOCAL
            BrowseSource.FTP -> SourceType.FTP
            BrowseSource.SMB -> SourceType.SMB
        }
        viewLifecycleOwner.lifecycleScope.launch {
            favoritesRepository.addFile(
                parentId = null,
                name = entry.name,
                path = favoritePath,
                sourceType = sourceType
            )
            Toast.makeText(requireContext(), getString(R.string.favorites_added), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEntryDetails(entry: BrowserEntry) {
        val sourceLabel = when (browseSource) {
            BrowseSource.LOCAL -> "LOCAL"
            BrowseSource.FTP -> "FTP"
            BrowseSource.SMB -> "SMB"
        }
        val path = entry.localFile?.absolutePath ?: entry.ftpPath ?: entry.smbPath ?: "-"
        val size = if (entry.isDirectory) "-" else entry.sizeBytes.toString()
        val modified = entry.modifiedText
            ?: entry.modifiedEpochMs?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
            ?: "-"
        val message = "Name: ${entry.name}\nType: ${if (entry.isDirectory) "DIR" else "FILE"}\nPath: $path\nSize: $size\nModified: $modified\nSource: $sourceLabel"
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.files_action_details))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showDiagnosticDialog(entry: BrowserEntry) {
        val rawBytes = entry.rawNameBytes
        if (rawBytes == null) {
            Toast.makeText(requireContext(), "No raw bytes available for this file (not FTP?)", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.append("Raw Hex:\n")
        sb.append(rawBytes.joinToString(" ") { "%02X".format(it) })
        sb.append("\n\n")

        val charsets = listOf(
            "UTF-8",
            "GBK",
            "ISO-8859-1",
            "Big5",
            "Shift_JIS",
            "windows-1251"
        )

        sb.append("Decoding Previews:\n")
        for (csName in charsets) {
            try {
                val decoded = String(rawBytes, java.nio.charset.Charset.forName(csName))
                sb.append("[$csName]: $decoded\n")
            } catch (e: Exception) {
                sb.append("[$csName]: <Error: ${e.message}>\n")
            }
        }
        
        val currentEncoding = ftpConfig?.encoding ?: "Auto"
        sb.append("\nCurrent Config: $currentEncoding")

        val message = sb.toString()

        AlertDialog.Builder(requireContext())
            .setTitle("Encoding Diagnosis")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton("Copy") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Diagnostic Info", message)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun refreshTitlesAsync() {
        if (browseSource != BrowseSource.LOCAL) {
            return
        }
        titleRefreshJob?.cancel()
        val snapshot = allEntries.toList()
        titleRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            val localFiles = snapshot.filter { !it.isDirectory }.mapNotNull { it.localFile }
            for (file in localFiles) {
                val path = file.absolutePath
                val cached = titleCacheRepository.get(path)
                if (cached != null && cached.lastModified == file.lastModified() && cached.title.isNotBlank()) {
                    displayTitleByPath[path] = cached.title
                }
            }
            renderEntries()
            for (file in localFiles) {
                val ext = file.name.substringAfterLast('.', "").lowercase(Locale.getDefault())
                if (ext != "mht" && ext != "mhtml") {
                    continue
                }
                val path = file.absolutePath
                val cached = titleCacheRepository.get(path)
                if (cached != null && cached.lastModified == file.lastModified() && cached.title.isNotBlank()) {
                    continue
                }
                val title = htmlTitleExtractor.extractTitle(
                    source = VfsPath.LocalFile(path),
                    cacheFile = file,
                    fileType = FileType.MHTML,
                    maxBytesToRead = 256L * 1024L
                )?.trim()
                if (!title.isNullOrBlank()) {
                    titleCacheRepository.upsert(
                        TitleCacheEntity(
                            path = path,
                            title = title,
                            lastModified = file.lastModified(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    displayTitleByPath[path] = title
                    renderEntries()
                }
            }
        }
    }

    private fun File.toVfsPath(): VfsPath.LocalFile = VfsPath.LocalFile(absolutePath)
}
