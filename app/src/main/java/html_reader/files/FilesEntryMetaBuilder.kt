package com.html_reader.files

/**
 * 文件列表行的元信息：仅展示大小与修改时间，不再附带文档标题
 * （标题行会被误认为重复的文件条目）。
 */
object FilesEntryMetaBuilder {
    private const val SEPARATOR = "  •  "

    fun build(sizeLabel: String, timeLabel: String): String =
        listOf(sizeLabel, timeLabel)
            .filter { it.isNotBlank() }
            .joinToString(SEPARATOR)
}
