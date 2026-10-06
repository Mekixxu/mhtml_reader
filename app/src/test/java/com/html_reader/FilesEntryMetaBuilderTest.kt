package com.html_reader

import com.html_reader.files.FilesEntryMetaBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FilesEntryMetaBuilderTest {

    @Test
    fun build_joinsSizeAndTime() {
        assertEquals(
            "1.0 KB  •  2026/9/26 10:00",
            FilesEntryMetaBuilder.build(sizeLabel = "1.0 KB", timeLabel = "2026/9/26 10:00")
        )
    }

    @Test
    fun build_directoryWithoutSize_showsTimeOnly() {
        assertEquals("2026/9/26 10:00", FilesEntryMetaBuilder.build(sizeLabel = "", timeLabel = "2026/9/26 10:00"))
    }

    @Test
    fun build_missingTime_showsSizeOnly() {
        assertEquals("1.0 KB", FilesEntryMetaBuilder.build(sizeLabel = "1.0 KB", timeLabel = ""))
    }

    @Test
    fun build_noParts_returnsEmpty() {
        assertEquals("", FilesEntryMetaBuilder.build(sizeLabel = "", timeLabel = ""))
    }

    @Test
    fun build_neverContainsTitle() {
        val meta = FilesEntryMetaBuilder.build(sizeLabel = "1.0 KB", timeLabel = "2026/9/26")
        assertFalse(meta.contains("Title", ignoreCase = true))
    }
}
