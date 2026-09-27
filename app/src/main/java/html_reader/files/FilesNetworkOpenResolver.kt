package com.html_reader.files

import android.net.Uri
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.FavoriteType
import core.database.entity.enums.NetworkProtocol
import core.database.entity.enums.SourceType
import java.net.URLDecoder

enum class NetworkOpenIssue {
    INVALID_PATH,
    MISSING_CREDENTIAL
}

data class ResolvedNetworkOpen(
    val configId: Long? = null,
    val adHocConfig: NetworkConfigEntity? = null,
    val openPath: String? = null,
    val issue: NetworkOpenIssue? = null
)

object FilesNetworkOpenResolver {
    fun resolve(
        path: String,
        sourceType: SourceType,
        favoriteType: FavoriteType,
        networkConfigs: List<NetworkConfigEntity>
    ): ResolvedNetworkOpen {
        val protocol = when (sourceType) {
            SourceType.FTP -> NetworkProtocol.FTP
            SourceType.SMB -> NetworkProtocol.SMB
            else -> return ResolvedNetworkOpen(issue = NetworkOpenIssue.INVALID_PATH)
        }
        val uri = runCatching { Uri.parse(path) }.getOrNull() ?: return ResolvedNetworkOpen(issue = NetworkOpenIssue.INVALID_PATH)
        val host = uri.host.orEmpty()
        if (host.isBlank()) return ResolvedNetworkOpen(issue = NetworkOpenIssue.INVALID_PATH)
        val port = if (uri.port > 0) uri.port else if (protocol == NetworkProtocol.FTP) 21 else 445
        val rawPath = runCatching {
            URLDecoder.decode(uri.encodedPath.orEmpty().ifBlank { "/" }, "UTF-8")
        }.getOrDefault("/")
        val normalized = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        val openPath = if (favoriteType == FavoriteType.FILE) {
            val index = normalized.lastIndexOf('/')
            if (index <= 0) "/" else normalized.substring(0, index)
        } else {
            normalized
        }
        val existing = networkConfigs.firstOrNull {
            it.protocol == protocol && it.host.equals(host, ignoreCase = true) && it.port == port
        }
        if (existing != null) {
            return ResolvedNetworkOpen(configId = existing.id, openPath = openPath)
        }
        val userInfo = uri.userInfo.orEmpty()
        // 注意：无冒号时必须回退为整个 userInfo（anonymous 等无密码用户名）
        val username = userInfo.substringBefore(':', userInfo).let { URLDecoder.decode(it, "UTF-8") }
        val password = userInfo.substringAfter(':', "").let { URLDecoder.decode(it, "UTF-8") }
        if (username.isBlank() && password.isBlank()) {
            return ResolvedNetworkOpen(issue = NetworkOpenIssue.MISSING_CREDENTIAL)
        }
        // 收藏路径已剥离密码：无密码的 adHoc 配置无法认证，应提示用户先建网络配置；
        // 匿名 FTP（anonymous + 空密码）除外
        val isAnonymous = username.equals("anonymous", ignoreCase = true) && password.isBlank()
        if (password.isBlank() && !isAnonymous) {
            return ResolvedNetworkOpen(issue = NetworkOpenIssue.MISSING_CREDENTIAL)
        }
        val adHoc = NetworkConfigEntity(
            id = 0L,
            name = "Fav ${protocol.name} ${host}:${port}",
            protocol = protocol,
            host = host,
            port = port,
            username = username,
            password = password,
            defaultPath = normalized
        )
        return ResolvedNetworkOpen(adHocConfig = adHoc, openPath = openPath)
    }
}
