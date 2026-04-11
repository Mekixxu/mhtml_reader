package com.html_reader.files

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

data class FtpParseResult(
    val entries: List<BrowserEntry>,
    val resolvedCharset: String
)

object FilesFtpCodec {
    fun parseEntries(
        lines: List<String>,
        path: String,
        configuredCharset: String?,
        previousResolvedCharset: String?,
        supportedExtensions: Set<String>,
        decodeCache: MutableMap<String, String>
    ): FtpParseResult {
        val rawEntries = lines.mapNotNull { parseFtpLine(it) }
        val decidedCharset = resolveFtpCharsetForEntries(
            rawNames = rawEntries.map { it.rawNameBytes },
            configuredCharset = configuredCharset,
            previousResolvedCharset = previousResolvedCharset,
            decodeCache = decodeCache
        )
        val entries = rawEntries.mapNotNull { raw ->
            val name = decodeForSearch(raw.rawNameBytes, decidedCharset, decodeCache)
                ?: String(raw.rawNameBytes, Charsets.ISO_8859_1)
            if (name == "." || name == "..") {
                null
            } else {
                val childPath = FilesNetworkGateway.joinFtpPath(path, name)
                BrowserEntry(
                    localFile = null,
                    ftpPath = childPath,
                    name = name,
                    isDirectory = raw.isDirectory,
                    sizeBytes = raw.sizeBytes,
                    modifiedEpochMs = null,
                    modifiedText = raw.modifiedText,
                    rawNameBytes = raw.rawNameBytes
                )
            }
        }
        val folders = entries.filter { it.isDirectory }.sortedBy { it.name.lowercase(Locale.getDefault()) }
        val files = entries
            .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase(Locale.getDefault()) in supportedExtensions }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
        return FtpParseResult(entries = folders + files, resolvedCharset = decidedCharset)
    }

    fun decodeForSearch(bytes: ByteArray, charsetName: String, cache: MutableMap<String, String>): String? {
        val cacheKey = "${charsetName}:${bytes.contentHashCode()}:${bytes.size}"
        val cached = cache[cacheKey]
        if (cached != null) {
            return cached
        }
        return runCatching {
            val decoder = Charset.forName(charsetName).newDecoder()
            decoder.onMalformedInput(CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()?.also { cache[cacheKey] = it }
    }

    private fun parseFtpLine(rawLine: String): FtpRawEntry? {
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith("total ")) {
            return null
        }
        return parseUnixStyle(line) ?: parseDosStyle(line)
    }

    private fun parseUnixStyle(line: String): FtpRawEntry? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 6) return null
        if (!parts[0].startsWith("d") && !parts[0].startsWith("-") && !parts[0].startsWith("l")) {
            return null
        }
        val isDir = parts[0].startsWith("d")
        val months = setOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        var dateStartIdx = -1
        var dateLength = 0
        var dateStr = ""

        for (i in 3 until parts.size - 1) {
            if (parts[i] in months && i + 2 < parts.size && parts[i + 1].all { it.isDigit() }) {
                dateStartIdx = i
                dateLength = 3
                dateStr = parts.subList(i, i + 3).joinToString(" ")
                break
            }
            if (parts[i].matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                dateStartIdx = i
                dateLength = if (i + 1 < parts.size && parts[i + 1].matches(Regex("\\d{1,2}:\\d{2}(:\\d{2})?"))) 2 else 1
                dateStr = parts.subList(i, i + dateLength).joinToString(" ")
                break
            }
        }

        if (dateStartIdx == -1) return null
        val sizeIdx = dateStartIdx - 1
        val size = parts.getOrNull(sizeIdx)?.toLongOrNull() ?: 0L
        val nameStartPartIdx = dateStartIdx + dateLength
        if (nameStartPartIdx >= parts.size) return null

        var currentSearchIdx = 0
        for (i in 0 until nameStartPartIdx) {
            val part = parts[i]
            val foundAt = line.indexOf(part, currentSearchIdx)
            if (foundAt == -1) return null
            currentSearchIdx = foundAt + part.length
        }
        while (currentSearchIdx < line.length && line[currentSearchIdx].isWhitespace()) {
            currentSearchIdx++
        }
        if (currentSearchIdx >= line.length) return null
        val rawNameString = line.substring(currentSearchIdx)
        val rawBytes = rawNameString.toByteArray(Charsets.ISO_8859_1)
        return FtpRawEntry(rawBytes, isDir, size, dateStr)
    }

    private fun parseDosStyle(line: String): FtpRawEntry? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 3) return null
        val dateLength = if (
            parts[0].matches(Regex("\\d{2}-\\d{2}-\\d{2}")) ||
            parts[0].matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
        ) {
            if (parts.size > 1 && parts[1].matches(Regex("\\d{1,2}:\\d{2}.*"))) 2 else 1
        } else {
            return null
        }

        val metaIdx = dateLength
        if (metaIdx >= parts.size) return null
        val metaPart = parts[metaIdx]
        val isDir = metaPart.equals("<DIR>", ignoreCase = true)
        val size = if (isDir) 0L else metaPart.toLongOrNull() ?: 0L
        val nameStartPartIdx = metaIdx + 1
        if (nameStartPartIdx >= parts.size) return null
        val dateStr = parts.subList(0, dateLength).joinToString(" ")

        var currentSearchIdx = 0
        for (i in 0 until nameStartPartIdx) {
            val part = parts[i]
            val foundAt = line.indexOf(part, currentSearchIdx)
            if (foundAt == -1) return null
            currentSearchIdx = foundAt + part.length
        }
        while (currentSearchIdx < line.length && line[currentSearchIdx].isWhitespace()) {
            currentSearchIdx++
        }
        if (currentSearchIdx >= line.length) return null
        val rawNameString = line.substring(currentSearchIdx)
        val rawBytes = rawNameString.toByteArray(Charsets.ISO_8859_1)
        return FtpRawEntry(rawBytes, isDir, size, dateStr)
    }

    private fun resolveFtpCharsetForEntries(
        rawNames: List<ByteArray>,
        configuredCharset: String?,
        previousResolvedCharset: String?,
        decodeCache: MutableMap<String, String>
    ): String {
        if (!configuredCharset.isNullOrBlank()) {
            return configuredCharset
        }
        val sampled = rawNames.take(24)
        if (sampled.isEmpty()) {
            return previousResolvedCharset ?: "UTF-8"
        }
        val candidates = listOfNotNull(
            previousResolvedCharset,
            "UTF-8",
            "GBK",
            "Big5",
            "Shift_JIS",
            "ISO-8859-1"
        ).distinct()
        return candidates.maxByOrNull { charset ->
            scoreFtpCharset(sampled, charset, decodeCache)
        } ?: "UTF-8"
    }

    private fun scoreFtpCharset(
        rawNames: List<ByteArray>,
        charsetName: String,
        decodeCache: MutableMap<String, String>
    ): Int {
        var score = 0
        for (bytes in rawNames) {
            val decoded = decodeForSearch(bytes, charsetName, decodeCache) ?: return Int.MIN_VALUE / 2
            if (decoded.isBlank()) {
                score -= 30
                continue
            }
            val replacementCount = decoded.count { it == '\uFFFD' }
            val controlCount = decoded.count { it.code < 0x20 && it != '\n' && it != '\t' }
            val suspiciousCount = decoded.count { it in listOf('Ã', 'â', '¤', '�') }
            score += 120
            score -= replacementCount * 80
            score -= controlCount * 40
            score -= suspiciousCount * 18
        }
        return score
    }
}
