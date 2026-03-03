package core.network.domain.model

/**
 * 后端业务层网络配置模型。
 */
data class NetworkConfig(
    val id: Long? = null,
    val name: String,
    val protocol: NetworkProtocol,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val defaultPath: String,
    val anonymous: Boolean = false
)
