package com.html_reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import core.common.DefaultDispatcherProvider
import core.data.repo.HistoryRepository
import core.database.entity.enums.FileType
import core.reader.model.OpenRequest
import core.reader.model.OpenState
import core.reader.model.ReaderTab
import core.reader.pdf.PdfReaderController
import core.reader.pdf.impl.AndroidPdfReaderController
import core.reader.web.BlockingResourceWebViewClient
import core.reader.web.NewTabLinkHandler
import core.reader.web.WebViewConfigurator
import core.reader.web.WebViewProgressTracker
import core.reader.vm.ReaderViewModel
import core.vfs.model.VfsPath
import java.io.File
import kotlinx.coroutines.launch

class ReaderFragment : Fragment() {
    private lateinit var pathInput: EditText
    private lateinit var openButton: Button
    private lateinit var closeSelectedButton: Button
    private lateinit var closeAllButton: Button
    private lateinit var openProgress: ProgressBar
    private lateinit var statusLabel: TextView
    private lateinit var activeLabel: TextView
    private lateinit var tabsList: ListView
    private lateinit var tabsAdapter: ArrayAdapter<String>
    private lateinit var readerViewModel: ReaderViewModel
    private lateinit var pdfReaderController: PdfReaderController
    private lateinit var historyRepository: HistoryRepository
    private lateinit var pdfPreviewImage: ImageView
    private lateinit var webPreview: WebView
    private lateinit var pdfPageInfoLabel: TextView
    private lateinit var pdfPrevButton: Button
    private lateinit var pdfNextButton: Button
    private var webProgressTracker: WebViewProgressTracker? = null

    private val tabs = mutableListOf<ReaderTab>()
    private var selectedTabId: String? = null
    private var opening = false
    private var pdfPageCount = 0
    private var currentPdfPageIndex = 0

