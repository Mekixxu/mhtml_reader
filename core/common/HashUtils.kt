package core.common

import java.security.MessageDigest

/**
 * 方便生成 sha256 哈希，用于缓存key
 */
object HashUtils {
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
