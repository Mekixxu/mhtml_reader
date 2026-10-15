package com.html_reader

import core.reader.web.WebGestureTakeoverState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebGestureTakeoverStateTest {

    @Test
    fun moveWithinTouchSlopIsNotIntentional() {
        val state = WebGestureTakeoverState()
        state.onDown(100f, 100f, generation = 1L)

        assertFalse(
            state.onMove(
                x = 102f, y = 103f, generation = 1L,
                touchSlop = 8f, hasValidContentExtent = true
            )
        )
    }

    @Test
    fun moveBeyondTouchSlopIsIntentional() {
        val state = WebGestureTakeoverState()
        state.onDown(100f, 100f, generation = 1L)

        assertTrue(
            state.onMove(
                x = 100f, y = 200f, generation = 1L,
                touchSlop = 8f, hasValidContentExtent = true
            )
        )
    }

    @Test
    fun moveWithoutDownIsIgnored() {
        val state = WebGestureTakeoverState()

        assertFalse(
            state.onMove(
                x = 100f, y = 400f, generation = 1L,
                touchSlop = 8f, hasValidContentExtent = true
            )
        )
    }

    @Test
    fun staleGenerationMoveIsIgnoredUntilNextDown() {
        val state = WebGestureTakeoverState()
        state.onDown(100f, 100f, generation = 1L)

        assertFalse(
            state.onMove(
                x = 100f, y = 400f, generation = 2L,
                touchSlop = 8f, hasValidContentExtent = true
            )
        )
        // 新 generation 重新按下后才可接管
        state.onDown(100f, 100f, generation = 2L)
        assertTrue(
            state.onMove(
                x = 100f, y = 400f, generation = 2L,
                touchSlop = 8f, hasValidContentExtent = true
            )
        )
    }

    @Test
    fun invalidContentExtentDefersThenAllowsCumulativeDrag() {
        val state = WebGestureTakeoverState()
        state.onDown(100f, 100f, generation = 1L)

        assertFalse(
            state.onMove(
                x = 100f, y = 400f, generation = 1L,
                touchSlop = 8f, hasValidContentExtent = false
            )
        )
        assertTrue(
            state.onMove(
                x = 100f, y = 400f, generation = 1L,
                touchSlop = 8f, hasValidContentExtent = true
            )
        )
    }

    @Test
    fun clearResetsGestureSoLaterMoveIsIgnored() {
        val state = WebGestureTakeoverState()
        state.onDown(100f, 100f, generation = 1L)
        state.clear()

        assertFalse(
            state.onMove(
                x = 100f, y = 400f, generation = 1L,
                touchSlop = 8f, hasValidContentExtent = true
            )
        )
    }
}
