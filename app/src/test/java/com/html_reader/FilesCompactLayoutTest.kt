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

    @Test
    fun toolbarControlsShareHeightAndColumnEdgesWithoutSearchUnderline() {
        for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            for (fontScale in listOf(1f, 1.5f, 2f)) {
                val base = ApplicationProvider.getApplicationContext<Context>()
                val config = Configuration(base.resources.configuration).apply {
                    this.fontScale = fontScale
                    uiMode = Configuration.UI_MODE_TYPE_NORMAL or night
                }
                val context = ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_MHTMLReader)
                val root = LayoutInflater.from(context).inflate(R.layout.fragment_files, null)
                val query = root.findViewById<View>(R.id.files_query_input)
                val search = query.parent.parent as TextInputLayout
                val action = root.findViewById<Button>(R.id.files_action_create)
                val path = root.findViewById<View>(R.id.files_current_dir)
                val display = root.findViewById<Button>(R.id.files_display_options)
                val width = dp(context, 320)
                val height = dp(context, 640)
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                assertEquals(dp(context, 48), search.height)
                assertEquals(search.height, action.height)
                assertEquals(search.height, path.height)
                assertEquals(search.height, display.height)
                assertEquals(search.right, path.right)
                assertEquals(action.left, display.left)
                assertEquals(action.right, display.right)
                assertEquals(0, search.boxStrokeWidth)
                assertEquals(0, search.boxStrokeWidthFocused)
                assertTrue(query.width >= dp(context, 96))
            }
        }
    }

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

    @Test
    fun operationDisabledUpStaysDisabledAcrossDirectoryRefreshAndRemotePreparation() {
        val controller = launchFilesActivity()
        try {
            val fragment = currentFilesFragment(controller.get())
            val parent = tempFolder.newFolder("refreshParent")
            fragment.currentDir = java.io.File(parent, "child").apply { mkdir() }
            val currentPath = fragment.currentDir?.absolutePath

            fragment.setOperationButtonsEnabled(false)
            fragment.updateCurrentDirLabel()
            fragment.prepareRemoteBrowse(R.string.action_upload_file)

            assertTrue("Refresh must not re-enable Up during an operation", !fragment.actionUpButton.isEnabled)
            assertTrue(fragment.navigateUp())
            assertEquals(currentPath, fragment.currentDir?.absolutePath)

            fragment.setOperationButtonsEnabled(true)
            assertTrue(fragment.actionUpButton.isEnabled)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun operationLockConsumesUpAndDirectoryRowNavigationWithoutChangingPath() {
        val controller = launchFilesActivity()
        try {
            val fragment = currentFilesFragment(controller.get())
            val parent = tempFolder.newFolder("lockedParent")
            val current = java.io.File(parent, "current").apply { mkdir() }
            java.io.File(current, "child").mkdir()
            fragment.currentDir = current
            fragment.loadEntries()
            idle()

            assertTrue(fragment.tryAcquireOperationLock())
            assertTrue(fragment.navigateUp())
            fragment.listView.onItemClickListener!!.onItemClick(fragment.listView, null, 1, 0L)

            assertEquals(current.absolutePath, fragment.currentDir?.absolutePath)
            assertTrue(!fragment.actionUpButton.isEnabled)
            fragment.releaseOperationLock()
            assertTrue(fragment.actionUpButton.isEnabled)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun systemBackDuringOperationDoesNotMoveUpOrLeaveFilesAtRoot() {
        val controller = launchFilesActivity()
        try {
            val activity = controller.get()
            val fragment = currentFilesFragment(activity)
            val parent = tempFolder.newFolder("backParent")
            val nested = java.io.File(parent, "nested").apply { mkdir() }
            fragment.currentDir = nested
            fragment.updateCurrentDirLabel()
            assertTrue(fragment.tryAcquireOperationLock())

            activity.onBackPressedDispatcher.onBackPressed()
            idle()

            assertSame(fragment, currentFilesFragment(activity))
            assertTrue(fragment.isVisible)
            assertEquals(nested.absolutePath, fragment.currentDir?.absolutePath)
            fragment.releaseOperationLock()

            fragment.currentDir = java.io.File("/")
            fragment.updateCurrentDirLabel()
            val pathBeforeBack = fragment.currentDir?.absolutePath
            assertTrue(fragment.tryAcquireOperationLock())

            activity.onBackPressedDispatcher.onBackPressed()
            idle()

            assertSame(fragment, currentFilesFragment(activity))
            assertTrue(fragment.isVisible)
            assertEquals(pathBeforeBack, fragment.currentDir?.absolutePath)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun ftpAndSmbRootStayDisabledWhenConfigurationIsMissing() {
        val controller = launchFilesActivity()
        try {
            val fragment = currentFilesFragment(controller.get())
            fragment.remoteController.syncFromSession(
                ftpConfig = null,
                ftpResolvedCharset = null,
                ftpCurrentPath = "/",
                smbConfig = null,
                smbCurrentPath = "/"
            )

            fragment.browseSource = com.html_reader.files.BrowseSource.FTP
            fragment.updateCurrentDirLabel()
            assertTrue("FTP root without config must not expose Up", !fragment.actionUpButton.isEnabled)

            fragment.browseSource = com.html_reader.files.BrowseSource.SMB
            fragment.updateCurrentDirLabel()
            assertTrue("SMB root without config must not expose Up", !fragment.actionUpButton.isEnabled)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun systemBackWhenParentIsInaccessibleKeepsFilesVisible() {
        val controller = launchFilesActivity()
        try {
            val activity = controller.get()
            val fragment = currentFilesFragment(activity)
            val parent = tempFolder.newFolder("deniedParent")
            val child = java.io.File(parent, "child").apply { mkdir() }
            val deniedParent = ParentWithoutListing(parent.absolutePath)
            fragment.currentDir = DirectoryWithParent(child.absolutePath, deniedParent)
            fragment.updateCurrentDirLabel()

            activity.onBackPressedDispatcher.onBackPressed()
            idle()

            assertSame(fragment, currentFilesFragment(activity))
            assertTrue(fragment.isVisible)
            assertEquals(child.absolutePath, fragment.currentDir?.absolutePath)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun launchFilesActivity() =
        Robolectric.buildActivity(MainActivity::class.java).create().also { controller ->
            controller.get().showDirectoryMode(fromFolders = true)
            controller.start().resume()
            controller.visible()
            idle()
        }

    private fun currentFilesFragment(activity: MainActivity): FilesFragment =
        activity.supportFragmentManager.fragments.filterIsInstance<FilesFragment>().last()

    private class ParentWithoutListing(path: String) : java.io.File(path) {
        override fun listFiles(): Array<java.io.File>? = null
    }

    private class DirectoryWithParent(
        path: String,
        private val parent: java.io.File
    ) : java.io.File(path) {
        override fun getParentFile(): java.io.File = parent
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
