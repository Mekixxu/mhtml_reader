package core.network.domain.validator

import core.network.domain.model.NetworkConfig
import core.network.domain.model.NetworkProtocol
import core.common.AppError

/**
 * 校验、补默认端口、匿名规则。
 */
object NetworkConfigValidator {

    private val SMB_DEFAULT_PORT = 445
    private val FTP_DEFAULT_PORT = 21

    fun validate(config: NetworkConfig): Result<NetworkConfig> {
        val host = config.host.trim()
        if (host.isEmpty()) return Result.failure(AppError.InvalidUri)
        val port = when {
            config.port in 1..65535 -> config.port
            config.protocol == NetworkProtocol.SMB -> SMB_DEFAULT_PORT
            config.protocol == NetworkProtocol.FTP -> FTP_DEFAULT_PORT
            else -> return Result.failure(AppError.InvalidUri)
        }
        if (port !in 1..65535) return Result.failure(AppError.InvalidUri)
        val username = if (config.anonymous && config.protocol == NetworkProtocol.FTP) "anonymous" else config.username
        val defaultPath = if (config.defaultPath.isBlank()) "/" else config.defaultPath
        val isAnon = config.anonymous && config.protocol == NetworkProtocol.FTP
        // SMB匿名规则：v1.0允许空用户名/密码，后续可加限制
        return Result.success(
            config.copy(
                host = host,
                port = port,
                username = username,
                defaultPath = defaultPath,
                anonymous = isAnon
            )
        )
    }
}
