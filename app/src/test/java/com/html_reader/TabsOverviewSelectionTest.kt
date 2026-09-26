package com.html_reader

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import core.database.entity.enums.FileType
import core.reader.model.OpenRequest
import core.vfs.model.VfsPath
import java.io.File
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowEnvironmentPermissions::class])
class TabsOverviewSelectionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun idle(cycles: Int = 30) {
        repeat(cycles) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun tabsOverview_highlightsCurrentTabNotTopItem() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        controller.start().resume()
        idle()

        val first = File(tempFolder.root, "a.mhtml").apply { writeText("<html>a</html>") }
        val second = File(tempFolder.root, "b.mhtml").apply { writeText("<html>b</html>") }

        val viewModel = ReaderRuntime.viewModel(context)
        runBlocking {
            viewModel.open(openRequest(first)).collect { }
            viewModel.open(openRequest(second)).collect { }
        }
        idle()

        val firstTabId = viewModel.tabs.value.first { it.title == "a.mhtml" }.tabId
        runBlocking { viewModel.switchTo(firstTabId) }
        idle()

        activity.findViewById<BottomNavigationView>(R.id.main_bottom_nav).selectedItemId = R.id.nav_reader
        idle()

        val tabsFragment = activity.supportFragmentManager.fragments
            .filterIsInstance<TabsOverviewFragment>()
            .lastOrNull()
        assertNotNull(tabsFragment)
        idle()

        val listView = tabsFragment!!.view?.findViewById<android.widget.ListView>(R.id.tabs_list)
        assertNotNull(listView)
        // 列表按最新在前排序，当前 tab 是较早打开的 a，应高亮索引 1 而不是最上的索引 0
        val titles = viewModel.tabs.value.reversed().map { it.title }
        assertEquals(listOf("b.mhtml", "a.mhtml"), titles)
        assertEquals(1, listView!!.checkedItemPosition)

        runBlocking { viewModel.closeAll() }
    }

    private fun openRequest(file: File) = OpenRequest(
        source = VfsPath.LocalFile(file.absolutePath),
        fileName = file.name,
        fileType = FileType.MHTML,
        versionStamp = "${file.lastModified()}:${file.length()}"
    )
}
