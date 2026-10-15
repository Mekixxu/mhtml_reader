package com.html_reader

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import core.common.DispatcherProvider
import core.data.repo.HistoryRepository
import core.database.AppDatabase
import core.database.entity.enums.FileType
import core.reader.web.WebProgressSamplingState
import core.reader.web.WebProgressSnapshot
import core.reader.web.WebViewProgressTracker
import core.reader.web.WebViewProgressWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 通过 Fragment 安装的生产 OnTouchListener 驱动触摸序列，覆盖 Q1 有效手势接管：
 * 轻触/抖动/CANCEL/零尺寸不得取消恢复或放行采样；超过 touchSlop 的有效拖动才接管。
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowEnvironmentPermissions::class])
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderFragmentGestureTakeoverTest {

    @Test
    fun tapBelowTouchSlopKeepsRestoredHistory() = runTest {
        val result = exercise(
            contentHeightPx = 2_000,
            pointerPath = pointerPath(dragPx = 2f),
            simulatedCurrentProgress = 0f
        )
        assertNull(result.takeoverGeneration)
        assertEquals(0.6f, result.progress, 0.001f)
    }

    @Test
    fun intentionalDragTakesOverAndSavesNewPosition() = runTest {
        val result = exercise(
            contentHeightPx = 2_000,
            pointerPath = pointerPath(dragPx = 400f),
            simulatedCurrentProgress = 1f
        )
        assertEquals(1L, result.takeoverGeneration)
        assertEquals(1f, result.progress, 0.001f)
    }

    @Test
    fun dragOnZeroSizedContentDoesNotUnlockSampling() = runTest {
        val result = exercise(
            contentHeightPx = 0,
            pointerPath = pointerPath(dragPx = 400f),
            simulatedCurrentProgress = 0f
        )
        assertNull(result.takeoverGeneration)
        assertEquals(0.6f, result.progress, 0.001f)
    }

    @Test
    fun cancelBeforeDragDoesNotTakeOver() = runTest {
        val result = exercise(
            contentHeightPx = 2_000,
            pointerPath = listOf(
                PointerAction(MotionEvent.ACTION_DOWN, 100f, 100f),
                PointerAction(MotionEvent.ACTION_CANCEL, 100f, 100f),
                PointerAction(MotionEvent.ACTION_MOVE, 100f, 500f)
            ),
            simulatedCurrentProgress = 0f
        )
        assertNull(result.takeoverGeneration)
        assertEquals(0.6f, result.progress, 0.001f)
    }

    private data class PointerAction(val action: Int, val x: Float, val y: Float)

    private data class ExerciseResult(val takeoverGeneration: Long?, val progress: Float)

    private fun pointerPath(dragPx: Float): List<PointerAction> = listOf(
        PointerAction(MotionEvent.ACTION_DOWN, 100f, 100f),
        PointerAction(MotionEvent.ACTION_MOVE, 100f, 100f + dragPx),
        PointerAction(MotionEvent.ACTION_UP, 100f, 100f + dragPx)
    )

    private suspend fun TestScope.exercise(
        contentHeightPx: Int,
        pointerPath: List<PointerAction>,
        simulatedCurrentProgress: Float
    ): ExerciseResult {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(ViewConfiguration.get(context).scaledTouchSlop > 0)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        try {
            val repository = repository(db)
            repository.saveWebProgress(PATH, "test", FileType.MHTML, 0.6f)
            val fragment = ReaderFragment()
            activity.get().supportFragmentManager.beginTransaction()
                .add(fragment, "gesture-test").commitNow()
            assertNotNull(fragment.view)

            // 生产监听器安装在布局自带的 WebView 上；测试替换 Fragment 的 webPreview 字段提供可控尺寸。
            val touchTarget = fragment.requireView().findViewById<View>(R.id.reader_web_preview)
            val web = MetricsWebView(context, contentHeightPx)
            measure(web)

            val writer = WebViewProgressWriter(this, repository)
            val tracker = WebViewProgressTracker(
                webView = web,
                sourcePathRaw = PATH,
                title = "test",
                fileType = FileType.MHTML,
                coroutineScope = this,
                onSnapshot = { writer.enqueue(it) },
                readCurrentSnapshot = {
                    WebProgressSnapshot(PATH, "test", FileType.MHTML, simulatedCurrentProgress)
                }
            )
            configure(fragment, web, repository, writer, tracker)

            val listener = productionTouchListener(touchTarget)
            assertNotNull("production OnTouchListener must be installed", listener)
            pointerPath.forEach { action ->
                val event = MotionEvent.obtain(0L, 0L, action.action, action.x, action.y, 0)
                try {
                    listener!!.onTouch(touchTarget, event)
                } finally {
                    event.recycle()
                }
            }

            activity.pause()
            writer.awaitWrites()
            return ExerciseResult(
                takeoverGeneration = field(fragment, "webRestoreInteractionGeneration") as Long?,
                progress = db.historyDao().getByPath(PATH)!!.progress
            )
        } finally {
            activity.pause().stop().destroy()
            db.close()
        }
    }

    /**
     * 通过反射取回 View 已安装的生产 OnTouchListener（框架未提供公开 getter）。
     */
    private fun productionTouchListener(target: View): View.OnTouchListener? {
        val listenerInfo = View::class.java.getDeclaredField("mListenerInfo")
            .apply { isAccessible = true }
            .get(target) ?: return null
        return listenerInfo.javaClass.getDeclaredField("mOnTouchListener")
            .apply { isAccessible = true }
            .get(listenerInfo) as View.OnTouchListener?
    }

    @Suppress("DEPRECATION")
    private fun measure(web: WebView) {
        web.measure(
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
        )
        web.layout(0, 0, 500, 500)
        web.bottom = 500
        web.right = 500
        assertEquals(500, web.height)
    }

    private fun TestScope.repository(db: AppDatabase): HistoryRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = object : DispatcherProvider {
            override val io = dispatcher
            override val default = dispatcher
            override val main = dispatcher
        }
        return HistoryRepository(db.historyDao(), provider)
    }

    private fun configure(
        fragment: ReaderFragment, web: WebView, repository: HistoryRepository,
        writer: WebViewProgressWriter, tracker: WebViewProgressTracker
    ) {
        setField(fragment, "webPreview", web)
        setField(fragment, "historyRepository", repository)
        setField(fragment, "webProgressWriter", writer)
        setField(fragment, "webProgressTracker", tracker)
        setField(fragment, "selectedTabId", "tab")
        setField(fragment, "activeWebTargetUrl", URL)
        setField(fragment, "webContentGeneration", 1L)
        val state = field(fragment, "webProgressSamplingState") as WebProgressSamplingState
        state.beginContent(1L)
        state.onResume(1L)
        tracker.startTracking()
    }

    private fun field(fragment: ReaderFragment, name: String): Any? =
        ReaderFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.get(fragment)

    private fun setField(fragment: ReaderFragment, name: String, value: Any) {
        ReaderFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.set(fragment, value)
    }

    private class MetricsWebView(context: Context, private val contentHeightPx: Int) : WebView(context) {
        override fun getContentHeight(): Int = contentHeightPx

        @Suppress("DEPRECATION")
        override fun getScale(): Float = 1f
    }

    private companion object {
        const val PATH = "/docs/gesture.mhtml"
        const val URL = "file:///cache/gesture.mhtml"
    }
}
