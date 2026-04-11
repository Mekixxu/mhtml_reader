package com.html_reader.files

import core.data.repo.TitleCacheRepository
import core.database.entity.TitleCacheEntity
import core.database.entity.enums.FileType
import core.title.impl.HtmlTitleExtractor
import core.vfs.model.VfsPath
import java.io.File

class FilesTitleRefresher(
    private val titleCacheRepository: TitleCacheRepository,
    private val htmlTitleExtractor: HtmlTitleExtractor
) {
    suspend fun refreshLocalTitles(
        files: List<File>,
        onCachedTitle: (path: String, title: String) -> Unit,
        onResolvedTitle: (path: String, title: String) -> Unit
    ) {
        for (file in files) {
            val path = file.absolutePath
            val cached = titleCacheRepository.get(path)
            if (cached != null && cached.lastModified == file.lastModified()) {
                val normalizedTitle = normalizeDisplayTitle(cached.title, file.name)
                if (!normalizedTitle.isNullOrBlank()) {
                    onCachedTitle(path, normalizedTitle)
                }
            }
        }
        for (file in files) {
            val ext = file.name.substringAfterLast('.', "").lowercase()
            if (ext != "mht" && ext != "mhtml") continue
            val path = file.absolutePath
            val cached = titleCacheRepository.get(path)
            val cachedUsable = cached?.let { normalizeDisplayTitle(it.title, file.name) } != null
            if (cached != null && cached.lastModified == file.lastModified() && cachedUsable) continue
            val title = htmlTitleExtractor.extractTitle(
                source = VfsPath.LocalFile(path),
                cacheFile = file,
                fileType = FileType.MHTML,
                maxBytesToRead = 256L * 1024L
            )?.trim()
            val normalizedTitle = normalizeDisplayTitle(title, file.name) ?: continue
            titleCacheRepository.upsert(
                TitleCacheEntity(
                    path = path,
                    title = normalizedTitle,
                    lastModified = file.lastModified(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            onResolvedTitle(path, normalizedTitle)
        }
    }

    fun normalizeDisplayTitle(rawTitle: String?, fileName: String): String? {
        val title = rawTitle?.trim().orEmpty()
        if (title.isBlank()) return null
        return if (titleRejectReason(title, fileName) == null) title else null
    }

    fun titleRejectReason(title: String, fileName: String): String? {
        if (title.equals(fileName, ignoreCase = true)) return "same_as_filename"
        if (title.any { it.code < 0x20 && it != '\n' && it != '\t' }) return "control_character"
        val replacementCount = title.count { it == '\uFFFD' }
        if (replacementCount >= 2 || replacementCount.toFloat() / title.length.toFloat() > 0.08f) {
            return "replacement_ratio_high"
        }
        val suspiciousCount = title.count { it in listOf('Ã', 'â', '¤', '�') }
        if (suspiciousCount >= 3 && suspiciousCount.toFloat() / title.length.toFloat() > 0.12f) {
            return "suspicious_symbol_density_high"
        }
        return null
    }
}
