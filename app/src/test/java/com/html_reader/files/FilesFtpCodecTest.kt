package com.html_reader.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesFtpCodecTest {

    private fun parse(lines: List<String>, path: String = "/") =
        FilesFtpCodec.parseEntries(
            lines = lines,
            path = path,
            configuredCharset = "UTF-8",
            previousResolvedCharset = null,
            supportedExtensions = setOf("pdf", "mht", "mhtml"),
            decodeCache = mutableMapOf()
        )

    @Test
    fun `unix listing parses file and directory`() {
        val result = parse(
            listOf(
                "-rw-r--r-- 1 owner group 1234 Jan 01 2024 readme.pdf",
                "drwxr-xr-x 2 owner group 4096 Jan 02 2024 docs"
            )
        )

        val file = result.entries.first { !it.isDirectory }
        val dir = result.entries.first { it.isDirectory }
        assertEquals("readme.pdf", file.name)
        assertEquals("/readme.pdf", file.ftpPath)
        assertEquals(1234L, file.sizeBytes)
        assertTrue(dir.name == "docs")
    }

    @Test
    fun `dos listing parses dir and file`() {
        val result = parse(
            listOf(
                "02-15-24  10:30AM  <DIR>  docs",
                "02-15-24  10:31AM  456 file.pdf"
            )
        )

        val dir = result.entries.first { it.isDirectory }
        val file = result.entries.first { !it.isDirectory }
        assertEquals("docs", dir.name)
        assertEquals("/docs", dir.ftpPath)
        assertEquals(456L, file.sizeBytes)
        assertTrue(file.name == "file.pdf")
    }

    @Test
    fun `skips total line and dot entries`() {
        val result = parse(
            listOf(
                "total 16",
                "-rw-r--r-- 1 owner group 1234 Jan 01 2024 .",
                "-rw-r--r-- 1 owner group 1234 Jan 01 2024 ..",
                "-rw-r--r-- 1 owner group 1234 Jan 01 2024 keep.pdf"
            )
        )

        assertEquals(1, result.entries.size)
        assertEquals("keep.pdf", result.entries.first().name)
    }
}
