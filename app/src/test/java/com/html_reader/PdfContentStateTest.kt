package com.html_reader

import core.reader.pdf.PdfContentState
import core.reader.pdf.PdfRenderKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfContentStateTest {

    @Test
    fun beginContentLoad_invalidatesOldGenerationAndKeys() {
        val state = PdfContentState()
        val firstGeneration = state.beginContentLoad()
        val key = PdfRenderKey(tabId = "a", pageIndex = 0)
        state.markPending(key)

        val secondGeneration = state.beginContentLoad()

        assertFalse(state.isCurrent(firstGeneration))
        assertTrue(state.isCurrent(secondGeneration))
        assertTrue("新内容加载后旧 pending 不应阻塞渲染", state.shouldSchedule(key, force = false))
    }

    @Test
    fun sameKey_requiresForceToRenderAgain() {
        val state = PdfContentState()
        state.beginContentLoad()
        val key = PdfRenderKey(tabId = "a", pageIndex = 0)

        state.markPending(key)
        assertFalse("同一 pending 页不应重复排队", state.shouldSchedule(key, force = false))
        assertTrue("显式首次渲染允许 force", state.shouldSchedule(key, force = true))

        state.clearPending()
        state.markApplied(key)
        assertFalse("同一已应用页不应重复渲染", state.shouldSchedule(key, force = false))
        assertTrue("force 仍可重新渲染", state.shouldSchedule(key, force = true))
    }

    @Test
    fun samePageDifferentTab_isScheduledAgain() {
        val state = PdfContentState()
        state.beginContentLoad()
        state.markApplied(PdfRenderKey(tabId = "a", pageIndex = 0))

        assertTrue(
            "A 的第 0 页切到 B 的第 0 页必须重新渲染",
            state.shouldSchedule(PdfRenderKey(tabId = "b", pageIndex = 0), force = false)
        )
    }
}
