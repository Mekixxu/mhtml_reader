package core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OutcomeTest {

    @Test
    fun `success getOrNull returns value`() {
        assertEquals(42, Outcome.Success(42).getOrNull())
    }

    @Test
    fun `failure getOrNull returns null`() {
        assertNull(Outcome.Failure(AppError.NotFound).getOrNull())
    }

    @Test
    fun `failure carries its error`() {
        val error: AppError = AppError.IoError("disk full")
        val outcome: Outcome<Nothing> = Outcome.Failure(error)
        assertEquals(true, outcome is Outcome.Failure)
        assertEquals(error, (outcome as Outcome.Failure).error)
    }

    @Test
    fun `success value is cased correctly`() {
        val outcome = Outcome.Success("data") as Outcome<Any>
        @Suppress("UNCHECKED_CAST")
        val cast = outcome as Outcome.Success<Any>
        assertEquals("data", cast.value)
    }
}