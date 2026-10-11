package com.html_reader

import com.html_reader.files.BrowseSource
import com.html_reader.files.FilesBrowseRequestGuard
import com.html_reader.files.LocalBrowseRequest
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesBrowseRequestTest {

    @Test
    fun begin_invalidatesPreviousGeneration() {
        val guard = FilesBrowseRequestGuard()

        val first = guard.begin()
        assertTrue(guard.isCurrent(first))

        val second = guard.begin()
        assertFalse("新的加载会使旧请求失效", guard.isCurrent(first))
        assertTrue(guard.isCurrent(second))
    }

    @Test
    fun localBrowseRequest_matchesSourceSessionAndDirectory() {
        val request = LocalBrowseRequest(
            generation = 1L,
            sessionId = 7L,
            source = BrowseSource.LOCAL,
            directory = File("/docs/root")
        )

        assertTrue(request.matches(BrowseSource.LOCAL, 7L, File("/docs/root")))
        assertFalse("本地 A 的旧结果不能覆盖 FTP 页面", request.matches(BrowseSource.FTP, 7L, File("/docs/root")))
        assertFalse("同路径不同会话不能互相串位置", request.matches(BrowseSource.LOCAL, 8L, File("/docs/root")))
        assertFalse("切到其他目录后旧结果作废", request.matches(BrowseSource.LOCAL, 7L, File("/docs/other")))
        assertFalse("目录为空时不接受结果", request.matches(BrowseSource.LOCAL, 7L, null))
    }
}
