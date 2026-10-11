package com.html_reader

import android.os.Looper
import com.html_reader.files.BrowseSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class FilesLocalBrowseGenerationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun slowFirstDirectory_doesNotOverwriteNewerDirectory() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = controller.get()
            activity.showDirectoryMode(fromFolders = true)
            controller.start().resume()
            idle()

            val files = activity.supportFragmentManager.fragments
                .filterIsInstance<FilesFragment>()
                .lastOrNull()
            assertNotNull(files)
            files!!

            val slowRoot = tempFolder.newFolder("slowRoot")
            File(slowRoot, "a.mhtml").writeText("<html>a</html>")
            val fastRoot = tempFolder.newFolder("fastRoot")
            File(fastRoot, "b.mhtml").writeText("<html>b</html>")

            files.browseSource = BrowseSource.LOCAL
            files.currentSessionId = 1L
            files.currentDir = SlowDirectory(slowRoot.absolutePath, delayMs = 300L)
            files.loadEntries()

            files.currentSessionId = 2L
            files.currentDir = fastRoot
            files.loadEntries()

            idle()
            assertEquals(listOf("b.mhtml"), files.displayedEntries.map { it.name })
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun slowLocalLoad_doesNotOverwriteNewerRemoteMode() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = controller.get()
            activity.showDirectoryMode(fromFolders = true)
            controller.start().resume()
            idle()

            val files = activity.supportFragmentManager.fragments
                .filterIsInstance<FilesFragment>()
                .lastOrNull()
            assertNotNull(files)
            files!!

            val slowRoot = tempFolder.newFolder("slowRemoteSwitch")
            File(slowRoot, "stale.mhtml").writeText("<html>stale</html>")

            files.browseSource = BrowseSource.LOCAL
            files.currentSessionId = 3L
            files.currentDir = SlowDirectory(slowRoot.absolutePath, delayMs = 300L)
            files.loadEntries()

            // 本地加载尚未返回时切换到 FTP（无配置，只走模式切换，不发起网络请求）
            files.remoteController.ftpConfig = null
            files.browseSource = BrowseSource.FTP
            files.currentSessionId = 4L
            files.loadEntries()

            idle()
            assertFalse(
                "本地旧结果不能覆盖 FTP 模式",
                files.displayedEntries.any { it.name == "stale.mhtml" }
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun idle(cycles: Int = 80) {
        repeat(cycles) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private class SlowDirectory(path: String, private val delayMs: Long) : File(path) {
        override fun listFiles(): Array<File>? {
            Thread.sleep(delayMs)
            return super.listFiles()
        }
    }
}
