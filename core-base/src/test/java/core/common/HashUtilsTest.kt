package core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HashUtilsTest {

    @Test
    fun `sha256 empty string matches known digest`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            HashUtils.sha256("")
        )
    }

    @Test
    fun `sha256 known vector matches`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HashUtils.sha256("abc")
        )
    }

    @Test
    fun `sha256 is deterministic and utf8 aware`() {
        assertEquals(HashUtils.sha256("中文文档"), HashUtils.sha256("中文文档"))
        assertEquals(64, HashUtils.sha256("中文文档").length)
    }

    @Test
    fun `sha256 different inputs differ`() {
        val a = HashUtils.sha256("hello")
        val b = HashUtils.sha256("world")
        assertEquals(64, a.length)
        assertEquals(64, b.length)
        assertEquals(false, a == b)
    }
}