    companion object {
        private const val ARG_INITIAL_PATH = "arg_initial_path"
        private const val STATE_PATH_INPUT = "reader_state_path_input"
        private const val STATE_SELECTED_TAB_ID = "reader_state_selected_tab_id"

        fun newInstance(initialPath: String): ReaderFragment {
            return ReaderFragment().apply {
                arguments = bundleOf(ARG_INITIAL_PATH to initialPath)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_reader, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pathInput = view.findViewById(R.id.reader_path_input)
        openButton = view.findViewById(R.id.reader_open_button)
        closeSelectedButton = view.findViewById(R.id.reader_close_selected_button)
        closeAllButton = view.findViewById(R.id.reader_close_all_button)
        openProgress = view.findViewById(R.id.reader_open_progress)
        statusLabel = view.findViewById(R.id.reader_status)
        activeLabel = view.findViewById(R.id.reader_active_tab)
        tabsList = view.findViewById(R.id.reader_tabs_list)
        pdfPreviewImage = view.findViewById(R.id.reader_pdf_preview)
        webPreview = view.findViewById(R.id.reader_web_preview)
        pdfPageInfoLabel = view.findViewById(R.id.reader_pdf_page_info)
        pdfPrevButton = view.findViewById(R.id.reader_pdf_prev)
        pdfNextButton = view.findViewById(R.id.reader_pdf_next)
        tabsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        tabsList.adapter = tabsAdapter

        val context = requireContext()
        val dispatcherProvider = DefaultDispatcherProvider()
        pdfReaderController = AndroidPdfReaderController(dispatcherProvider)
        historyRepository = ReaderRuntime.historyRepository(context)
        readerViewModel = ReaderRuntime.viewModel(context)

        statusLabel.text = getString(R.string.reader_status_idle)
        activeLabel.text = getString(R.string.reader_active_none)
        pdfPageInfoLabel.text = getString(R.string.reader_pdf_page_template, 0, 0)
        pdfPrevButton.isEnabled = false
        pdfNextButton.isEnabled = false
        WebViewConfigurator.configure(webPreview)
        webPreview.visibility = View.GONE
        val restoredPath = savedInstanceState?.getString(STATE_PATH_INPUT)
        val initialPath = arguments?.getString(ARG_INITIAL_PATH)
        pathInput.setText(restoredPath ?: initialPath.orEmpty())
        selectedTabId = savedInstanceState?.getString(STATE_SELECTED_TAB_ID)

        openButton.setOnClickListener {
            if (opening) {
                return@setOnClickListener
            }
            val path = pathInput.text?.toString()?.trim().orEmpty()
            if (path.isBlank()) {
                showShort(getString(R.string.reader_select_file_first))
                return@setOnClickListener
            }
            val file = File(path)
            if (!file.exists() || !file.isFile) {
                showShort(getString(R.string.reader_file_not_found))
                return@setOnClickListener
            }
            if (!isSupportedLocalFile(file.name)) {
                showShort(getString(R.string.reader_unsupported_file_type))
                return@setOnClickListener
            }
            val request = OpenRequest(
                source = VfsPath.LocalFile(file.absolutePath),
                fileName = file.name,
                fileType = inferType(file.name)
            )
            runOpen(request)
        }

        closeSelectedButton.setOnClickListener {
            val tabId = selectedTabId
            if (tabId == null) {
                showShort(getString(R.string.reader_select_tab_first))
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                readerViewModel.closeTab(tabId)
                selectedTabId = tabs.firstOrNull()?.tabId
                updateActiveLabel()
            }
        }

        closeAllButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                readerViewModel.closeAll()
                selectedTabId = null
                updateActiveLabel()
            }
        }

        tabsList.setOnItemClickListener { _, _, position, _ ->
            val tab = tabs.getOrNull(position) ?: return@setOnItemClickListener
            selectedTabId = tab.tabId
            viewLifecycleOwner.lifecycleScope.launch {
                readerViewModel.switchTo(tab.tabId)
                updateTabs()
                loadSelectedTabContent()
            }
        }

        pdfPrevButton.setOnClickListener {
            if (currentPdfPageIndex <= 0) {
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                pdfReaderController.goToPage(currentPdfPageIndex - 1)
            }
        }

        pdfNextButton.setOnClickListener {
            if (currentPdfPageIndex >= pdfPageCount - 1) {
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                pdfReaderController.goToPage(currentPdfPageIndex + 1)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            readerViewModel.tabs.collect { newTabs ->
                tabs.clear()
                tabs.addAll(newTabs)
                if (selectedTabId == null || tabs.none { it.tabId == selectedTabId }) {
                    selectedTabId = tabs.firstOrNull()?.tabId
                }
                updateTabs()
                loadSelectedTabContent()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            pdfReaderController.observePageChanges().collect { pageIndex ->
                currentPdfPageIndex = pageIndex
                updatePdfPageControlState()
                renderCurrentPdfPage()
                val tab = tabs.firstOrNull { it.tabId == selectedTabId && it.fileType == FileType.PDF } ?: return@collect
                if (pdfPageCount > 0) {
                    val progress = (pageIndex.toFloat() / pdfPageCount.toFloat()).coerceIn(0f, 1f)
                    historyRepository.updateProgress(tab.sourcePathRaw, progress, pageIndex)
                }
            }
        }

        // Auto-open logic if initial path provided and not restored from state
        if (savedInstanceState == null && !initialPath.isNullOrBlank()) {
             val file = File(initialPath)
             if (file.exists() && file.isFile && isSupportedLocalFile(file.name)) {
                 val request = OpenRequest(
                    source = VfsPath.LocalFile(file.absolutePath),
                    fileName = file.name,
                    fileType = inferType(file.name)
                 )
                 // Run on next frame to ensure UI is ready
                 view.post {
                     runOpen(request)
                 }
             }
        }
    }

    private fun runOpen(request: OpenRequest) {
        opening = true
        setButtonsEnabled(false)
        openProgress.visibility = View.VISIBLE
        openProgress.isIndeterminate = true
        statusLabel.text = getString(R.string.reader_status_loading)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                readerViewModel.open(request).collect { state ->
                    when (state) {
                        is OpenState.Loading -> {
                            statusLabel.text = getString(R.string.reader_status_loading)
                            openProgress.isIndeterminate = true
                        }
                        is OpenState.Copying -> {
                            if (state.totalBytes > 0L) {
                                openProgress.isIndeterminate = false
                                val percent = ((state.bytesCopied * 100L) / state.totalBytes).toInt().coerceIn(0, 100)
                                openProgress.max = 100
                                openProgress.progress = percent
                                statusLabel.text = getString(R.string.reader_status_copying_percent, percent)
                            } else {
                                openProgress.isIndeterminate = true
                                statusLabel.text = getString(R.string.reader_status_copying)
                            }
                        }
                        is OpenState.Ready -> {
                            selectedTabId = state.tab.tabId
                            statusLabel.text = getString(R.string.reader_status_ready, state.tab.title ?: state.tab.tabId)
                            loadSelectedTabContent()
                        }
                        is OpenState.Error -> {
                            val message = state.error.message ?: state.error.javaClass.simpleName
                            statusLabel.text = getString(R.string.reader_status_error, message)
                        }
                    }
                }
            } catch (e: Exception) {
                // Crash fix: Catch any unexpected errors during open flow
                 statusLabel.text = getString(R.string.reader_status_error, e.message ?: "Unknown error")
            } finally {
                opening = false
                setButtonsEnabled(true)
                openProgress.visibility = View.GONE
                updateTabs()
            }
        }
    }

