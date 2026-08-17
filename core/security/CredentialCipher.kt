package core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 网络凭据加解密。密钥保存在 AndroidKeyStore，不可导出。
 *
 * 密文格式：enc:v1:<base64(iv)>:<base64(ciphertext)>
 * 旧版本数据库中的明文密码没有 enc:v1: 前缀，直接原样返回，实现平滑迁移。
 */
class CredentialCipher(
    private val alias: String = DEFAULT_ALIAS
) {
    fun encrypt(plaintext: String): String {
        if (plaintext.isBlank()) return plaintext
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return buildString {
            append(PREFIX)
            append(Base64.encodeToString(iv, Base64.NO_WRAP))
            append(':')
            append(Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
    }

    /**
     * 是否为 Keystore 加密格式。未带前缀的历史明文返回 false。
     */
    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    /**
     * 解密。无前缀的历史明文原样返回（迁移用）；带前缀的密文解密失败时
     * 抛出异常，由调用方决定如何呈现，避免静默置空丢失原始密码。
     */
    fun decrypt(stored: String): String {
        if (stored.isBlank() || !stored.startsWith(PREFIX)) {
            return stored
        }
        val payload = stored.removePrefix(PREFIX)
        val parts = payload.split(':')
        if (parts.size != 2) {
            throw IllegalArgumentException("Malformed credential payload")
        }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey = synchronized(this) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return@synchronized it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "mhtml_reader_credential_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KEY_SIZE_BITS = 256
        private const val PREFIX = "enc:v1:"
    }
}
