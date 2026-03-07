package core.network.domain.mapper

import core.network.domain.model.NetworkConfig
import core.network.domain.model.NetworkProtocol
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.NetworkProtocol as EntityNetworkProtocol

/**
 * entity <-> domain model 映射工具
 */
object NetworkConfigMapper {
    fun entityToDomain(entity: NetworkConfigEntity): NetworkConfig = NetworkConfig(
        id = entity.id,
        name = entity.name,
        protocol = NetworkProtocol.fromString(entity.protocol.name),
        host = entity.host,
        port = entity.port,
        username = entity.username,
        password = entity.password,
        defaultPath = if (entity.defaultPath.isBlank()) "/" else entity.defaultPath,
        anonymous = entity.username == "anonymous"
    )

    fun domainToEntity(config: NetworkConfig): NetworkConfigEntity = NetworkConfigEntity(
        id = config.id ?: 0L,
        name = config.name,
        protocol = EntityNetworkProtocol.valueOf(config.protocol.name),
        host = config.host,
        port = config.port,
        username = config.username,
        password = config.password,
        defaultPath = if (config.defaultPath.isBlank()) "/" else config.defaultPath
    )
}
