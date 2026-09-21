package core.data.repo

import android.util.Log
import core.common.DispatcherProvider
import core.database.dao.NetworkConfigDao
import core.database.entity.NetworkConfigEntity
import core.security.CredentialCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 网络配置Repo。密码在写入口统一加密、在读取路径统一解密；
 * DAO 层仍保持 TEXT 列不变，旧明文数据无 enc:v1: 前缀可继续读取。
 *
 * 解密失败时保留原存值并标记 decryptFailed，由 UI 警示并阻断覆盖保存。
 */
class NetworkConfigRepository(
    private val dao: NetworkConfigDao,
    private val dispatcherProvider: DispatcherProvider,
    private val credentialCipher: CredentialCipher
) {
    companion object {
        private const val TAG = "NetworkConfigRepository"
    }
    fun observeAll(): Flow<List<NetworkConfigEntity>> =
        dao.observeAll()
            .map { entities -> entities.map { it.withDecryptedPassword() } }
            .flowOn(dispatcherProvider.io)

    suspend fun add(entity: NetworkConfigEntity): Long = withContext(dispatcherProvider.io) {
        dao.insert(entity.withEncryptedPassword())
    }

    suspend fun update(entity: NetworkConfigEntity) = withContext(dispatcherProvider.io) {
        dao.update(entity.withEncryptedPassword())
    }

    suspend fun delete(id: Long) = withContext(dispatcherProvider.io) { dao.delete(id) }

    suspend fun getAll(): List<NetworkConfigEntity> = withContext(dispatcherProvider.io) {
        val all = ArrayList<NetworkConfigEntity>(64)
        var offset = 0
        val pageSize = 100
        while (true) {
            val page = dao.getAll(limit = pageSize, offset = offset)
            if (page.isEmpty()) break
            all.addAll(page.map { it.withDecryptedPassword() })
            offset += page.size
        }
        all
    }

    suspend fun getById(id: Long): NetworkConfigEntity? = withContext(dispatcherProvider.io) {
        dao.getById(id)?.withDecryptedPassword()
    }

    suspend fun clearAll() = withContext(dispatcherProvider.io) { dao.clearAll() }

    /**
     * 一次性迁移：把历史明文密码（无 enc:v1: 前缀）重加密写回。幂等。
     */
    suspend fun migrateLegacyPlaintextIfNeeded() = withContext(dispatcherProvider.io) {
        var offset = 0
        val pageSize = 100
        while (true) {
            val page = dao.getAll(limit = pageSize, offset = offset)
            if (page.isEmpty()) break
            page.forEach { entity ->
                val stored = entity.password
                if (stored.isNotEmpty() && !credentialCipher.isEncrypted(stored)) {
                    dao.update(entity.copy(password = credentialCipher.encrypt(stored)))
                    Log.d(TAG, "credential_migrated id=${entity.id} host=${entity.host}")
                }
            }
            offset += page.size
        }
    }

    private fun NetworkConfigEntity.withEncryptedPassword(): NetworkConfigEntity =
        // 已是密文（如导入的备份）直接保留，避免二次加密导致永久不可解
        if (credentialCipher.isEncrypted(password)) this
        else copy(password = credentialCipher.encrypt(password))

    private fun NetworkConfigEntity.withDecryptedPassword(): NetworkConfigEntity {
        val decrypted = runCatching { credentialCipher.decrypt(password) }
        return if (decrypted.isSuccess) {
            copy(password = decrypted.getOrThrow())
        } else {
            Log.w(TAG, "credential_decrypt_failed id=$id host=$host err=${decrypted.exceptionOrNull()?.javaClass?.simpleName}")
            this.apply { decryptFailed = true }
        }
    }
}
