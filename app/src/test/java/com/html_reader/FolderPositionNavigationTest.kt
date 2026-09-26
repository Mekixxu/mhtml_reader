package com.html_reader

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.io.File
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
class FolderPositionNavigationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun idle(cycles: Int = 30) {
        repeat(cycles) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun openFileThenReturnToFolders_restoresNestedDirectory() {
        // 仅 create，先替换掉 HomeFragment（其 isExternalStorageManager 在 Robolectric 下不可用），再 start/resume
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val root = tempFolder.newFolder("root")
        val nested = File(root, "a/b").apply { mkdirs() }
        val doc = File(nested, "doc.mhtml").apply { writeText("<html><title>doc</title></html>") }

        runBlocking {
            val repo = FilesRuntime.folderSessionRepository(context)
            val id = repo.add("session", root.absolutePath)
            repo.updateCurrentDir(id, nested.absolutePath)
            FilesRuntime.currentSessionStore(context).set(id)
        }

        activity.showDirectoryMode(fromFolders = true)
        controller.start().resume()
        idle()

        val first = activity.supportFragmentManager.fragments
            .filterIsInstance<FilesFragment>()
            .lastOrNull()
        assertNotNull(first)
        idle()
        assertEquals(nested.absolutePath, first!!.currentDir?.absolutePath)

        // 点击文件打开（进入 reader，目录 fragment 被 replace 销毁）
        activity.showReaderModeWithPath(doc.absolutePath)
        idle()

        // 返回 folders（等价于点击底部 Folders 卡片）
        activity.showDirectoryMode(fromFolders = true)
        idle()

        val second = activity.supportFragmentManager.fragments
            .filterIsInstance<FilesFragment>()
            .lastOrNull()
        assertNotNull(second)
        idle()
        assertEquals(nested.absolutePath, second!!.currentDir?.absolutePath)
    }

    @Test
    fun reopenLocalFolderCard_resumesNestedDirectory() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val root = tempFolder.newFolder("cardRoot")
        val nested = File(root, "sub/deep").apply { mkdirs() }
        val doc = File(nested, "doc.mhtml").apply { writeText("<html></html>") }

        runBlocking {
            val repo = FilesRuntime.folderSessionRepository(context)
            val id = repo.add("cardSession", root.absolutePath)
            repo.updateCurrentDir(id, root.absolutePath)
            FilesRuntime.currentSessionStore(context).set(id)
        }

        // 从 Home 目录卡片进入
        activity.showDirectoryModeWithPath(root.absolutePath)
        controller.start().resume()
        idle()

        val first = activity.supportFragmentManager.fragments
            .filterIsInstance<FilesFragment>()
            .lastOrNull()
        assertNotNull(first)
        idle()

        first!!.currentDir = nested
        first.loadEntries()
        first.persistCurrentDir()
        idle()

        activity.showReaderModeWithPath(doc.absolutePath)
        idle()

        // 再次点击同一个 Home 目录卡片
        activity.showDirectoryModeWithPath(root.absolutePath)
        idle()

        val second = activity.supportFragmentManager.fragments
            .filterIsInstance<FilesFragment>()
            .lastOrNull()
        assertNotNull(second)
        idle()
        assertEquals(nested.absolutePath, second!!.currentDir?.absolutePath)
    }

    @Test
    fun browseIntoDirThenOpenFileImmediately_keepsPersistedDir() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val root = tempFolder.newFolder("rootRace")
        val nested = File(root, "nested").apply { mkdirs() }
        val doc = File(nested, "doc.mhtml").apply { writeText("<html></html>") }

        runBlocking {
            val repo = FilesRuntime.folderSessionRepository(context)
            val id = repo.add("raceSession", root.absolutePath)
            repo.updateCurrentDir(id, root.absolutePath)
            FilesRuntime.currentSessionStore(context).set(id)
        }

        activity.showDirectoryMode(fromFolders = true)
        controller.start().resume()
        idle()

        val first = activity.supportFragmentManager.fragments
            .filterIsInstance<FilesFragment>()
            .lastOrNull()
        assertNotNull(first)
        idle()

        // 模拟点击目录（UI handler 顺序：改 currentDir → loadEntries → persistCurrentDir）
        first!!.currentDir = nested
        first.loadEntries()
        first.persistCurrentDir()
        // 不等待写入完成，立刻打开文件（快速用户操作）
        activity.showReaderModeWithPath(doc.absolutePath)
        idle()

        activity.showDirectoryMode(fromFolders = true)
        idle()

        val second = activity.supportFragmentManager.fragments
            .filterIsInstance<FilesFragment>()
            .lastOrNull()
        assertNotNull(second)
        idle()
        assertEquals(nested.absolutePath, second!!.currentDir?.absolutePath)
    }

    @Test
    fun scrollPosition_isRestoredAfterOpenFileAndReturn() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val root = tempFolder.newFolder("rootScroll")
        repeat(8) { index -> File(root, "dir$index").mkdirs() }
        val target = File(root, "dir6")

        runBlocking {
            val repo = FilesRuntime.folderSessionRepository(context)
            val id = repo.add("scrollSession", root.absolutePath)
            repo.updateCurrentDir(id, root.absolutePath)
            FilesRuntime.currentSessionStore(context).set(id)
        }

        activity.showDirectoryMode(fromFolders = true)
        controller.start().resume()
        idle()

        val first = activity.supportFragmentManager.fragments
            .filterIsInstance<FilesFragment>()
            .lastOrNull()
        assertNotNull(first)
        idle()

        first!!.listView.setSelection(6)
        idle()
        val saved = first.listView.firstVisiblePosition

        activity.showReaderModeWithPath(File(target, "missing.mhtml").absolutePath)
        idle()
        activity.showDirectoryMode(fromFolders = true)
        idle()

        val second = activity.supportFragmentManager.fragments
            .filterIsInstance<FilesFragment>()
            .lastOrNull()
        assertNotNull(second)
        idle()
        assertEquals(saved, second!!.listView.firstVisiblePosition)
    }
}
