package com.html_reader

import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textfield.TextInputLayout
import com.html_reader.files.BrowserEntry
import com.html_reader.files.FilesListItemResolver
import com.html_reader.files.FilesUiBinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
class FilesCompactLayoutTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun compactHeaderKeepsSearchOptionsAndListViewportAvailable() {
        val context = themedContext(fontScale = 1f)
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_files, null)
        val list = root.findViewById<ListView>(R.id.files_list)
        val header = LayoutInflater.from(context).inflate(R.layout.item_files_parent, list, false)
        list.addHeaderView(header, null, false)
        list.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            mutableListOf(entry("first.mhtml"))
        )
        val views = FilesUiBinder.bind(root, header)

        assertSame(header.findViewById<Button>(R.id.files_action_up), views.actionUpButton)
        assertEquals(R.id.files_sort_spinner, views.sortSpinner.id)
        assertEquals(R.id.files_font_size_spinner, views.fontSizeSpinner.id)
        val width = dp(context, 360)
        val contentHeight = dp(context, 640)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(contentHeight, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, contentHeight)

        val fixedTop = list.top - root.paddingTop
        val fileViewport = list.measuredHeight - header.measuredHeight
        assertTrue("Fixed top used $fixedTop px", fixedTop <= dp(context, 160))
        assertTrue("List viewport should dominate the content", list.measuredHeight >= contentHeight * 0.65f)
        assertTrue("File area should remain useful below its header", fileViewport >= contentHeight * 0.55f)
        assertTrue(views.queryInput.height >= dp(context, 48))
        assertTrue(views.actionCreateButton.minimumHeight >= dp(context, 48))
        assertTrue(views.currentDirLabel.minimumHeight >= dp(context, 48))
        assertTrue(views.displayOptionsPanel.visibility != View.VISIBLE)
        assertEquals(1, list.headerViewsCount)
        assertEquals(2, list.count)
    }

    @Test
    fun listHeaderIsNotResolvedAsAnEntryForClickOrLongPress() {
        val context = themedContext(fontScale = 1f)
        val list = ListView(context)
        val header = LayoutInflater.from(context).inflate(R.layout.item_files_parent, list, false)
        list.addHeaderView(header, null, false)
        val firstEntry = entry("first.mhtml")
        list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, mutableListOf(firstEntry))

        assertTrue(header.findViewById<Button>(R.id.files_action_up).minimumHeight >= dp(context, 48))
        assertNull(FilesListItemResolver.entryAt(list, 0))
        assertSame(firstEntry, FilesListItemResolver.entryAt(list, 1))

        list.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            mutableListOf<BrowserEntry>()
        )
        assertEquals(1, list.count)
        assertNull(FilesListItemResolver.entryAt(list, 0))
    }

    @Test
    fun narrowLargeFontKeepsSearchUploadAndDisplayActionsReachable() {
        val context = themedContext(fontScale = 1.5f)
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_files, null)
        val list = root.findViewById<ListView>(R.id.files_list)
        val header = LayoutInflater.from(context).inflate(R.layout.item_files_parent, list, false)
        list.addHeaderView(header, null, false)
        val views = FilesUiBinder.bind(root, header)
        val width = dp(context, 320)
        val height = dp(context, 640)

        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)

        assertTrue(views.queryInput.width >= dp(context, 96))
        assertTrue(views.actionCreateButton.minimumHeight >= dp(context, 48))
        assertTrue(views.currentDirLabel.minimumHeight >= dp(context, 48))
        assertTrue(views.displayOptionsButton.minimumHeight >= dp(context, 48))
        val searchContainer = views.queryInput.parent.parent as TextInputLayout
        assertTrue(
            "Search right=${searchContainer.right}, action left=${views.actionCreateButton.left}",
            searchContainer.right <= views.actionCreateButton.left
        )
        assertTrue(views.currentDirLabel.right <= views.displayOptionsButton.left)
    }

    @Test
    fun tappingParentHeaderNavigatesUpOneLocalDirectory() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = controller.get()
            val rootDir = tempFolder.newFolder("root")
            val nestedDir = java.io.File(rootDir, "nested").apply { mkdirs() }
            activity.showDirectoryMode(fromFolders = true)
            controller.start().resume()
            idle()

            val fragment = activity.supportFragmentManager.fragments
                .filterIsInstance<FilesFragment>()
                .last()
            fragment.currentDir = nestedDir
            fragment.loadEntries()
            idle()

            assertTrue(fragment.actionUpButton.isEnabled)
            assertTrue(fragment.actionUpButton.performClick())
            idle()
            assertEquals(rootDir.absolutePath, fragment.currentDir?.absolutePath)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun entry(name: String) = BrowserEntry(
        name = name,
        isDirectory = false,
        sizeBytes = 1,
        modifiedEpochMs = 1L
    )

    private fun themedContext(fontScale: Float): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(base.resources.configuration).apply {
            this.fontScale = fontScale
        }
        return ContextThemeWrapper(
            base.createConfigurationContext(configuration),
            R.style.Theme_MHTMLReader
        )
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun idle(cycles: Int = 30) {
        repeat(cycles) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }
}
