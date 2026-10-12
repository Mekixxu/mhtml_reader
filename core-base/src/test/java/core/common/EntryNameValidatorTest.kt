package core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryNameValidatorTest {

    @Test
    fun isValid_acceptsNormalNames() {
        assertTrue(EntryNameValidator.isValid("a.txt"))
        assertTrue(EntryNameValidator.isValid("文档.v1.mhtml"))
        assertTrue(EntryNameValidator.isValid("a b"))
        assertTrue(EntryNameValidator.isValid("a..b"))
        assertTrue(EntryNameValidator.isValid("..hidden"))
    }

    @Test
    fun isValid_rejectsEmptyDotDotSeparatorsAndControlCharacters() {
        assertFalse(EntryNameValidator.isValid(""))
        assertFalse(EntryNameValidator.isValid("   "))
        assertFalse(EntryNameValidator.isValid("."))
        assertFalse(EntryNameValidator.isValid(".."))
        assertFalse(EntryNameValidator.isValid("a/b"))
        assertFalse(EntryNameValidator.isValid("a\\b"))
        assertFalse(EntryNameValidator.isValid("/tmp/x"))
        assertFalse(EntryNameValidator.isValid("a\u0000b"))
        assertFalse(EntryNameValidator.isValid("a\nb"))
        assertFalse(EntryNameValidator.isValid("a\tb"))
    }

    @Test
    fun isValidSmb_rejectsWindowsReservedCharacters() {
        assertFalse(EntryNameValidator.isValidSmb("a:b"))
        assertFalse(EntryNameValidator.isValidSmb("a*b"))
        assertFalse(EntryNameValidator.isValidSmb("a?b"))
        assertFalse(EntryNameValidator.isValidSmb("a\"b"))
        assertFalse(EntryNameValidator.isValidSmb("a<b"))
        assertFalse(EntryNameValidator.isValidSmb("a>b"))
        assertFalse(EntryNameValidator.isValidSmb("a|b"))
        assertTrue(EntryNameValidator.isValidSmb("正常名称.v2.mhtml"))
    }
}
