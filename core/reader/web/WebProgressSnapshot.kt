package core.reader.web

import core.database.entity.enums.FileType

data class WebProgressSnapshot(
    val sourcePathRaw: String,
    val title: String,
    val fileType: FileType,
    val progress: Float,
    val pageIndex: Int = -1
)