    private fun updateTabs() {
        tabsAdapter.clear()
        tabsAdapter.addAll(
            tabs.map {
                val selected = if (it.tabId == selectedTabId) "▶ " else ""
                "$selected${it.title ?: it.tabId}  •  ${it.fileType.name}  •  ${(it.lastKnownProgress * 100f).toInt()}%"
            }
        )
        tabsAdapter.notifyDataSetChanged()
        updateActiveLabel()
    }

    private fun updateActiveLabel() {
        val active = tabs.firstOrNull { it.tabId == selectedTabId }
        activeLabel.text = if (active == null) {
            getString(R.string.reader_active_none)
        } else {
            getString(R.string.reader_active_template, active.title ?: active.tabId)
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        openButton.isEnabled = enabled
        closeSelectedButton.isEnabled = enabled
        closeAllButton.isEnabled = enabled
    }

    private fun loadSelectedTabContent() {
        val tab = tabs.firstOrNull { it.tabId == selectedTabId }
        if (tab == null) {
            pdfPageCount = 0
            currentPdfPageIndex = 0
            pdfPreviewImage.setImageDrawable(null)
            webPreview.loadUrl("about:blank")
            webPreview.visibility = View.GONE
            webProgressTracker?.stopTracking()
            webProgressTracker = null
            updatePdfPageControlState()
            return
        }
        if (tab.fileType == FileType.PDF) {
            loadSelectedPdfTab(tab)
            return
        }
        loadSelectedWebTab(tab)
    }

    private fun loadSelectedPdfTab(tab: ReaderTab) {
        webPreview.visibility = View.GONE
        webPreview.loadUrl("about:blank")
        webProgressTracker?.stopTracking()
        webProgressTracker = null
        pdfPreviewImage.visibility = View.VISIBLE
        pdfPrevButton.visibility = View.VISIBLE
        pdfNextButton.visibility = View.VISIBLE
        pdfPageInfoLabel.visibility = View.VISIBLE
        val cachePath = tab.cacheFilePath
        if (cachePath.isNullOrBlank()) {
            pdfPageCount = 0
            currentPdfPageIndex = 0
            updatePdfPageControlState()
            return
        }
        val file = File(cachePath)
        if (!file.exists() || !file.isFile) {
            pdfPageCount = 0
            currentPdfPageIndex = 0
            updatePdfPageControlState()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pdfReaderController.open(file)
                pdfPageCount = pdfReaderController.getPageCount()
                val initialPage = if (tab.lastKnownPageIndex >= 0) tab.lastKnownPageIndex else 0
                pdfReaderController.goToPage(initialPage.coerceIn(0, (pdfPageCount - 1).coerceAtLeast(0)))
                updatePdfPageControlState()
            } catch (e: Exception) {
                statusLabel.text = getString(R.string.reader_status_error, e.message ?: e.javaClass.simpleName)
                pdfPreviewImage.setImageDrawable(null)
                pdfPageCount = 0
                currentPdfPageIndex = 0
                updatePdfPageControlState()
            }
        }
    }

