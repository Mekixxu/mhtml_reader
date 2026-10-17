package com.html_reader

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.graphics.drawable.BitmapDrawable
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
import core.common.AppError
import core.common.DefaultDispatcherProvider
import core.data.repo.HistoryRepository
import core.common.UrlCredentialSanitizer
import core.database.entity.enums.FileType
import core.reader.model.OpenRequest
import core.reader.model.OpenState
import core.reader.model.ReaderTab
import core.reader.usecase.InferFileTypeUseCase
import core.reader.pdf.PdfContentState
import core.reader.pdf.PdfReaderController
import core.reader.pdf.PdfRenderKey
import core.reader.pdf.impl.AndroidPdfReaderController
import core.reader.web.BlockingResourceWebViewClient
import core.reader.web.MeasuringWebView
import core.reader.web.NewTabLinkHandler
import core.reader.web.SafeUrlLogSummary
import core.reader.web.WebViewConfigurator
import core.reader.web.WebProgressRestoreGuard
import core.reader.web.WebProgressRestoreGate
import core.reader.web.WebProgressRestoreRequest
import core.reader.web.WebProgressRestoreResult
import core.reader.web.WebViewProgressTracker
import core.reader.web.WebProgressSamplingState
import core.reader.web.WebViewMetrics
import core.reader.web.WebViewProgressRestorer
import core.reader.web.WebViewProgressWriter
import core.reader.vm.ReaderViewModel
import core.vfs.model.VfsPath
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderFragment : Fragment() {
    private lateinit var openProgress: ProgressBar
    private lateinit var statusLabel: TextView
    private lateinit var pdfPreviewImage: ImageView
    private lateinit var webPreview: WebView
    private lateinit var pdfPageInfoLabel: TextView
    private lateinit var pdfPrevButton: Button
    private lateinit var pdfNextButton: Button
    private lateinit var pdfControls: View
    private lateinit var readerViewModel: ReaderViewModel
    private lateinit var pdfReaderController: PdfReaderController
    private lateinit var historyRepository: HistoryRepository
    private lateinit var webProgressWriter: WebViewProgressWriter
    private var webProgressTracker: WebViewProgressTracker? = null
    private val webProgressSamplingState = WebProgressSamplingState()
    private var webRestoreJob: Job? = null
    private val webProgressRestoreGate = WebProgressRestoreGate()
    private var webContentGeneration = 0L
    private var webRestoreInteractionGeneration: Long? = null
    private var activeWebTabId: String? = null
    private var activeWebTargetUrl: String? = null

    private val tabs = mutableListOf<ReaderTab>()
    private var selectedTabId: String? = null
    private var restoredContentLoaded = false
    private var opening = false
    private var pdfPageCount = 0
    private var currentPdfPageIndex = 0
    private val pdfContentState = PdfContentState()
    private var pdfLoadJob: Job? = null
    private var pdfRenderJob: Job? = null
    private var pdfExitJob: Job? = null
    private var activePdfTabId: String? = null
    private var pdfRenderRequestToken = 0L
    private val mhtmlFallbackRetriedTabIds = mutableSetOf<String>()
    private var activeWebRenderProfile: WebViewConfigurator.RenderProfile = WebViewConfigurator.RenderProfile.DEFAULT

    private data class ImagePayload(
        val bytes: ByteArray,
        val mimeType: String,
        val fileName: String
    )

    companion object {
        private const val ARG_INITIAL_PATH = "arg_initial_path"
        private const val ARG_INITIAL_VERSION_STAMP = "arg_initial_version_stamp"
        private const val STATE_SELECTED_TAB_ID = "reader_state_selected_tab_id"
        private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024

        fun newInstance(initialPath: String, initialVersionStamp: String? = null): ReaderFragment {
            return ReaderFragment().apply {
                arguments = bundleOf(
                    ARG_INITIAL_PATH to initialPath,
                    ARG_INITIAL_VERSION_STAMP to initialVersionStamp
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_reader, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        openProgress = view.findViewById(R.id.reader_open_progress)
        statusLabel = view.findViewById(R.id.reader_status)
        pdfPreviewImage = view.findViewById(R.id.reader_pdf_preview)
        webPreview = view.findViewById(R.id.reader_web_preview)
        pdfPageInfoLabel = view.findViewById(R.id.reader_pdf_page_info)
        pdfPrevButton = view.findViewById(R.id.reader_pdf_prev)
        pdfNextButton = view.findViewById(R.id.reader_pdf_next)
        pdfControls = view.findViewById(R.id.reader_pdf_controls)

        val context = requireContext()
        val dispatcherProvider = DefaultDispatcherProvider()
        pdfReaderController = AndroidPdfReaderController(dispatcherProvider)
        historyRepository = ReaderRuntime.historyRepository(context)
        webProgressWriter = ReaderRuntime.webProgressWriter(context)
        readerViewModel = ReaderRuntime.viewModel(context)

        statusLabel.visibility = View.GONE
        pdfPageInfoLabel.text = getString(R.string.reader_pdf_page_template, 0, 0)
        pdfPrevButton.isEnabled = false
        pdfNextButton.isEnabled = false
        webPreview.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        WebViewConfigurator.configure(webPreview, profile = WebViewConfigurator.RenderProfile.DEFAULT)
        webPreview.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                webRestoreInteractionGeneration = webContentGeneration
                if (webProgressSamplingState.onRestoreResult(
                        webContentGeneration,
                        WebProgressRestoreResult.USER_TOOK_CONTROL
                    )
                ) webProgressTracker?.enableSampling()
            }
            false
        }
        webPreview.visibility = View.GONE
        installWebLongPressHandler()
        
        selectedTabId = savedInstanceState?.getString(STATE_SELECTED_TAB_ID)
        val initialPath = arguments?.getString(ARG_INITIAL_PATH)
        val initialVersionStampArg = arguments?.getString(ARG_INITIAL_VERSION_STAMP)

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
                // 配置变更（旋转/分屏）后 currentTabId 与恢复的 selectedTabId 相同，
                // currentTabId 收集器不会触发加载，这里补一次幂等恢复。
                if (!restoredContentLoaded && selectedTabId != null && tabs.any { it.tabId == selectedTabId }) {
                    restoredContentLoaded = true
                    loadSelectedTabContent()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            readerViewModel.currentTabId.collect { tabId ->
                if (tabId != null && tabId != selectedTabId) {
                    selectedTabId = tabId
                    restoredContentLoaded = true
                    loadSelectedTabContent()
                } else if (tabId == null && selectedTabId != null) {
                    selectedTabId = null
                    restoredContentLoaded = true
                    loadSelectedTabContent()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            pdfReaderController.observePageChanges().collect { pageIndex ->
                val tabId = activePdfTabId ?: return@collect
                if (tabId != selectedTabId) return@collect
                if (tabs.none { it.tabId == tabId && it.fileType == FileType.PDF }) return@collect
                currentPdfPageIndex = pageIndex
                updatePdfPageControlState()
                schedulePdfPageRender(tabId, pageIndex, pdfContentState.generation, force = false)
            }
        }

        // Auto-open logic if initial path provided and not restored from state
        if (savedInstanceState == null && !initialPath.isNullOrBlank()) {
             val file = File(initialPath)
             if (file.exists() && file.isFile && isSupportedLocalFile(file.name)) {
                 val request = OpenRequest(
                    source = VfsPath.LocalFile(file.absolutePath),
                    fileName = file.name,
                    fileType = inferType(file.name),
                    versionStamp = initialVersionStampArg ?: buildLocalVersionStamp(file)
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
        openProgress.visibility = View.VISIBLE
        openProgress.isIndeterminate = true
        statusLabel.visibility = View.VISIBLE
        statusLabel.text = getString(R.string.reader_status_loading)
        statusLabel.setOnClickListener(null)
        
        viewLifecycleOwner.lifecycleScope.launch {
            var terminalStateReached = false
            try {
                readerViewModel.open(request).collect { state ->
                    when (state) {
                        is OpenState.Loading -> {
                            statusLabel.text = getString(R.string.reader_status_loading)
                            statusLabel.setOnClickListener(null)
                            openProgress.isIndeterminate = true
                        }
                        is OpenState.Copying -> {
                            statusLabel.setOnClickListener(null)
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
                            terminalStateReached = true
                            // selectedTabId = state.tab.tabId // Handled by currentTabId flow
                            statusLabel.visibility = View.GONE
                            statusLabel.setOnClickListener(null)
                            openProgress.visibility = View.GONE
                            // loadSelectedTabContent() // Handled by currentTabId flow
                        }
                         is OpenState.Error -> {
                             terminalStateReached = true
                             openProgress.visibility = View.GONE
                             // 类型化错误映射为本地化文案，让用户能区分「文件超限」与「活跃缓存占满」
                             val display = when (state.error) {
                                 is AppError.CacheLimitExceeded -> getString(R.string.reader_error_cache_limit)
                                 is AppError.CacheBusy -> getString(R.string.reader_error_cache_busy)
                                 else -> getString(R.string.reader_status_error)
                             }
                             reportOpenError(display, state.error.message ?: state.error.javaClass.simpleName)
                         }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                 terminalStateReached = true
                 // 展示通用错误文案，原始异常仅落日志（避免路径/异常栈暴露到对话框）
                 reportOpenError(getString(R.string.reader_status_error), e.message ?: e.javaClass.simpleName)
            } finally {
                opening = false
                // 未到达终态说明仍处于加载/拷贝中，收起进度；不再依赖本地化文案比较
                if (!terminalStateReached && view != null) {
                     statusLabel.visibility = View.GONE
                     openProgress.visibility = View.GONE
                }
            }
        }
    }

    /**
     * 展示用户可理解的摘要（displayMsg），完整错误仅落日志（fullDetails），避免路径/异常栈暴露到对话框。
     */
    private fun reportOpenError(displayMsg: String, fullDetails: String = displayMsg) {
        Log.w("ReaderFragment", "reader_open_error details=${UrlCredentialSanitizer.sanitizeText(fullDetails)}")
        setErrorState(displayMsg)
    }

    private fun reportPdfOpenError(fullDetails: String) {
        Log.w("ReaderFragment", "reader_pdf_open_error details=${UrlCredentialSanitizer.sanitizeText(fullDetails)}")
        setErrorState(getString(R.string.reader_pdf_open_error))
    }

    /**
     * 只展示用户可理解的摘要，完整错误仅落日志，避免路径/异常栈暴露到对话框。
     */
    private fun setErrorState(displayMsg: String) {
        statusLabel.text = displayMsg
        statusLabel.visibility = View.VISIBLE
        statusLabel.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.common_error_details_title)
                .setMessage(displayMsg)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.common_copy) { _, _ ->
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Error Message", displayMsg)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), getString(R.string.common_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun loadSelectedTabContent() {
        val generation = pdfContentState.beginContentLoad()
        val previousLoad = pdfLoadJob
        val previousRender = pdfRenderJob
        previousLoad?.cancel()
        pdfLoadJob = null
        previousRender?.cancel()
        pdfRenderJob = null
        activePdfTabId = null
        val exitJob = schedulePdfControllerExit(previousLoad, previousRender)

        val tab = tabs.firstOrNull { it.tabId == selectedTabId }
        if (tab == null) {
            resetPdfPreview()
            invalidateWebContent()
            webPreview.loadUrl("about:blank")
            webPreview.visibility = View.GONE
            return
        }
        if (tab.fileType == FileType.PDF) {
            loadSelectedPdfTab(tab, generation, exitJob)
            return
        }
        loadSelectedWebTab(tab)
    }

    private fun loadSelectedPdfTab(tab: ReaderTab, generation: Long, exitJob: Job?) {
        webPreview.visibility = View.GONE
        invalidateWebContent()
        webPreview.loadUrl("about:blank")
        pdfPreviewImage.visibility = View.VISIBLE
        pdfControls.visibility = View.VISIBLE

        val cachePath = tab.cacheFilePath
        if (cachePath.isNullOrBlank()) {
            resetPdfPreview()
            return
        }
        val file = File(cachePath)
        if (!file.exists() || !file.isFile) {
            resetPdfPreview()
            return
        }
        val controller = pdfReaderController
        val tabId = tab.tabId
        val lastKnownPageIndex = tab.lastKnownPageIndex
        pdfLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                exitJob?.join()
                if (!isCurrentPdfContent(tabId, generation)) return@launch
                controller.open(file)
                if (!isCurrentPdfContent(tabId, generation)) return@launch
                val pageCount = controller.getPageCount()
                if (!isCurrentPdfContent(tabId, generation)) return@launch
                pdfPageCount = pageCount
                val initialPage = if (lastKnownPageIndex >= 0) lastKnownPageIndex else 0
                val safeInitial = initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                controller.goToPage(safeInitial)
                if (!isCurrentPdfContent(tabId, generation)) return@launch
                activePdfTabId = tabId
                currentPdfPageIndex = safeInitial
                updatePdfPageControlState()
                // 同页码的 StateFlow 不会重复发射，首次渲染必须显式触发
                schedulePdfPageRender(tabId, safeInitial, generation, force = true)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                if (isCurrentPdfContent(tabId, generation)) {
                    resetPdfPreview()
                    reportPdfOpenError(e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    private fun loadSelectedWebTab(tab: ReaderTab) {
        pdfPageCount = 0
        currentPdfPageIndex = 0
        resetPdfPreview()
        pdfPreviewImage.visibility = View.GONE
        pdfControls.visibility = View.GONE
        webPreview.visibility = View.VISIBLE
        
        val cachePath = tab.cacheFilePath
        if (cachePath.isNullOrBlank()) {
            invalidateWebContent()
            webPreview.loadUrl("about:blank")
            return
        }
        val file = File(cachePath)
        if (!file.exists() || !file.isFile) {
            invalidateWebContent()
            webPreview.loadUrl("about:blank")
            val msg = getString(R.string.reader_file_not_found)
            Log.w("ReaderFragment", "reader_web_file_missing cachePath=$cachePath")
            setErrorState(msg)
            return
        }

        val targetUrl = file.toURI().toString()
        val isSameUrl = webPreview.url == targetUrl
        if (activeWebTabId == tab.tabId &&
            activeWebTargetUrl == targetUrl &&
            webProgressTracker != null
        ) {
            webProgressTracker?.startTracking()
            return
        }

        webProgressTracker?.stopAndFlush()
        webProgressTracker = null
        webRestoreJob?.cancel()
        webRestoreJob = null
        webContentGeneration++
        val contentGeneration = webContentGeneration
        webProgressSamplingState.beginContent(contentGeneration)
        webRestoreInteractionGeneration = null
        activeWebTabId = tab.tabId
        activeWebTargetUrl = targetUrl

        mhtmlFallbackRetriedTabIds.remove(tab.tabId)
        activeWebRenderProfile = initialRenderProfileFor(tab.fileType)
        WebViewConfigurator.configure(webPreview, profile = activeWebRenderProfile)
        bindWebViewClient(tab, targetUrl, contentGeneration)
        createWebProgressTracker(tab, file.name)

        if (isSameUrl) {
            startWebProgressRestore(tab, targetUrl, contentGeneration, loadedUrl = targetUrl)
        } else {
            webPreview.loadUrl(targetUrl)
        }
    }

    private fun bindWebViewClient(tab: ReaderTab, targetUrl: String, generation: Long) {
        webPreview.webViewClient = BlockingResourceWebViewClient(
            fileType = tab.fileType,
            allowedCacheRootPath = requireContext().cacheDir.resolve("app_cache").absolutePath,
            onOpenLinkInNewTab = NewTabLinkHandler { url ->
                if (tab.fileType == FileType.WEB) {
                    webPreview.loadUrl(url)
                } else {
                    showShort(getString(R.string.reader_external_link_blocked))
                }
            },
            onPageFinishedCallback = { loadedUrl ->
                onWebPageFinished(tab, loadedUrl, targetUrl, generation)
            }
        )
    }

    private fun createWebProgressTracker(tab: ReaderTab, fallbackTitle: String) {
        webProgressTracker = WebViewProgressTracker(
            webView = webPreview,
            sourcePathRaw = tab.sourcePathRaw,
            title = tab.title ?: fallbackTitle,
            fileType = tab.fileType,
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            onSnapshot = { snapshot -> webProgressWriter.enqueue(snapshot) }
        ).also {
            it.startTracking()
        }
    }

    private fun initialRenderProfileFor(fileType: FileType): WebViewConfigurator.RenderProfile {
        return if (fileType == FileType.MHTML || fileType == FileType.HTML) {
            WebViewConfigurator.RenderProfile.MHTML_DESKTOP
        } else {
            WebViewConfigurator.RenderProfile.DEFAULT
        }
    }

    private fun onWebPageFinished(
        tab: ReaderTab,
        loadedUrl: String,
        targetUrl: String,
        generation: Long
    ) {
        val request = WebProgressRestoreRequest(tab.tabId, generation, targetUrl)
        if (!isCurrentWebRestore(request, loadedUrl)) return
        if ((tab.fileType == FileType.MHTML || tab.fileType == FileType.HTML) &&
            maybeRetryMhtmlWithFallback(tab, loadedUrl)
        ) return
        startWebProgressRestore(tab, targetUrl, generation, loadedUrl)
    }

    private fun startWebProgressRestore(
        tab: ReaderTab,
        targetUrl: String,
        generation: Long,
        loadedUrl: String
    ) {
        val request = WebProgressRestoreRequest(tab.tabId, generation, targetUrl)
        val claimed = webProgressRestoreGate.tryClaim(
            request = request,
            selectedTabId = selectedTabId,
            generation = webContentGeneration,
            currentTargetUrl = activeWebTargetUrl,
            loadedUrl = loadedUrl,
            webViewUrl = webPreview.url,
            viewAlive = view != null
        )
        if (!claimed) return
        webRestoreJob?.cancel()
        webRestoreJob = viewLifecycleOwner.lifecycleScope.launch {
            restoreWebProgress(tab, request, loadedUrl)
        }
    }

    private suspend fun restoreWebProgress(
        tab: ReaderTab,
        request: WebProgressRestoreRequest,
        loadedUrl: String
    ) {
        var result = WebProgressRestoreResult.SUPERSEDED
        try {
            webProgressWriter.awaitWrites()
            if (!isCurrentWebRestore(request, loadedUrl)) return

            val history = historyRepository.getByPath(tab.sourcePathRaw)
            if (!isCurrentWebRestore(request, loadedUrl)) return
            val progress = history?.progress ?: tab.lastKnownProgress
            result = WebViewProgressRestorer.restore(
                progress = progress,
                readMetrics = ::readWebViewMetrics,
                isCurrent = { isCurrentWebRestore(request, loadedUrl) },
                userTookControl = { webRestoreInteractionGeneration == request.generation },
                scrollTo = { webPreview.scrollTo(0, it) }
            )
            if (isCurrentWebRestore(request, loadedUrl) &&
                webProgressSamplingState.onRestoreResult(request.generation, result)
            ) webProgressTracker?.enableSampling()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.w("ReaderFragment", "web_progress_restore_failed error=${error.javaClass.simpleName}")
        } finally {
            if (result != WebProgressRestoreResult.APPLIED &&
                result != WebProgressRestoreResult.USER_TOOK_CONTROL
            ) {
                webProgressRestoreGate.release(request)
            }
        }
    }

    private fun isCurrentWebRestore(
        request: WebProgressRestoreRequest,
        loadedUrl: String
    ): Boolean = WebProgressRestoreGuard.isCurrent(
        request = request,
        selectedTabId = selectedTabId,
        generation = webContentGeneration,
        currentTargetUrl = activeWebTargetUrl,
        loadedUrl = loadedUrl,
        viewAlive = view != null
    ) && webPreview.url?.startsWith(request.targetUrl) == true

    @Suppress("DEPRECATION")
    private fun readWebViewMetrics(): WebViewMetrics = WebViewMetrics(
        contentHeightPx = webPreview.contentHeight.toFloat(),
        viewportHeightPx = webPreview.height.toFloat(),
        scale = webPreview.scale,
        scrollY = webPreview.scrollY
    )

    private fun maybeRetryMhtmlWithFallback(tab: ReaderTab, loadedUrl: String): Boolean {
        if (activeWebRenderProfile != WebViewConfigurator.RenderProfile.MHTML_DESKTOP) {
            return false
        }
        if (mhtmlFallbackRetriedTabIds.contains(tab.tabId)) {
            return false
        }
        val viewWidth = webPreview.width
        if (viewWidth <= 0) {
            return false
        }
        if (selectedTabId != tab.tabId) {
            return false
        }
        val contentWidthPx = (webPreview as? MeasuringWebView)?.contentWidthPxScaled()?.toFloat()
            ?: return false
        if (contentWidthPx <= 0f) return false
        val narrow = contentWidthPx < viewWidth * 0.72f
        Log.d(
            "ReaderFragment",
            "mhtml_layout_check tab=${tab.tabId} viewWidth=$viewWidth contentWidthPx=$contentWidthPx narrow=$narrow profile=$activeWebRenderProfile"
        )
        if (!narrow) {
            return false
        }
        mhtmlFallbackRetriedTabIds.add(tab.tabId)
        webProgressTracker?.stopAndFlush()
        webProgressTracker?.pauseForRestore()
        webProgressTracker?.startTracking()
        activeWebRenderProfile = WebViewConfigurator.RenderProfile.MHTML_FALLBACK
        WebViewConfigurator.configure(webPreview, profile = activeWebRenderProfile)
        Log.d(
            "ReaderFragment",
            "mhtml_layout_fallback_reload tab=${tab.tabId} ${SafeUrlLogSummary.summarize(loadedUrl)}"
        )
        webPreview.reload()
        return true
    }

    private fun invalidateWebContent() {
        webRestoreJob?.cancel()
        webRestoreJob = null
        webContentGeneration++
        webProgressSamplingState.beginContent(webContentGeneration)
        webRestoreInteractionGeneration = null
        activeWebTabId = null
        activeWebTargetUrl = null
        webProgressTracker?.stopAndFlush()
        webProgressTracker = null
    }

    private fun schedulePdfPageRender(tabId: String, pageIndex: Int, generation: Long, force: Boolean) {
        if (view == null || !isCurrentPdfContent(tabId, generation)) return
        val key = PdfRenderKey(tabId, pageIndex)
        if (!pdfContentState.shouldSchedule(key, force)) return
        val width = if (pdfPreviewImage.width > 0) {
            pdfPreviewImage.width
        } else {
            resources.displayMetrics.widthPixels - 24
        }
        val controller = pdfReaderController
        val requestToken = ++pdfRenderRequestToken
        pdfRenderJob?.cancel()
        pdfContentState.markPending(key)
        pdfRenderJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bitmap = controller.renderPage(pageIndex, width)
                if (!isCurrentPdfContent(tabId, generation) || pageIndex != currentPdfPageIndex) {
                    if (!bitmap.isRecycled) bitmap.recycle()
                    return@launch
                }
                val previous = (pdfPreviewImage.drawable as? BitmapDrawable)?.bitmap
                pdfPreviewImage.setImageBitmap(bitmap)
                if (previous != null && previous !== bitmap && !previous.isRecycled) {
                    previous.recycle()
                }
                pdfContentState.markApplied(key)
                recordPdfProgress(tabId, pageIndex)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                if (isCurrentPdfContent(tabId, generation) && pageIndex == currentPdfPageIndex) {
                    reportPdfOpenError(e.message ?: e.javaClass.simpleName)
                }
            } finally {
                if (requestToken == pdfRenderRequestToken) {
                    pdfContentState.clearPending()
                }
            }
        }
    }

    private suspend fun recordPdfProgress(tabId: String, pageIndex: Int) {
        val tab = tabs.firstOrNull { it.tabId == tabId && it.fileType == FileType.PDF } ?: return
        if (tabId != selectedTabId || pdfPageCount <= 0) return
        val denominator = (pdfPageCount - 1).coerceAtLeast(1)
        val progress = (pageIndex.toFloat() / denominator).coerceIn(0f, 1f)
        historyRepository.updateProgress(tab.sourcePathRaw, progress, pageIndex)
    }

    private fun isCurrentPdfContent(tabId: String, generation: Long): Boolean =
        pdfContentState.isCurrent(generation) &&
            selectedTabId == tabId &&
            view != null &&
            tabs.any { it.tabId == tabId && it.fileType == FileType.PDF }

    private fun schedulePdfControllerExit(previousLoad: Job?, previousRender: Job?): Job? {
        if (!::pdfReaderController.isInitialized) return pdfExitJob
        val exitJob = ReaderRuntime.closePdfControllerAfter(
            previous = pdfExitJob,
            controller = pdfReaderController,
            pendingJobs = listOfNotNull(previousLoad, previousRender).distinct()
        )
        pdfExitJob = exitJob
        return exitJob
    }

    private fun resetPdfPreview() {
        pdfPageCount = 0
        currentPdfPageIndex = 0
        clearPdfPreviewImage()
        updatePdfPageControlState()
    }

    private fun clearPdfPreviewImage() {
        val bitmap = (pdfPreviewImage.drawable as? BitmapDrawable)?.bitmap
        pdfPreviewImage.setImageDrawable(null)
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }

    private fun updatePdfPageControlState() {
        val hasPdf = pdfPageCount > 0
        pdfPrevButton.isEnabled = hasPdf && currentPdfPageIndex > 0
        pdfNextButton.isEnabled = hasPdf && currentPdfPageIndex < pdfPageCount - 1
        val shownPage = if (hasPdf) currentPdfPageIndex + 1 else 0
        val total = if (hasPdf) pdfPageCount else 0
        pdfPageInfoLabel.text = getString(R.string.reader_pdf_page_template, shownPage, total)
    }

    private fun inferType(fileName: String): FileType = InferFileTypeUseCase.infer(fileName)

    private fun isSupportedLocalFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "pdf" || ext == "mhtml" || ext == "mht"
    }

    private fun buildLocalVersionStamp(file: File): String? {
        if (!file.exists() || !file.isFile) {
            return null
        }
        return "${file.lastModified().coerceAtLeast(0L)}:${file.length().coerceAtLeast(0L)}"
    }

    private fun showShort(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun installWebLongPressHandler() {
        webPreview.setOnLongClickListener {
            when (val hitType = webPreview.hitTestResult.type) {
                WebView.HitTestResult.IMAGE_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val imageSource = webPreview.hitTestResult.extra
                    if (imageSource.isNullOrBlank()) {
                        return@setOnLongClickListener false
                    }
                    showImageActions(imageSource)
                    true
                }
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    val linkUrl = webPreview.hitTestResult.extra
                    // 仅对绝对 http/https 链接提供外部打开；相对链接/页内锚点不处理
                    if (linkUrl.isNullOrBlank() || !isExternalHttpLink(linkUrl)) {
                        return@setOnLongClickListener false
                    }
                    showLinkActions(linkUrl)
                    true
                }
                else -> {
                    Log.d("ReaderFragment", "web_long_press_unhandled type=$hitType")
                    false
                }
            }
        }
    }

    private fun isExternalHttpLink(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private fun showLinkActions(linkUrl: String) {
        AlertDialog.Builder(requireContext())
            .setItems(arrayOf(getString(R.string.reader_open_in_browser_action))) { _, which ->
                if (which != 0) {
                    return@setItems
                }
                openInDefaultBrowser(linkUrl)
            }
            .show()
    }

    private fun openInDefaultBrowser(linkUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w("ReaderFragment", "open_in_browser_failed cause=${e.javaClass.simpleName} ${SafeUrlLogSummary.summarize(linkUrl)}")
            showShort(getString(R.string.reader_open_in_browser_failed))
        }
    }

    private fun showImageActions(imageSource: String) {
        AlertDialog.Builder(requireContext())
            .setItems(arrayOf(getString(R.string.reader_save_image_action))) { _, which ->
                if (which != 0) {
                    return@setItems
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val context = requireContext().applicationContext
                        val payload = withContext(Dispatchers.IO) {
                            resolveImagePayload(imageSource)
                        }
                        withContext(Dispatchers.IO) {
                            saveImagePayload(context, payload)
                        }
                        showShort(getString(R.string.reader_save_image_success, payload.fileName))
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        val message = t.message?.ifBlank { getString(R.string.reader_save_image_failed) }
                            ?: getString(R.string.reader_save_image_failed)
                        showShort(getString(R.string.reader_save_image_failed_reason, message))
                    }
                }
            }
            .show()
    }

    private fun resolveImagePayload(source: String): ImagePayload {
        return when {
            source.startsWith("data:", ignoreCase = true) -> resolveDataUrlPayload(source)
            source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true) -> resolveHttpPayload(source)
            else -> throw IllegalArgumentException(getString(R.string.reader_save_image_unsupported_source))
        }
    }

    private fun resolveHttpPayload(source: String): ImagePayload {
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            instanceFollowRedirects = true
            doInput = true
        }
        connection.connect()
        val contentLength = connection.contentLengthLong
        if (contentLength > MAX_IMAGE_BYTES) {
            throw IllegalStateException(getString(R.string.reader_save_image_failed_reason, "Image too large"))
        }
        connection.inputStream.use { stream ->
            val bytes = stream.readWithSizeLimit(MAX_IMAGE_BYTES)
            if (bytes.isEmpty()) {
                throw IllegalStateException(getString(R.string.reader_save_image_empty_data))
            }
            val mimeType = normalizeMimeType(connection.contentType)
            val fileName = buildHttpFileName(source, mimeType)
            return ImagePayload(bytes = bytes, mimeType = mimeType, fileName = fileName)
        }
    }

    private fun resolveDataUrlPayload(source: String): ImagePayload {
        val commaIndex = source.indexOf(',')
        if (commaIndex <= 5) {
            throw IllegalArgumentException(getString(R.string.reader_save_image_invalid_data_url))
        }
        val metadata = source.substring(5, commaIndex)
        val payload = source.substring(commaIndex + 1)
        val mimeTypeToken = metadata.substringBefore(';').takeIf { it.contains('/') }
        val mimeType = normalizeMimeType(mimeTypeToken)
        val isBase64 = metadata.split(';').any { it.equals("base64", ignoreCase = true) }
        val bytes = if (isBase64) {
            val estimatedSize = payload.length.toLong() * 3 / 4
            if (estimatedSize > MAX_IMAGE_BYTES) {
                throw IllegalStateException(getString(R.string.reader_save_image_failed_reason, "Image too large"))
            }
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            val decoded = Uri.decode(payload)
            if (decoded.length > MAX_IMAGE_BYTES) {
                throw IllegalStateException(getString(R.string.reader_save_image_failed_reason, "Image too large"))
            }
            decoded.toByteArray(Charsets.UTF_8)
        }
        if (bytes.isEmpty()) {
            throw IllegalStateException(getString(R.string.reader_save_image_empty_data))
        }
        val extension = extensionFromMimeType(mimeType)
        val fileName = "image_${System.currentTimeMillis()}.$extension"
        return ImagePayload(bytes = bytes, mimeType = mimeType, fileName = fileName)
    }

    private fun java.io.InputStream.readWithSizeLimit(maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw IllegalStateException(getString(R.string.reader_save_image_failed_reason, "Image too large"))
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun saveImagePayload(context: Context, payload: ImagePayload) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, payload.fileName)
            put(MediaStore.Images.Media.MIME_TYPE, payload.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MHTMLReader")
        }
        val itemUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException(getString(R.string.reader_save_image_failed))
        try {
            resolver.openOutputStream(itemUri)?.use { outputStream ->
                outputStream.write(payload.bytes)
            } ?: throw IllegalStateException(getString(R.string.reader_save_image_failed))
        } catch (error: Exception) {
            resolver.delete(itemUri, null, null)
            throw error
        }
    }

    private fun normalizeMimeType(contentType: String?): String {
        val normalized = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return if (normalized.startsWith("image/")) normalized else "image/jpeg"
    }

    private fun buildHttpFileName(source: String, mimeType: String): String {
        val suggested = Uri.parse(source).lastPathSegment
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.ifBlank { null }
        val extension = extensionFromMimeType(mimeType)
        val baseName = sanitizeFileName(suggested ?: "image_${System.currentTimeMillis()}")
        return if (baseName.contains('.')) baseName else "$baseName.$extension"
    }

    private fun sanitizeFileName(value: String): String {
        val cleaned = value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return cleaned.ifBlank { "image_${System.currentTimeMillis()}" }
    }

    private fun extensionFromMimeType(mimeType: String): String {
        return when (mimeType.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            "image/svg+xml" -> "svg"
            else -> "jpg"
        }
    }

    override fun onResume() {
        super.onResume()
        webProgressTracker?.startTracking()
        if (webProgressSamplingState.onResume(webContentGeneration)) {
            webProgressTracker?.enableSampling()
        }
    }

    override fun onPause() {
        super.onPause()
        webProgressSamplingState.onPause()
        webProgressTracker?.stopTracking()
    }

    override fun onDestroyView() {
        val previousLoad = pdfLoadJob
        val previousRender = pdfRenderJob
        previousLoad?.cancel()
        pdfLoadJob = null
        previousRender?.cancel()
        pdfRenderJob = null
        pdfRenderRequestToken += 1
        pdfContentState.invalidate()
        activePdfTabId = null
        if (::pdfPreviewImage.isInitialized) {
            clearPdfPreviewImage()
        }
        if (::pdfReaderController.isInitialized) {
            // Fragment 销毁后由应用级任务等待旧 load/render，再关闭捕获的 controller。
            schedulePdfControllerExit(previousLoad, previousRender)
        }
        invalidateWebContent()
        webPreview.loadUrl("about:blank")
        webPreview.stopLoading()
        webPreview.webViewClient = WebViewClient()
        webPreview.destroy()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_TAB_ID, selectedTabId)
        super.onSaveInstanceState(outState)
    }
}
