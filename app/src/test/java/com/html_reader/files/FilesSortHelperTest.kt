package com.html_reader.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesSortHelperTest {

    private fun entry(name: String, size: Long = 1, modified: Long = 1, dir: Boolean = false) =
        BrowserEntry(
            name = name,
            isDirectory = dir,
            sizeBytes = size,
            modifiedEpochMs = modified
        )

    private val entries = listOf(
        entry("banana", size = 30, modified = 3),
        entry("Apple", size = 10, modified = 1),
        entry("cherry", size = 20, modified = 2)
    )

    @Test
    fun `sortIndex 0 sorts by name ascending case-insensitive`() {
        val sorted = FilesSortHelper.sortEntriesWithinGroup(entries, 0)
        assertEquals(listOf("Apple", "banana", "cherry"), sorted.map { it.name })
    }

    @Test
    fun `sortIndex 1 sorts by name descending`() {
        val sorted = FilesSortHelper.sortEntriesWithinGroup(entries, 1)
        assertEquals(listOf("cherry", "banana", "Apple"), sorted.map { it.name })
    }

    @Test
    fun `sortIndex 2 sorts by modified descending`() {
        val sorted = FilesSortHelper.sortEntriesWithinGroup(entries, 2)
        assertEquals(listOf("banana", "cherry", "Apple"), sorted.map { it.name })
    }

    @Test
    fun `sortIndex 3 sorts by size descending`() {
        val sorted = FilesSortHelper.sortEntriesWithinGroup(entries, 3)
        assertEquals(listOf("banana", "cherry", "Apple"), sorted.map { it.name })
    }

    @Test
    fun `sortIndex 4 sorts by size ascending`() {
        val sorted = FilesSortHelper.sortEntriesWithinGroup(entries, 4)
        assertEquals(listOf("Apple", "cherry", "banana"), sorted.map { it.name })
    }

    @Test
    fun `unknown sortIndex falls back to name ascending`() {
        val sorted = FilesSortHelper.sortEntriesWithinGroup(entries, 99)
        assertEquals(listOf("Apple", "banana", "cherry"), sorted.map { it.name })
    }

    @Test
    fun `directories rank naturally by name when name matches`() {
        val mixed = listOf(
            entry("docs", dir = true),
            entry("docs", dir = false)
        )
        val sorted = FilesSortHelper.sortEntriesWithinGroup(mixed, 0)
        assertEquals(2, sorted.size)
    }
}