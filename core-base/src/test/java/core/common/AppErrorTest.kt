package core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorTest {

    @Test
    fun `singleton errors are distinct objects`() {
        assertNotEquals(AppError.NotFound, AppError.PermissionDenied)
    }

    @Test
    fun `IoError carries detail and cause`() {
        val cause = IllegalStateException("boom")
        val err = AppError.IoError("detail", cause)
        assertEquals("detail", err.detail)
        assertEquals(cause, err.t)
    }

    @Test
    fun `errors are throwable`() {
        var caught: Throwable? = null
        try {
            throw AppError.InvalidUri
        } catch (t: Throwable) {
            caught = t
        }
        assertTrue(caught is AppError.InvalidUri)
        assertEquals("Invalid URI", caught?.message)
    }
}