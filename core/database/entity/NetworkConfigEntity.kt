package core.database.entity

import androidx.room.*
import core.database.entity.enums.NetworkProtocol

/**
 * 网络配置项。
 * DAO 列仍为 TEXT，但仓库层已使用 CredentialCipher 加密后落库；
 * 旧版本明文密码在读取时按旧格式兼容。
 */
@Entity(
    tableName = "network_configs",
    indices = [
        Index(value = ["protocol", "host", "port", "username", "defaultPath"], unique = true)
    ]
)
data class NetworkConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: NetworkProtocol,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val defaultPath: String,
    val encoding: String = "Auto"
)
