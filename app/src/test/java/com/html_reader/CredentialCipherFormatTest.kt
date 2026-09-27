package com.html_reader

import android.util.Base64
import core.security.CredentialCipher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CredentialCipherFormatTest {

    private val cipher = CredentialCipher()

    @Test
    fun validCiphertext_isRecognized() {
        val iv = Base64.encodeToString(ByteArray(12), Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(ByteArray(32), Base64.NO_WRAP)
        assertTrue(cipher.isEncrypted("enc:v1:$iv:$ciphertext"))
    }

    @Test
    fun plaintextPasswordStartingWithPrefix_isNotRecognized() {
        // 真实密码恰好以 enc:v1: 开头时不能被当作密文，否则会明文落库
        assertFalse(cipher.isEncrypted("enc:v1:my-real-password"))
    }

    @Test
    fun malformedPayloads_areNotRecognized() {
        assertFalse(cipher.isEncrypted("enc:v1:onlyonepart"))
        assertFalse(cipher.isEncrypted("enc:v1::"))
        assertFalse(cipher.isEncrypted("enc:v1:!!!:???"))
        // 密文长度不足以容纳 GCM tag
        val iv = Base64.encodeToString(ByteArray(12), Base64.NO_WRAP)
        val shortCiphertext = Base64.encodeToString(ByteArray(8), Base64.NO_WRAP)
        assertFalse(cipher.isEncrypted("enc:v1:$iv:$shortCiphertext"))
        // IV 长度不正确
        val shortIv = Base64.encodeToString(ByteArray(4), Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(ByteArray(32), Base64.NO_WRAP)
        assertFalse(cipher.isEncrypted("enc:v1:$shortIv:$ciphertext"))
    }

    @Test
    fun plainPassword_isNotRecognized() {
        assertFalse(cipher.isEncrypted("secret"))
        assertFalse(cipher.isEncrypted(""))
    }
}