    private fun loadSelectedWebTab(tab: ReaderTab) {
        pdfPageCount = 0
        currentPdfPageIndex = 0
        updatePdfPageControlState()
        pdfPreviewImage.setImageDrawable(null)
        pdfPreviewImage.visibility = View.GONE
        pdfPrevButton.visibility = View.GONE
        pdfNextButton.visibility = View.GONE
        pdfPageInfoLabel.visibility = View.GONE
        webPreview.visibility = View.VISIBLE
        val cachePath = tab.cacheFilePath
        if (cachePath.isNullOrBlank()) {
            webPreview.loadUrl("about:blank")
            return
        }
        val file = File(cachePath)
        if (!file.exists() || !file.isFile) {
            webPreview.loadUrl("about:blank")
            statusLabel.text = getString(R.string.reader_status_error, getString(R.string.reader_file_not_found))
            return
        }
        WebViewConfigurator.configure(webPreview)
        webPreview.webViewClient = BlockingResourceWebViewClient(
            fileType = tab.fileType,
            onOpenLinkInNewTab = NewTabLinkHandler { url ->
                if (tab.fileType == FileType.WEB) {
                    webPreview.loadUrl(url)
                } else {
                    showShort(getString(R.string.reader_external_link_blocked))
                }
            }
        )
        webProgressTracker?.stopTracking()
        webProgressTracker = WebViewProgressTracker(
            webView = webPreview,
            sourcePathRaw = tab.sourcePathRaw,
            historyRepo = historyRepository,
            coroutineScope = viewLifecycleOwner.lifecycleScope
        ).also { it.startTracking() }
        webPreview.loadUrl(file.toURI().toString())
    }

    private suspend fun renderCurrentPdfPage() {
        if (pdfPageCount <= 0) {
            return
        }
        val width = if (pdfPreviewImage.width > 0) {
            pdfPreviewImage.width
        } else {
            resources.displayMetrics.widthPixels - 24
        }
        val bitmap = pdfReaderController.renderPage(currentPdfPageIndex, width)
        pdfPreviewImage.setImageBitmap(bitmap)
    }

    private fun updatePdfPageControlState() {
        val hasPdf = pdfPageCount > 0
        pdfPrevButton.isEnabled = hasPdf && currentPdfPageIndex > 0
        pdfNextButton.isEnabled = hasPdf && currentPdfPageIndex < pdfPageCount - 1
        val shownPage = if (hasPdf) currentPdfPageIndex + 1 else 0
        val total = if (hasPdf) pdfPageCount else 0
        pdfPageInfoLabel.text = getString(R.string.reader_pdf_page_template, shownPage, total)
    }

    private fun inferType(fileName: String): FileType {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> FileType.PDF
            "mhtml", "mht" -> FileType.MHTML
            else -> FileType.MHTML
        }
    }

    private fun isSupportedLocalFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "pdf" || ext == "mhtml" || ext == "mht"
    }

    private fun showShort(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        viewLifecycleOwner.lifecycleScope.launch {
            pdfReaderController.close()
        }
        webProgressTracker?.stopTracking()
        webProgressTracker = null
        webPreview.loadUrl("about:blank")
        webPreview.stopLoading()
        webPreview.webViewClient = WebViewClient()
        webPreview.destroy()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PATH_INPUT, pathInput.text?.toString().orEmpty())
        outState.putString(STATE_SELECTED_TAB_ID, selectedTabId)
        super.onSaveInstanceState(outState)
    }
}
