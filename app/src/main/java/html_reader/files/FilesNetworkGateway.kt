package com.html_reader.files

import core.database.entity.NetworkConfigEntity
import java.io.File
import java.net.URLEncoder
import java.net.URL
import java.security.MessageDigest

object FilesNetworkGateway {
    fun normalizeFtpPath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return "/"
        val normalized = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return normalized.replace(Regex("/+"), "/")
    }

    fun joinFtpPath(parent: String, child: String): String {
        val p = normalizeFtpPath(parent).trimEnd('/')
        val c = child.trimStart('/')
        return normalizeFtpPath("$p/$c")
    }

    fun ftpParentPath(path: String): String {
        val normalized = normalizeFtpPath(path).trimEnd('/')
        if (normalized.isBlank() || normalized == "/") return "/"
        val idx = normalized.lastIndexOf('/')
        return if (idx <= 0) "/" else normalized.substring(0, idx)
    }

    fun normalizeSmbPath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return "/"
        val normalized = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return normalized.replace(Regex("/+"), "/")
    }

    fun joinSmbPath(parent: String, child: String): String {
        val p = normalizeSmbPath(parent).trimEnd('/')
        val c = child.trimStart('/')
        return normalizeSmbPath("$p/$c")
    }

    fun smbParentPath(path: String): String {
        val normalized = normalizeSmbPath(path).trimEnd('/')
        if (normalized.isBlank() || normalized == "/") return "/"
        val idx = normalized.lastIndexOf('/')
        return if (idx <= 0) "/" else normalized.substring(0, idx)
    }

    fun buildFtpUrl(config: NetworkConfigEntity, path: String, type: String, charset: String): String {
        val user = config.username.trim().ifBlank { "anonymous" }
        val pass = config.password.ifBlank { "anonymous@" }
        val encodedUser = encodeSegment(user, charset)
        val encodedPass = encodeSegment(pass, charset)
        val normalized = normalizeFtpPath(path)
        val encodedPath = normalized
            .split("/")
            .joinToString("/") { segment -> if (segment.isBlank()) "" else encodeSegment(segment, charset) }
        val finalPath = if (type == "d" && !encodedPath.endsWith("/")) "$encodedPath/" else encodedPath
        return "ftp://$encodedUser:$encodedPass@${config.host}:${config.port}$finalPath;type=$type"
    }

    fun buildSmbDirUrl(config: NetworkConfigEntity, path: String): String {
        val encodedPath = normalizeSmbPath(path)
            .split("/")
            .joinToString("/") { segment ->
                if (segment.isBlank()) "" else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
        val url = "smb://${config.host}:${config.port}$encodedPath"
        return if (url.endsWith("/")) url else "$url/"
    }

    fun buildSmbFileUrl(config: NetworkConfigEntity, path: String): String {
        val encodedPath = normalizeSmbPath(path)
            .split("/")
            .joinToString("/") { segment ->
                if (segment.isBlank()) "" else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
        return "smb://${config.host}:${config.port}$encodedPath"
    }

    fun encodeSegment(value: String, charset: String): String {
        return runCatching { URLEncoder.encode(value, charset) }
            .getOrDefault(URLEncoder.encode(value, "UTF-8"))
            .replace("+", "%20")
    }

    fun createUniqueCacheFile(baseDir: File, displayName: String, uniquenessKey: String): File {
        if (!baseDir.exists()) baseDir.mkdirs()
        val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val suffix = hashHex(uniquenessKey).take(10)
        val dotted = safeName.lastIndexOf('.')
        val fileName = if (dotted in 1 until safeName.lastIndex) {
            safeName.substring(0, dotted) + "_$suffix" + safeName.substring(dotted)
        } else {
            "${safeName}_$suffix"
        }
        return File(baseDir, fileName)
    }

    fun openFtpStream(config: NetworkConfigEntity, path: String, charset: String) =
        URL(buildFtpUrl(config, path, "i", charset)).openStream()

    private fun hashHex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
