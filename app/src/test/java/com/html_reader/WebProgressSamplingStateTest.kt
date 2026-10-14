package com.html_reader

import core.reader.web.WebProgressSamplingState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebProgressSamplingStateTest {

    @Test
    fun resumeBeforeRestoreCompletesDoesNotEnableSampling() {
        val state = WebProgressSamplingState()
        state.beginContent(generation = 1L)
        state.onPause()

        assertFalse(state.onResume(generation = 1L))
        assertTrue(state.onRestoreCompleted(generation = 1L))
    }

    @Test
    fun restoreCompletedInBackgroundEnablesSamplingOnNextResume() {
        val state = WebProgressSamplingState()
        state.beginContent(generation = 2L)
        state.onPause()

        assertFalse(state.onRestoreCompleted(generation = 2L))
        assertTrue(state.onResume(generation = 2L))
    }

    @Test
    fun pauseAndResumeAfterRestoreReEnablesSampling() {
        val state = WebProgressSamplingState()
        state.beginContent(generation = 3L)
        assertFalse(state.onResume(generation = 3L))
        assertTrue(state.onRestoreCompleted(generation = 3L))

        state.onPause()

        assertTrue(state.onResume(generation = 3L))
    }

    @Test
    fun staleRestoreCompletionCannotEnableSamplingForNewContent() {
        val state = WebProgressSamplingState()
        state.beginContent(generation = 4L)
        state.onPause()
        state.beginContent(generation = 5L)

        assertFalse(state.onRestoreCompleted(generation = 4L))
        assertFalse(state.onResume(generation = 5L))
        assertTrue(state.onRestoreCompleted(generation = 5L))
    }
}
