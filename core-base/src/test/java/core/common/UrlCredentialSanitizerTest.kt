package core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlCredentialSanitizerTest {

    @Test
    fun stripPassword_removesPasswordKeepsUser() {
        assertEquals(
            "ftp://user@host:21/path/file.mhtml",
            UrlCredentialSanitizer.stripPassword("ftp://user:secret@host:21/path/file.mhtml")
        )
    }

    @Test
    fun stripPassword_removesPasswordOnlyUserInfo() {
        assertEquals(
            "ftp://host:21/path",
            UrlCredentialSanitizer.stripPassword("ftp://:secret@host:21/path")
        )
    }

    @Test
    fun stripPassword_keepsPathWithoutPassword() {
        val path = "ftp://user@host:21/path"
        assertEquals(path, UrlCredentialSanitizer.stripPassword(path))
    }

    @Test
    fun stripPassword_keepsPlainLocalPath() {
        val path = "/storage/emulated/0/Documents/file.mhtml"
        assertEquals(path, UrlCredentialSanitizer.stripPassword(path))
    }

    @Test
    fun stripPassword_keepsUrlWithoutUserInfo() {
        val path = "smb://host:445/share/file.pdf"
        assertEquals(path, UrlCredentialSanitizer.stripPassword(path))
    }

    @Test
    fun stripPassword_handlesEncodedPasswordWithAtSign() {
        assertEquals(
            "ftp://user@host/file.mhtml",
            UrlCredentialSanitizer.stripPassword("ftp://user:p%40ss@host/file.mhtml")
        )
    }

    @Test
    fun sanitizeText_erasesPasswordInExceptionMessage() {
        val message = "java.io.FileNotFoundException: ftp://alice:s3cret@host:21/private/a.mhtml"
        assertEquals(
            "java.io.FileNotFoundException: ftp://alice@host:21/private/a.mhtml",
            UrlCredentialSanitizer.sanitizeText(message)
        )
    }

    @Test
    fun sanitizeText_keepsTextWithoutCredentials() {
        val message = "Connection refused: /storage/emulated/0/a.mhtml"
        assertEquals(message, UrlCredentialSanitizer.sanitizeText(message))
    }
}
