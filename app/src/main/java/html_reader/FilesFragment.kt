package com.html_reader

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.html_reader.files.BrowseSource
import com.html_reader.files.BrowserEntry
import com.html_reader.files.FilesErrorFormatter
import com.html_reader.files.FilesRemoteController
import com.html_reader.files.FilesRemoteHost
import com.html_reader.files.FilesNavigateUpHelper
import com.html_reader.files.FilesOperationRunner
import com.html_reader.files.FilesOperationUiBinder
import com.html_reader.files.FilesPathHelper
import com.html_reader.files.FilesStatusUiHelper
import com.html_reader.files.NetworkErrorTexts
import com.html_reader.files.FilesTitleRefresher
import com.html_reader.files.FilesUiBinder
import com.html_reader.files.isSamePathAs
import com.html_reader.files.pathKey
import core.common.DefaultDispatcherProvider
import core.data.repo.FavoritesRepository
import core.data.repo.NetworkConfigRepository
import core.data.repo.TitleCacheRepository
import core.database.entity.enums.FileType
import core.database.entity.enums.NetworkProtocol
import core.fileops.model.FileOpRequest
import core.fileops.usecase.ExecuteFileOpUseCase
import core.fileops.util.NameConflictResolver
import core.reader.model.OpenRequest
import core.reader.model.OpenState
import core.reader.usecase.InferFileTypeUseCase
import core.reader.vm.ReaderViewModel
import core.session.repo.FolderSessionRepository
import core.title.impl.HtmlTitleExtractor
import core.vfs.local.LocalFileSystem
import core.vfs.model.VfsPath
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FilesFragment : Fragment(), FilesRemoteHost {
    internal lateinit var queryInput: EditText
    internal lateinit var sortSpinner: Spinner
    private lateinit var fontSizeSpinner: Spinner
    internal lateinit var currentDirLabel: TextView
    internal lateinit var operationStatusLabel: TextView
    internal lateinit var operationProgress: ProgressBar
    internal lateinit var actionUpButton: Button
    internal lateinit var actionCreateButton: Button
    internal lateinit var listView: android.widget.ListView
    internal lateinit var adapter: ArrayAdapter<BrowserEntry>
    internal lateinit var executeFileOpUseCase: ExecuteFileOpUseCase
    internal lateinit var folderSessionRepository: FolderSessionRepository
    internal lateinit var favoritesRepository: FavoritesRepository
    internal lateinit var networkConfigRepository: NetworkConfigRepository
    internal lateinit var titleCacheRepository: TitleCacheRepository
    internal lateinit var currentSessionStore: AppCurrentSessionStore
    internal lateinit var sessionSourceStore: AppSessionSourceStore
    internal lateinit var htmlTitleExtractor: HtmlTitleExtractor
    internal lateinit var readerViewModel: ReaderViewModel

    internal val allEntries = mutableListOf<BrowserEntry>()
    internal val displayedEntries = mutableListOf<BrowserEntry>()
    internal var currentDir: File? = null
    internal var selectedEntry: BrowserEntry? = null
    internal var operationRunning = false
    internal var currentSessionId: Long? = null
    internal var initialNetworkConfigId: Long? = null
    internal var requestedNetworkEntry: Boolean = false
    internal var initialStartPath: String? = null
    internal var initialSafTreeUri: String? = null
    internal var currentNetworkLabel: String? = null
    internal var browseSource: BrowseSource = BrowseSource.LOCAL
    internal var titleRefreshJob: Job? = null
    internal lateinit var filesTitleRefresher: FilesTitleRefresher
    internal lateinit var remoteController: FilesRemoteController
    internal var currentNameTextSizeSp: Float = 16f
    internal val supportedExtensions = setOf("mht", "mhtml", "pdf")
    internal val displayTitleByPath = ConcurrentHashMap<String, String>()
    internal val ftpUploadLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            remoteController.uploadDocumentToFtp(uri)
        }
    }
    internal val smbUploadLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            remoteController.uploadDocumentToSmb(uri)
        }
    }

    companion object {
        private const val ARG_NETWORK_CONFIG_ID = "arg_network_config_id"
        private const val ARG_START_PATH = "arg_start_path"
        private const val ARG_SAF_TREE_URI = "arg_saf_tree_uri"
        private const val FILE_BROWSER_PREFS = "files_browser_settings"
        private const val KEY_NAME_FONT_SIZE_INDEX = "name_font_size_index"
        private const val KEY_QUERY_TEXT = "query_text"
        private const val KEY_SCROLL_DIR = "scroll_dir"
        private const val KEY_SCROLL_POSITION = "scroll_position"
        private const val KEY_SCROLL_TOP = "scroll_top"
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
                saveQueryText(s?.toString().orEmpty())
                renderEntries()
            }
        })

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val item = displayedEntries.getOrNull(position) ?: return@OnItemClickListener
            if (item.isDirectory) {
                if (browseSource == BrowseSource.FTP) {
                    remoteController.ftpCurrentPath = item.ftpPath ?: remoteController.ftpCurrentPath
                    selectedEntry = null
                    loadEntries()
                    persistCurrentDir()
                    return@OnItemClickListener
                }
                if (browseSource == BrowseSource.SMB) {
                    remoteController.smbCurrentPath = item.smbPath ?: remoteController.smbCurrentPath
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
                // Direct open logic：打开前先记录当前目录，避免返回时位置丢失
                selectedEntry = null
                persistCurrentDir()
                if (browseSource == BrowseSource.FTP) {
                    remoteController.openFtpFile(item)
                } else if (browseSource == BrowseSource.SMB) {
                    remoteController.openSmbFile(item)
                } else {
                    val path = item.localFile?.absolutePath ?: return@OnItemClickListener
                    (activity as? MainActivity)?.showReaderModeWithPath(path)
                }
            }
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val item = displayedEntries.getOrNull(position) ?: return@OnItemLongClickListener true
            showEntryActions(item)
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
        restoreQueryText()
        restoreSessionAndLoad()
        observeSessionSwitch()
        ensureStoragePermissionIfNeeded()
    }

    private fun browserPrefs() = requireContext().getSharedPreferences(FILE_BROWSER_PREFS, Context.MODE_PRIVATE)

    internal fun saveQueryText(value: String) {
        browserPrefs().edit().putString(KEY_QUERY_TEXT, value).apply()
    }

    internal fun restoreQueryText() {
        queryInput.setText(browserPrefs().getString(KEY_QUERY_TEXT, "").orEmpty())
        queryInput.setSelection(queryInput.text?.length ?: 0)
    }

    internal fun saveScrollState(dir: File) {
        val position = listView.firstVisiblePosition
        val child = listView.getChildAt(0) ?: return
        browserPrefs().edit()
            .putString(KEY_SCROLL_DIR, dir.absolutePath)
            .putInt(KEY_SCROLL_POSITION, position)
            .putInt(KEY_SCROLL_TOP, child.top)
            .apply()
    }

    internal fun restoreScrollState(dir: File) {
        val prefs = browserPrefs()
        if (prefs.getString(KEY_SCROLL_DIR, null) != dir.absolutePath) return
        val position = prefs.getInt(KEY_SCROLL_POSITION, 0)
        val top = prefs.getInt(KEY_SCROLL_TOP, 0)
        listView.post { listView.setSelectionFromTop(position, top) }
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
        remoteController = FilesRemoteController(this, supportedExtensions)
    }

    private fun initOpenArguments() {
        initialNetworkConfigId = arguments?.getLong(ARG_NETWORK_CONFIG_ID)?.takeIf { it > 0L }
        requestedNetworkEntry = initialNetworkConfigId != null
        initialStartPath = arguments?.getString(ARG_START_PATH)?.trim()?.takeIf { it.isNotBlank() }
        initialSafTreeUri = arguments?.getString(ARG_SAF_TREE_URI)?.trim()?.takeIf { it.isNotBlank() }
    }

    internal fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        if (browseSource == BrowseSource.LOCAL && !checkStoragePermission()) {
            // Optional: check again if needed
        }
    }

    override fun onPause() {
        super.onPause()
        currentDir?.let { saveScrollState(it) }
        // 离开页面（如进入阅读器）时同步持久化目录，防止进程被回收后位置丢失
        persistCurrentDir()
    }

    fun navigateUp(): Boolean {
        val plan = FilesNavigateUpHelper.plan(
            browseSource,
            currentDirFile(),
            remoteController.ftpCurrentPath,
            remoteController.smbCurrentPath
        )
        if (!plan.handled) return false
        if (plan.localAccessDenied) {
            if (!checkStoragePermission()) requestStoragePermission()
            else Toast.makeText(requireContext(), getString(R.string.files_msg_cannot_access_parent), Toast.LENGTH_SHORT).show()
            return false
        }
        if (plan.nextFtpPath != null) remoteController.ftpCurrentPath = plan.nextFtpPath
        if (plan.nextSmbPath != null) remoteController.smbCurrentPath = plan.nextSmbPath
        if (plan.nextLocalDir != null) currentDir = plan.nextLocalDir
        selectedEntry = null
        loadEntries()
        persistCurrentDir()
        return true
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

    internal fun currentDirFile(): File = currentDir ?: requireContext().filesDir

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

    internal fun runOperation(request: FileOpRequest) {
        if (!tryAcquireOperationLock()) {
            updateStatus(getString(R.string.files_status_operation_in_progress), isError = true)
            return
        }
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
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                updateStatus(t.message ?: t.javaClass.simpleName, isError = true)
            } finally {
                releaseOperationLock()
                // 视图已销毁时不再触碰 UI/触发新任务
                if (view != null) {
                    FilesOperationUiBinder.onAfterRun(operationProgress, ::setOperationButtonsEnabled)
                    loadEntries()
                    persistCurrentDir()
                }
            }
        }
    }

    override fun tryAcquireOperationLock(): Boolean {
        if (operationRunning) {
            return false
        }
        operationRunning = true
        return true
    }

    override fun releaseOperationLock() {
        operationRunning = false
    }

    internal fun selectResultPath(resultPath: VfsPath?) {
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

    internal fun buildCurrentDirText(dir: File): String {
        return FilesPathHelper.buildCurrentDirText(
            browseSource,
            currentNetworkLabel,
            dir,
            remoteController.ftpCurrentPath,
            remoteController.smbCurrentPath
        ) { label, path ->
            getString(R.string.files_current_dir_network_template, label, path)
        }
    }

    override fun persistCurrentDir() {
        val sessionId = currentSessionId ?: return
        val dir = FilesPathHelper.pathForPersist(
            browseSource,
            currentDir,
            remoteController.ftpCurrentPath,
            remoteController.smbCurrentPath
        ) ?: return
        // 使用 Activity 作用域，避免导航到阅读器时 viewLifecycle 销毁取消尚未完成的写入
        val scope = activity?.lifecycleScope ?: viewLifecycleOwner.lifecycleScope
        scope.launch {
            folderSessionRepository.updateCurrentDir(sessionId, dir)
        }
    }

    internal fun defaultRootDir(): File = requireContext().filesDir.parentFile ?: requireContext().filesDir

    override fun setOperationButtonsEnabled(enabled: Boolean) {
        if (view == null) return
        actionUpButton.isEnabled = enabled
        actionCreateButton.isEnabled = enabled
    }

    internal fun setLocalActionButtonsEnabled(enabled: Boolean) {
        if (view == null) return
        actionCreateButton.isEnabled = enabled
    }

    override fun updateStatus(value: String, isError: Boolean) {
        if (view == null) return
        operationStatusLabel.text = value
        val colorRes = if (isError) android.R.color.holo_red_dark else android.R.color.black
        operationStatusLabel.setTextColor(resources.getColor(colorRes, null))

        if (isError) {
            FilesStatusUiHelper.bindErrorClick(requireContext(), operationStatusLabel, value)
        } else {
            FilesStatusUiHelper.clearErrorClick(operationStatusLabel)
        }
    }

    override val hostContext: Context
        get() = requireContext()

    override val hostCacheDir: File
        get() = requireContext().cacheDir

    override val hostLifecycleScope: LifecycleCoroutineScope
        get() = viewLifecycleOwner.lifecycleScope

    override fun isAttached(): Boolean = isAdded

    override fun string(resId: Int): String = if (isAdded) getString(resId) else ""

    override fun updateCurrentDirLabel() {
        if (view == null) return
        currentDirLabel.text = buildCurrentDirText(currentDirFile())
    }

    override fun prepareRemoteBrowse(createActionLabelRes: Int) {
        if (view == null) return
        setLocalActionButtonsEnabled(false)
        actionCreateButton.isEnabled = true
        actionCreateButton.text = getString(createActionLabelRes)
        titleRefreshJob?.cancel()
        displayTitleByPath.clear()
    }

    override fun onRemoteEntriesLoaded(entries: List<BrowserEntry>) {
        allEntries.clear()
        allEntries.addAll(entries)
        renderEntries()
        updateStatus(getString(R.string.files_status_done), isError = false)
    }

    override fun onRemoteEntriesLoadFailed(message: String) {
        if (view == null) return
        allEntries.clear()
        displayedEntries.clear()
        adapter.clear()
        adapter.notifyDataSetChanged()
        updateStatus(message, isError = true)
    }

    override fun showOperationProgress() {
        if (view == null) return
        operationProgress.visibility = View.VISIBLE
        operationProgress.isIndeterminate = true
        operationProgress.progress = 0
    }

    override fun hideOperationProgress() {
        if (view == null) return
        operationProgress.visibility = View.GONE
    }

    override fun openDownloadedFile(file: File, displayName: String, isBackground: Boolean) {
        if (view == null) return
        updateStatus(getString(R.string.files_status_done), isError = false)
        if (isBackground) {
            openFileInBackground(file, displayName)
        } else {
            (activity as? MainActivity)?.showReaderModeWithPath(file.absolutePath)
        }
    }

    override fun reloadEntriesAfterRemoteMutation() {
        if (view == null) return
        loadEntries()
    }

    override fun formatNetworkError(error: Throwable, protocol: NetworkProtocol): String {
        if (!isAdded) return ""
        return FilesErrorFormatter.format(
            error = error,
            protocol = protocol,
            texts = NetworkErrorTexts(
                ftpAuthFailed = getString(R.string.files_status_ftp_auth_failed),
                smbAuthFailed = getString(R.string.files_status_smb_auth_failed),
                ftpConnectionFailed = getString(R.string.files_status_ftp_connection_failed),
                smbConnectionFailed = getString(R.string.files_status_smb_connection_failed),
                defaultMessage = getString(R.string.files_status_invalid_start_path),
                inputUnavailable = getString(R.string.files_error_document_unreadable),
                permissionDenied = getString(R.string.files_error_permission_denied)
            )
        )
    }

    internal fun openFileInBackground(file: File, displayName: String) {
        val request = OpenRequest(
            source = VfsPath.LocalFile(file.absolutePath),
            fileName = displayName,
            fileType = inferType(file.name),
            versionStamp = buildLocalVersionStamp(file),
            background = true
        )

        Toast.makeText(requireContext(), getString(R.string.files_msg_opening_background, displayName), Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                readerViewModel.open(request).collect { state ->
                    when (state) {
                        is OpenState.Ready -> {
                            Toast.makeText(requireContext(), getString(R.string.files_msg_opened_background_tab, displayName), Toast.LENGTH_SHORT).show()
                        }
                        is OpenState.Error -> {
                            Toast.makeText(requireContext(), getString(R.string.files_msg_open_background_error, displayName, state.error.message ?: getString(R.string.common_unknown_error)), Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            // Ignore other states
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (view != null) {
                    Toast.makeText(requireContext(), getString(R.string.files_msg_open_background_error, displayName, t.message ?: getString(R.string.common_unknown_error)), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    internal fun inferType(fileName: String): FileType = InferFileTypeUseCase.infer(fileName)

    internal fun buildLocalVersionStamp(file: File): String? {
        if (!file.exists() || !file.isFile) {
            return null
        }
        return "${file.lastModified().coerceAtLeast(0L)}:${file.length().coerceAtLeast(0L)}"
    }
}
