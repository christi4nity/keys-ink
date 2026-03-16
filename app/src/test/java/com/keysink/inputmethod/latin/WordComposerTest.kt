package com.keysink.inputmethod.latin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class WordComposerTest {

    @Test
    fun `new composer has empty typed word`() {
        val wc = WordComposer()
        assertEquals("", wc.typedWord)
        assertFalse(wc.isComposingWord)
    }

    @Test
    fun `addCodePoint builds the typed word`() {
        val wc = WordComposer()
        wc.addCodePoint('h'.code, -1, -1)
        wc.addCodePoint('i'.code, -1, -1)
        assertEquals("hi", wc.typedWord)
        assertTrue(wc.isComposingWord)
    }

    @Test
    fun `deleteLast removes last character`() {
        val wc = WordComposer()
        wc.addCodePoint('a'.code, -1, -1)
        wc.addCodePoint('b'.code, -1, -1)
        wc.addCodePoint('c'.code, -1, -1)
        wc.deleteLast()
        assertEquals("ab", wc.typedWord)
        assertTrue(wc.isComposingWord)
    }

    @Test
    fun `deleteLast on single char resets to empty`() {
        val wc = WordComposer()
        wc.addCodePoint('x'.code, -1, -1)
        wc.deleteLast()
        assertEquals("", wc.typedWord)
        assertFalse(wc.isComposingWord)
    }

    @Test
    fun `deleteLast on empty is a no-op`() {
        val wc = WordComposer()
        wc.deleteLast() // should not throw
        assertEquals("", wc.typedWord)
        assertFalse(wc.isComposingWord)
    }

    @Test
    fun `reset clears everything`() {
        val wc = WordComposer()
        wc.addCodePoint('t'.code, -1, -1)
        wc.addCodePoint('e'.code, -1, -1)
        wc.addCodePoint('s'.code, -1, -1)
        wc.addCodePoint('t'.code, -1, -1)
        wc.reset()
        assertEquals("", wc.typedWord)
        assertFalse(wc.isComposingWord)
        assertEquals(0, wc.size())
    }

    @Test
    fun `copy constructor creates independent snapshot`() {
        val original = WordComposer()
        original.addCodePoint('a'.code, -1, -1)
        original.addCodePoint('b'.code, -1, -1)

        val copy = WordComposer(original)
        assertEquals("ab", copy.typedWord)

        // Mutating original does not affect copy
        original.addCodePoint('c'.code, -1, -1)
        assertEquals("ab", copy.typedWord)
        assertEquals("abc", original.typedWord)
    }

    @Test
    fun `size tracks code point count`() {
        val wc = WordComposer()
        assertEquals(0, wc.size())
        wc.addCodePoint('a'.code, -1, -1)
        assertEquals(1, wc.size())
        wc.addCodePoint('b'.code, -1, -1)
        assertEquals(2, wc.size())
        wc.deleteLast()
        assertEquals(1, wc.size())
    }

    @Test
    fun `isSingleLetter is true only for one character`() {
        val wc = WordComposer()
        assertFalse(wc.isSingleLetter())
        wc.addCodePoint('x'.code, -1, -1)
        assertTrue(wc.isSingleLetter())
        wc.addCodePoint('y'.code, -1, -1)
        assertFalse(wc.isSingleLetter())
    }

    @Test
    fun `caps tracking for first-char-only capitalized`() {
        val wc = WordComposer()
        wc.addCodePoint('H'.code, -1, -1)
        wc.addCodePoint('e'.code, -1, -1)
        wc.addCodePoint('l'.code, -1, -1)
        wc.addCodePoint('l'.code, -1, -1)
        wc.addCodePoint('o'.code, -1, -1)
        assertTrue(wc.isOrWillBeOnlyFirstCharCapitalized())
        assertFalse(wc.isAllUpperCase())
    }

    @Test
    fun `caps tracking for all uppercase`() {
        val wc = WordComposer()
        wc.addCodePoint('H'.code, -1, -1)
        wc.addCodePoint('I'.code, -1, -1)
        assertTrue(wc.isAllUpperCase())
    }

    @Test
    fun `respects MAX_WORD_LENGTH`() {
        val wc = WordComposer()
        for (i in 0 until WordComposer.MAX_WORD_LENGTH + 5) {
            wc.addCodePoint('a'.code, -1, -1)
        }
        // typedWord should be capped at MAX_WORD_LENGTH chars
        assertEquals(WordComposer.MAX_WORD_LENGTH, wc.typedWord.length)
        // but size() tracks the actual count
        assertEquals(WordComposer.MAX_WORD_LENGTH + 5, wc.size())
    }

    @Test
    fun `getComposedDataSnapshot returns correct typed word`() {
        val wc = WordComposer()
        wc.addCodePoint('t'.code, 50, 100)
        wc.addCodePoint('e'.code, 80, 100)
        val snapshot = wc.getComposedDataSnapshot()
        assertEquals("te", snapshot.mTypedWord)
        assertFalse(snapshot.mIsBatchMode)
    }
}
