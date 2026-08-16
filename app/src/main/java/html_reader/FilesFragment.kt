package com.html_reader

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import com.html_reader.files.BrowseSource
import com.html_reader.files.BrowserEntry
import com.html_reader.files.FilesFtpCodec
import com.html_reader.files.FilesFtpDiagnosticBuilder
import com.html_reader.files.FilesEntryDetailsBuilder
import com.html_reader.files.FilesErrorFormatter
import com.html_reader.files.FilesFavoritePathBuilder
import com.html_reader.files.FilesLocalEntries
import com.html_reader.files.FilesNetworkGateway
import com.html_reader.files.FilesNavigateUpHelper
import com.html_reader.files.FilesOperationRunner
import com.html_reader.files.FilesOperationUiBinder
import com.html_reader.files.FilesPathHelper
import com.html_reader.files.FilesPermissionHelper
import com.html_reader.files.FilesSessionPlanner
import com.html_reader.files.FilesSortHelper
import com.html_reader.files.FilesStatusUiHelper
import com.html_reader.files.NetworkErrorTexts
import com.html_reader.files.FilesSmbGateway
import com.html_reader.files.FilesStartupHandler
import com.html_reader.files.FilesTitleRefresher
import com.html_reader.files.FilesTransferGateway
import com.html_reader.files.FilesUiBinder
import com.html_reader.files.InitialOpenState
import com.html_reader.files.isSamePathAs
import com.html_reader.files.pathKey
import kotlinx.coroutines.CancellationException
import core.common.DefaultDispatcherProvider
import core.data.repo.FavoritesRepository
import core.data.repo.NetworkConfigRepository
import core.data.repo.TitleCacheRepository
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.FileType
import core.database.entity.enums.NetworkProtocol
import core.database.entity.enums.SourceType
import core.fileops.model.ConflictStrategy
import core.fileops.model.FileOpRequest
import core.fileops.model.FileOpState
import core.fileops.usecase.ExecuteFileOpUseCase
import core.fileops.util.NameConflictResolver
import core.reader.model.OpenRequest
import core.reader.model.OpenState
import core.reader.vm.ReaderViewModel
import core.session.repo.FolderSessionRepository
import core.title.impl.HtmlTitleExtractor
import core.vfs.local.LocalFileSystem
import core.vfs.model.VfsPath
import java.io.File
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilesFragment : Fragment() {
    private lateinit var queryInput: EditText
    private lateinit var sortSpinner: Spinner
    private lateinit var fontSizeSpinner: Spinner
    private lateinit var currentDirLabel: TextView
    private lateinit var operationStatusLabel: TextView
    private lateinit var operationProgress: ProgressBar
    private lateinit var actionUpButton: Button
    private lateinit var actionCreateButton: Button
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
    private lateinit var readerViewModel: ReaderViewModel

    private val allEntries = mutableListOf<BrowserEntry>()
    private val displayedEntries = mutableListOf<BrowserEntry>()
    private var currentDir: File? = null
    private var selectedEntry: BrowserEntry? = null
    private var operationRunning = false
    private var currentSessionId: Long? = null
    private var initialNetworkConfigId: Long? = null
    private var requestedNetworkEntry: Boolean = false
    private var initialStartPath: String? = null
    private var initialSafTreeUri: String? = null
    private var currentNetworkLabel: String? = null
    private var browseSource: BrowseSource = BrowseSource.LOCAL
    private var ftpConfig: NetworkConfigEntity? = null
    private var ftpCurrentPath: String = "/"
    private var smbConfig: NetworkConfigEntity? = null
    private var smbCurrentPath: String = "/"
    private var ftpLoadToken: Long = 0L
    private var ftpResolvedCharset: String? = null
    private var titleRefreshJob: Job? = null
    private lateinit var filesTitleRefresher: FilesTitleRefresher
    private var currentNameTextSizeSp: Float = 16f
    private val supportedExtensions = setOf("mht", "mhtml", "pdf", "html", "htm")
    private val displayTitleByPath = mutableMapOf<String, String>()
    private val ftpDecodeCache = mutableMapOf<String, String>()
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
        private const val FILE_BROWSER_PREFS = "files_browser_settings"
        private const val KEY_NAME_FONT_SIZE_INDEX = "name_font_size_index"
        private const val FONT_INDEX_SMALL = 0
        private const val FONT_INDEX_MEDIUM = 1
        private const val FONT_INDEX_LARGE = 2
        private const val SORT_INDEX_NAME_ASC = 0
        private const val SORT_INDEX_NAME_DESC = 1
        private const val SORT_INDEX_MODIFIED_DESC = 2
        private const val SORT_INDEX_SIZE_DESC = 3
        private const val SORT_INDEX_SIZE_ASC = 4

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
        val ui = FilesUiBinder.bind(view)
        queryInput = ui.queryInput
        sortSpinner = ui.sortSpinner
        fontSizeSpinner = ui.fontSizeSpinner
        currentDirLabel = ui.currentDirLabel
        operationStatusLabel = ui.operationStatusLabel
        operationProgress = ui.operationProgress
        actionUpButton = ui.actionUpButton
        actionCreateButton = ui.actionCreateButton
        listView = ui.listView

        adapter = object : ArrayAdapter<BrowserEntry>(requireContext(), R.layout.item_files_entry, displayedEntries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val itemView = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_files_entry, parent, false)
                val item = getItem(position) ?: return itemView
                val text1 = itemView.findViewById<TextView>(R.id.files_item_name)
                val text2 = itemView.findViewById<TextView>(R.id.files_item_meta)

                val namePart = item.name
                val titlePart = item.pathKey()?.let { displayTitleByPath[it] }

                val typeLabel = if (item.isDirectory) getString(R.string.icon_dir) else getString(R.string.icon_file)
                val sizeLabel = if (item.isDirectory) "" else formatSize(item.sizeBytes)
                val timeLabel = item.modifiedText ?: item.modifiedEpochMs?.let { DateFormat.getDateTimeInstance().format(Date(it)) }.orEmpty()
                val metaPart = buildList {
                    if (!titlePart.isNullOrBlank()) {
                        add("Title: $titlePart")
                    }
                    if (sizeLabel.isNotBlank()) {
                        add(sizeLabel)
                    }
                    if (timeLabel.isNotBlank()) {
                        add(timeLabel)
                    }
                }.joinToString("  •  ")

                val selectedPrefix = if (item.isSamePathAs(selectedEntry)) "▶ " else ""
                
                text1.text = "$selectedPrefix$typeLabel $namePart"
                text1.setTextSize(currentNameTextSizeSp)
                text2.text = metaPart
                return itemView
            }
        }
        listView.adapter = adapter
        listView.isFastScrollEnabled = true

        initDependencies()
        initOpenArguments()

        sortSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.sort_name_asc),
                getString(R.string.sort_name_desc),
                getString(R.string.sort_modified_desc),
                getString(R.string.sort_size_desc),
                getString(R.string.sort_size_asc)
            )
        )

        sortSpinner.setSelection(2, false)
        sortSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                renderEntries()
                val sessionId = currentSessionId
                if (sessionId != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        folderSessionRepository.updateSortOption(sessionId, position)
                    }
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        })

        fontSizeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.files_font_small),
                getString(R.string.files_font_medium),
                getString(R.string.files_font_large)
            )
        )
        val initialFontSizeIndex = readFontSizeIndex()
        applyNameTextSize(initialFontSizeIndex)
        fontSizeSpinner.setSelection(initialFontSizeIndex, false)
        fontSizeSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyNameTextSize(position)
                writeFontSizeIndex(position)
                adapter.notifyDataSetChanged()
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
            
            if (isFile) {
                options.add("Open in new tab")
            }
            options.add(getString(R.string.files_action_add_favorite))
            options.add(getString(R.string.files_action_details))
            
            // Check capability instead of just browseSource
            val isLocal = browseSource == BrowseSource.LOCAL
            val isSmb = browseSource == BrowseSource.SMB && item.smbPath != null
            
            if (isLocal || isSmb) {
                options.add(getString(R.string.action_rename))
                options.add(getString(R.string.action_delete))
            }

            if (browseSource == BrowseSource.FTP) {
                options.add("Diagnose Encoding")
            }

            AlertDialog.Builder(requireContext())
                .setItems(options.toTypedArray()) { _, which ->
                    val selectedOption = options[which]
                    when (selectedOption) {
                        "Open in new tab" -> {
                            if (browseSource == BrowseSource.FTP) {
                                openFtpFile(item, isBackground = true)
                            } else if (browseSource == BrowseSource.SMB) {
                                openSmbFile(item, isBackground = true)
                            } else {
                                val localFile = item.localFile
                                if (localFile != null) {
                                    openFileInBackground(localFile, item.name)
                                }
                            }
                        }
                        getString(R.string.files_action_add_favorite) -> addEntryToFavorites(item)
                        getString(R.string.files_action_details) -> showEntryDetails(item)
                        getString(R.string.action_rename) -> promptRename(item)
                        getString(R.string.action_delete) -> promptDelete(item)
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

        operationStatusLabel.text = getString(R.string.files_status_idle)
        restoreSessionAndLoad()
        observeSessionSwitch()
        ensureStoragePermissionIfNeeded()
    }

    private fun initDependencies() {
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
        filesTitleRefresher = FilesTitleRefresher(titleCacheRepository, htmlTitleExtractor)
        readerViewModel = ReaderRuntime.viewModel(requireContext())
    }

    private fun initOpenArguments() {
        initialNetworkConfigId = arguments?.getLong(ARG_NETWORK_CONFIG_ID)?.takeIf { it > 0L }
        requestedNetworkEntry = initialNetworkConfigId != null
        initialStartPath = arguments?.getString(ARG_START_PATH)?.trim()?.takeIf { it.isNotBlank() }
        initialSafTreeUri = arguments?.getString(ARG_SAF_TREE_URI)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun restoreSessionAndLoad() {
        viewLifecycleOwner.lifecycleScope.launch {
            ensureDefaultSession()
            val updated = FilesStartupHandler.applyInitialOpen(
                state = InitialOpenState(
                    networkConfigId = initialNetworkConfigId,
                    startPath = initialStartPath,
                    safTreeUri = initialSafTreeUri
                ),
                requestedNetworkEntry = requestedNetworkEntry,
                networkConfigRepository = networkConfigRepository,
                folderSessionRepository = folderSessionRepository,
                sessionSourceStore = sessionSourceStore,
                currentSessionStore = currentSessionStore,
                onInvalidStartPath = {
                    updateStatus(getString(R.string.files_status_invalid_start_path), isError = true)
                }
            )
            initialNetworkConfigId = updated.networkConfigId
            initialStartPath = updated.startPath
            initialSafTreeUri = updated.safTreeUri
            val active = currentSessionStore.get()
            if (active != null) switchToSession(active) else loadEntries()
        }
    }

    private fun observeSessionSwitch() {
        viewLifecycleOwner.lifecycleScope.launch {
            currentSessionStore.observe().collect { sessionId ->
                if (sessionId != null && sessionId != currentSessionId) {
                    switchToSession(sessionId)
                }
            }
        }
    }

    private fun ensureStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !checkStoragePermission()) {
            requestStoragePermission()
        }
    }

    private fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return true
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            FilesPermissionHelper.showAllFilesPermissionDialog(requireContext()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${requireContext().packageName}")
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (browseSource == BrowseSource.LOCAL && !checkStoragePermission()) {
            // Optional: check again if needed
        }
    }

    fun navigateUp(): Boolean {
        val plan = FilesNavigateUpHelper.plan(browseSource, currentDirFile(), ftpCurrentPath, smbCurrentPath)
        if (!plan.handled) return false
        if (plan.localAccessDenied) {
            if (!checkStoragePermission()) requestStoragePermission()
            else Toast.makeText(requireContext(), "Cannot access parent directory", Toast.LENGTH_SHORT).show()
            return false
        }
        if (plan.nextFtpPath != null) ftpCurrentPath = plan.nextFtpPath
        if (plan.nextSmbPath != null) smbCurrentPath = plan.nextSmbPath
        if (plan.nextLocalDir != null) currentDir = plan.nextLocalDir
        selectedEntry = null
        loadEntries()
        persistCurrentDir()
        return true
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

    private fun renderEntries() {
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

    private fun matchesQuery(entry: BrowserEntry, query: String): Boolean {
        if (entry.name.lowercase(Locale.getDefault()).contains(query)) {
            return true
        }
        if (browseSource != BrowseSource.FTP) {
            return false
        }
        val raw = entry.rawNameBytes ?: return false
        val charsets = listOfNotNull(
            configuredFtpCharsetName(ftpConfig),
            ftpResolvedCharset,
            "UTF-8",
            "GBK",
            "Big5",
            "Shift_JIS"
        ).distinct()
        return charsets.any { cs ->
            FilesFtpCodec.decodeForSearch(raw, cs, ftpDecodeCache)?.lowercase(Locale.getDefault())?.contains(query) == true
        }
    }

    private fun sortEntriesWithinGroup(entries: List<BrowserEntry>): List<BrowserEntry> {
        return FilesSortHelper.sortEntriesWithinGroup(entries, sortSpinner.selectedItemPosition)
    }

    private fun applyNameTextSize(index: Int) {
        currentNameTextSizeSp = when (index) {
            FONT_INDEX_SMALL -> 14f
            FONT_INDEX_LARGE -> 18f
            else -> 16f
        }
    }

    private fun readFontSizeIndex(): Int {
        val prefs = requireContext().getSharedPreferences(FILE_BROWSER_PREFS, Context.MODE_PRIVATE)
        val value = prefs.getInt(KEY_NAME_FONT_SIZE_INDEX, FONT_INDEX_MEDIUM)
        return when (value) {
            FONT_INDEX_SMALL, FONT_INDEX_MEDIUM, FONT_INDEX_LARGE -> value
            else -> FONT_INDEX_MEDIUM
        }
    }

    private fun writeFontSizeIndex(index: Int) {
        val normalized = when (index) {
            FONT_INDEX_SMALL, FONT_INDEX_MEDIUM, FONT_INDEX_LARGE -> index
            else -> FONT_INDEX_MEDIUM
        }
        requireContext()
            .getSharedPreferences(FILE_BROWSER_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_NAME_FONT_SIZE_INDEX, normalized)
            .apply()
    }

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
        FilesOperationUiBinder.onBeforeRun(operationProgress, ::setOperationButtonsEnabled)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                FilesOperationRunner.run(
                    useCase = executeFileOpUseCase,
                    request = request,
                    onStarted = {
                        updateStatus(getString(R.string.files_status_working), isError = false)
                        operationProgress.isIndeterminate = true
                    },
                    onProgress = { progress ->
                        FilesOperationUiBinder.onProgress(operationProgress, progress)
                    },
                    onSuccessPath = { resultPath ->
                        updateStatus(getString(R.string.files_status_done), isError = false)
                        selectResultPath(resultPath)
                    },
                    onError = { message ->
                        updateStatus(message, isError = true)
                    }
                )
            } catch (t: Throwable) {
                updateStatus(t.message ?: t.javaClass.simpleName, isError = true)
            } finally {
                operationRunning = false
                FilesOperationUiBinder.onAfterRun(operationProgress, ::setOperationButtonsEnabled)
                loadEntries()
                persistCurrentDir()
            }
        }
    }

    private fun selectResultPath(resultPath: VfsPath?) {
        if (resultPath !is VfsPath.LocalFile) {
            return
        }
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
        val plan = FilesSessionPlanner.build(
            session = session,
            linkedNetworkConfig = linkedNetworkConfig,
            defaultRootDir = defaultRootDir(),
            configuredFtpCharsetName = { configuredFtpCharsetName(it) }
        )
        if (sortSpinner.selectedItemPosition != plan.sortIndex) {
            sortSpinner.setSelection(plan.sortIndex, false)
        }
        currentNetworkLabel = plan.currentNetworkLabel
        browseSource = plan.source
        ftpConfig = plan.ftpConfig
        ftpResolvedCharset = plan.ftpResolvedCharset
        ftpCurrentPath = plan.ftpCurrentPath
        smbConfig = plan.smbConfig
        smbCurrentPath = plan.smbCurrentPath
        currentDir = plan.localDir
        selectedEntry = null
        loadEntries()
    }

    private fun buildCurrentDirText(dir: File): String {
        return FilesPathHelper.buildCurrentDirText(browseSource, currentNetworkLabel, dir, ftpCurrentPath, smbCurrentPath) { label, path ->
            getString(R.string.files_current_dir_network_template, label, path)
        }
    }

    private fun persistCurrentDir() {
        val sessionId = currentSessionId ?: return
        val dir = FilesPathHelper.pathForPersist(browseSource, currentDir, ftpCurrentPath, smbCurrentPath) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            folderSessionRepository.updateCurrentDir(sessionId, dir)
        }
    }

    private fun defaultRootDir(): File = requireContext().filesDir.parentFile ?: requireContext().filesDir

    private fun setOperationButtonsEnabled(enabled: Boolean) {
        actionUpButton.isEnabled = enabled
        actionCreateButton.isEnabled = enabled
    }

    private fun setLocalActionButtonsEnabled(enabled: Boolean) {
        actionCreateButton.isEnabled = enabled
    }

    private fun updateStatus(value: String, isError: Boolean) {
        operationStatusLabel.text = value
        val colorRes = if (isError) android.R.color.holo_red_dark else android.R.color.black
        operationStatusLabel.setTextColor(resources.getColor(colorRes, null))

        if (isError) {
            FilesStatusUiHelper.bindErrorClick(requireContext(), operationStatusLabel, value)
        } else {
            FilesStatusUiHelper.clearErrorClick(operationStatusLabel)
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
        if (!isFtpAutoEncoding(config)) {
            ftpResolvedCharset = configuredFtpCharsetName(config)
        }
        ftpDecodeCache.clear()
        loadRemoteEntries(
            config = config,
            protocol = NetworkProtocol.FTP,
            loadingText = getString(R.string.files_status_ftp_loading)
        ) { fetchFtpEntries(it, ftpCurrentPath) }
    }

    private fun loadSmbEntries() {
        val config = smbConfig
        if (config == null) {
            updateStatus(getString(R.string.files_status_invalid_start_path), isError = true)
            return
        }
        setLocalActionButtonsEnabled(false)
        actionCreateButton.isEnabled = true
        actionCreateButton.text = getString(R.string.action_new_folder)
        titleRefreshJob?.cancel()
        displayTitleByPath.clear()
        loadRemoteEntries(
            config = config,
            protocol = NetworkProtocol.SMB,
            loadingText = getString(R.string.files_status_smb_loading)
        ) { FilesSmbGateway.fetchEntries(it, smbCurrentPath, supportedExtensions) }
    }

    private fun loadRemoteEntries(
        config: NetworkConfigEntity,
        protocol: NetworkProtocol,
        loadingText: String,
        fetcher: suspend (NetworkConfigEntity) -> List<BrowserEntry>
    ) {
        updateStatus(loadingText, isError = false)
        currentDirLabel.text = buildCurrentDirText(defaultRootDir())
        val token = ++ftpLoadToken
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { fetcher(config) }
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
                updateStatus(formatNetworkError(error, protocol), isError = true)
            }
        }
    }

    private fun createSmbFolder(name: String) {
        val config = smbConfig ?: return
        runNetworkSmbOperation {
            FilesSmbGateway.createFolder(config, smbCurrentPath, name)
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
        runNetworkSmbOperation {
            FilesSmbGateway.renameEntry(config, entry, newName)
        }
    }

    private fun deleteSmbEntry(entry: BrowserEntry) {
        val config = smbConfig ?: return
        runNetworkSmbOperation {
            FilesSmbGateway.deleteEntry(config, entry)
        }
    }

    private suspend fun fetchFtpEntries(config: NetworkConfigEntity, path: String): List<BrowserEntry> = withContext(Dispatchers.IO) {
        val url = URL(FilesNetworkGateway.buildFtpUrl(config, path, "d", ftpEffectiveCharset(config)))
        val lines = url.openStream().bufferedReader(Charsets.ISO_8859_1).use { it.readLines() }
        val parseResult = FilesFtpCodec.parseEntries(
            lines = lines,
            path = path,
            configuredCharset = configuredFtpCharsetName(ftpConfig),
            previousResolvedCharset = ftpResolvedCharset,
            supportedExtensions = supportedExtensions,
            decodeCache = ftpDecodeCache
        )
        val decidedCharset = parseResult.resolvedCharset
        ftpResolvedCharset = decidedCharset
        Log.d(
            "FilesFragment",
            "ftp_charset_decision charset=$decidedCharset source=FTP path=$path sample_count=${lines.size}"
        )
        parseResult.entries
    }

    private fun openFtpFile(entry: BrowserEntry, isBackground: Boolean = false) {
        openRemoteFile(
            entry = entry,
            isBackground = isBackground,
            protocol = NetworkProtocol.FTP,
            downloadingStatus = getString(R.string.files_status_ftp_downloading),
            resolveConfig = { ftpConfig },
            resolveRemotePath = { it.ftpPath },
            downloader = { config, remotePath, displayName ->
                FilesTransferGateway.downloadFtpToLocal(
                    cacheDir = requireContext().cacheDir,
                    config = config,
                    remotePath = remotePath,
                    displayName = displayName,
                    charset = ftpEffectiveCharset(config)
                )
            }
        )
    }

    private fun openSmbFile(entry: BrowserEntry, isBackground: Boolean = false) {
        openRemoteFile(
            entry = entry,
            isBackground = isBackground,
            protocol = NetworkProtocol.SMB,
            downloadingStatus = getString(R.string.files_status_smb_downloading),
            resolveConfig = { smbConfig },
            resolveRemotePath = { it.smbPath },
            downloader = { config, remotePath, displayName ->
                FilesTransferGateway.downloadSmbToLocal(
                    cacheDir = requireContext().cacheDir,
                    config = config,
                    remotePath = remotePath,
                    displayName = displayName
                )
            }
        )
    }

    private fun ftpEffectiveCharset(config: NetworkConfigEntity): String {
        return configuredFtpCharsetName(config) ?: ftpResolvedCharset ?: "UTF-8"
    }

    private fun isFtpAutoEncoding(config: NetworkConfigEntity?): Boolean {
        val encoding = config?.encoding
        return encoding.isNullOrBlank() || encoding.equals("Auto", ignoreCase = true)
    }

    private fun configuredFtpCharsetName(config: NetworkConfigEntity?): String? {
        val encoding = config?.encoding?.trim().orEmpty()
        if (encoding.isBlank() || encoding.equals("Auto", ignoreCase = true)) {
            return null
        }
        return encoding
    }

    private fun formatNetworkError(error: Throwable, protocol: NetworkProtocol): String {
        return FilesErrorFormatter.format(
            error = error,
            protocol = protocol,
            texts = NetworkErrorTexts(
                ftpAuthFailed = getString(R.string.files_status_ftp_auth_failed),
                smbAuthFailed = getString(R.string.files_status_smb_auth_failed),
                ftpConnectionFailed = getString(R.string.files_status_ftp_connection_failed),
                smbConnectionFailed = getString(R.string.files_status_smb_connection_failed),
                defaultMessage = getString(R.string.files_status_invalid_start_path)
            )
        )
    }

    private fun uploadDocumentToFtp(uri: Uri) {
        uploadRemoteDocument(
            protocol = NetworkProtocol.FTP,
            uploadingStatus = getString(R.string.files_status_ftp_uploading),
            uploadedStatus = getString(R.string.files_status_ftp_uploaded),
            resolveConfig = { ftpConfig },
            uploader = { config ->
                FilesTransferGateway.uploadToFtp(
                    contentResolver = requireContext().contentResolver,
                    uri = uri,
                    config = config,
                    currentPath = ftpCurrentPath,
                    charset = ftpEffectiveCharset(config)
                )
            }
        )
    }

    private fun uploadDocumentToSmb(uri: Uri) {
        uploadRemoteDocument(
            protocol = NetworkProtocol.SMB,
            uploadingStatus = getString(R.string.files_status_smb_uploading),
            uploadedStatus = getString(R.string.files_status_smb_uploaded),
            resolveConfig = { smbConfig },
            uploader = { config ->
                FilesTransferGateway.uploadToSmb(
                    contentResolver = requireContext().contentResolver,
                    uri = uri,
                    config = config,
                    currentPath = smbCurrentPath
                )
            }
        )
    }

    private fun openRemoteFile(
        entry: BrowserEntry,
        isBackground: Boolean,
        protocol: NetworkProtocol,
        downloadingStatus: String,
        resolveConfig: () -> NetworkConfigEntity?,
        resolveRemotePath: (BrowserEntry) -> String?,
        downloader: suspend (NetworkConfigEntity, String, String) -> File
    ) {
        val config = resolveConfig() ?: return
        val remotePath = resolveRemotePath(entry) ?: return
        updateStatus(downloadingStatus, isError = false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { downloader(config, remotePath, entry.name) }
            result.onSuccess { local ->
                updateStatus(getString(R.string.files_status_done), isError = false)
                if (isBackground) openFileInBackground(local, entry.name)
                else (activity as? MainActivity)?.showReaderModeWithPath(local.absolutePath)
            }.onFailure { error ->
                updateStatus(formatNetworkError(error, protocol), isError = true)
            }
        }
    }

    private fun uploadRemoteDocument(
        protocol: NetworkProtocol,
        uploadingStatus: String,
        uploadedStatus: String,
        resolveConfig: () -> NetworkConfigEntity?,
        uploader: suspend (NetworkConfigEntity) -> Unit
    ) {
        val config = resolveConfig() ?: return
        updateStatus(uploadingStatus, isError = false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { uploader(config) }
            result.onSuccess {
                updateStatus(uploadedStatus, isError = false)
                loadEntries()
            }.onFailure { error ->
                updateStatus(formatNetworkError(error, protocol), isError = true)
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

    private fun addEntryToFavorites(entry: BrowserEntry) {
        val favoritePath = FilesFavoritePathBuilder.buildPath(
            source = browseSource,
            entry = entry,
            ftpConfig = ftpConfig,
            smbConfig = smbConfig,
            ftpCharsetProvider = { ftpEffectiveCharset(it) }
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

    private fun showEntryDetails(entry: BrowserEntry) {
        val message = FilesEntryDetailsBuilder.buildMessage(browseSource, entry)
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
        val currentEncoding = ftpConfig?.encoding ?: "Auto"
        val message = FilesFtpDiagnosticBuilder.buildMessage(rawBytes, currentEncoding)

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
            filesTitleRefresher.refreshLocalTitles(
                files = localFiles,
                onCachedTitle = { path, title ->
                    displayTitleByPath[path] = title
                },
                onResolvedTitle = { path, title ->
                    displayTitleByPath[path] = title
                    renderEntries()
                }
            )
            renderEntries()
        }
    }

    private fun promptRename(entry: BrowserEntry) {
        promptText(
            title = "Rename",
            hint = "New name",
            initialValue = entry.name
        ) { newName ->
            if (browseSource == BrowseSource.SMB) {
                renameSmbEntry(entry, newName)
            } else if (browseSource == BrowseSource.LOCAL) {
                val localFile = entry.localFile
                if (localFile == null) {
                    updateStatus(getString(R.string.files_select_item_first), isError = true)
                    return@promptText
                }
                runOperation(
                    FileOpRequest.Rename(
                        target = localFile.toVfsPath(),
                        newName = newName
                    )
                )
            }
        }
    }

    private fun promptDelete(entry: BrowserEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete")
            .setMessage("Are you sure you want to delete '${entry.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                if (browseSource == BrowseSource.SMB) {
                    deleteSmbEntry(entry)
                } else if (browseSource == BrowseSource.LOCAL) {
                    val localFile = entry.localFile
                    if (localFile == null) {
                        updateStatus(getString(R.string.files_select_item_first), isError = true)
                        return@setPositiveButton
                    }
                    runOperation(
                        FileOpRequest.Delete(
                            target = localFile.toVfsPath()
                        )
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openFileInBackground(file: File, displayName: String) {
        val request = OpenRequest(
            source = VfsPath.LocalFile(file.absolutePath),
            fileName = displayName,
            fileType = inferType(file.name),
            versionStamp = buildLocalVersionStamp(file),
            background = true
        )

        Toast.makeText(requireContext(), "Opening $displayName in background...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            readerViewModel.open(request).collect { state ->
                when (state) {
                    is OpenState.Ready -> {
                        Toast.makeText(requireContext(), "$displayName opened in background tab", Toast.LENGTH_SHORT).show()
                    }
                    is OpenState.Error -> {
                        Toast.makeText(requireContext(), "Error opening $displayName: ${state.error.message ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        // Ignore other states
                    }
                }
            }
        }
    }

    private fun inferType(fileName: String): FileType {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> FileType.PDF
            "mhtml", "mht" -> FileType.MHTML
            else -> FileType.MHTML // Default or WEB
        }
    }

    private fun File.toVfsPath(): VfsPath.LocalFile = VfsPath.LocalFile(absolutePath)

    private fun buildLocalVersionStamp(file: File): String? {
        if (!file.exists() || !file.isFile) {
            return null
        }
        return "${file.lastModified().coerceAtLeast(0L)}:${file.length().coerceAtLeast(0L)}"
    }
}
