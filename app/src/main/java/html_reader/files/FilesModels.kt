package com.html_reader.files

import java.io.File

enum class BrowseSource {
    LOCAL,
    FTP,
    SMB
}

data class BrowserEntry(
    val localFile: File? = null,
    val ftpPath: String? = null,
    val smbPath: String? = null,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedEpochMs: Long?,
    val modifiedText: String? = null,
    val rawNameBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BrowserEntry

        if (localFile != other.localFile) return false
        if (ftpPath != other.ftpPath) return false
        if (smbPath != other.smbPath) return false
        if (name != other.name) return false
        if (isDirectory != other.isDirectory) return false
        if (sizeBytes != other.sizeBytes) return false
        if (modifiedEpochMs != other.modifiedEpochMs) return false
        if (modifiedText != other.modifiedText) return false
        if (rawNameBytes != null) {
            if (other.rawNameBytes == null) return false
            if (!rawNameBytes.contentEquals(other.rawNameBytes)) return false
        } else if (other.rawNameBytes != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = localFile?.hashCode() ?: 0
        result = 31 * result + (ftpPath?.hashCode() ?: 0)
        result = 31 * result + (smbPath?.hashCode() ?: 0)
        result = 31 * result + name.hashCode()
        result = 31 * result + isDirectory.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + (modifiedEpochMs?.hashCode() ?: 0)
        result = 31 * result + (modifiedText?.hashCode() ?: 0)
        result = 31 * result + (rawNameBytes?.contentHashCode() ?: 0)
        return result
    }
}

data class FtpRawEntry(
    val rawNameBytes: ByteArray,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedText: String?
)

fun BrowserEntry.pathKey(): String? = localFile?.absolutePath ?: ftpPath ?: smbPath

fun BrowserEntry.isSamePathAs(other: BrowserEntry?): Boolean {
    val selectedPath = other?.localFile?.absolutePath ?: other?.ftpPath ?: other?.smbPath
    val currentPath = localFile?.absolutePath ?: ftpPath ?: smbPath
    return selectedPath != null && selectedPath == currentPath
}
