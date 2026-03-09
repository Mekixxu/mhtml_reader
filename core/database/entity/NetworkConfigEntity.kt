package core.database.entity

import androidx.room.*
import core.database.entity.enums.NetworkProtocol

/**
 * 网络配置项
 * 密码明文存储 >> 仅限v1.0，生产必须加密! 导入导出强烈警告!
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
