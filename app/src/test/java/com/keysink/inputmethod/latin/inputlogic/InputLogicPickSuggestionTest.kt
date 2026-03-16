package com.keysink.inputmethod.latin.inputlogic

import com.keysink.inputmethod.latin.LatinIME
import com.keysink.inputmethod.latin.RichInputConnection
import com.keysink.inputmethod.latin.SuggestedWords
import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.keysink.inputmethod.latin.WordComposer
import com.keysink.inputmethod.latin.settings.SettingsValues
import com.keysink.inputmethod.latin.suggestions.SuggestionStripViewAccessor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import sun.misc.Unsafe

class InputLogicPickSuggestionTest {

    private lateinit var inputLogic: InputLogic
    private lateinit var mockConnection: RichInputConnection
    private lateinit var mockAccessor: SuggestionStripViewAccessor
    private lateinit var mockSettings: SettingsValues

    @BeforeEach
    fun setUp() {
        val mockLatinIME: LatinIME = mock()
        mockConnection = mock()
        mockAccessor = mock()
        mockSettings = mock()

        inputLogic = InputLogic(mockLatinIME)
        inputLogic.initSuggest(null, mockAccessor)

        // Replace the final mConnection field with our mock using Unsafe
        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as Unsafe
        val connectionField = InputLogic::class.java.getDeclaredField("mConnection")
        val offset = unsafe.objectFieldOffset(connectionField)
        unsafe.putObject(inputLogic, offset, mockConnection)
    }

    private fun getWordComposer(): WordComposer {
        val field = InputLogic::class.java.getDeclaredField("mWordComposer")
        field.isAccessible = true
        return field.get(inputLogic) as WordComposer
    }

    private fun getSuggestedWordsField(): SuggestedWords {
        val field = InputLogic::class.java.getDeclaredField("mSuggestedWords")
        field.isAccessible = true
        return field.get(inputLogic) as SuggestedWords
    }

    private fun typeWord(word: String) {
        val wc = getWordComposer()
        for (ch in word) {
            wc.addCodePoint(ch.code, -1, -1)
        }
    }

    // --- Pick suggestion: replace typed word ---

    @Test
    fun `picking suggestion deletes typed word and commits picked word plus space`() {
        typeWord("helo")

        val suggestionInfo = SuggestedWordInfo(
            "hello", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION, ""
        )

        inputLogic.onPickSuggestionManually(suggestionInfo, mockSettings)

        val inOrder = inOrder(mockConnection)
        inOrder.verify(mockConnection).deleteTextBeforeCursor(4)
        inOrder.verify(mockConnection).commitText(eq("hello"), eq(1))
        inOrder.verify(mockConnection).commitText(eq(" "), eq(1))

        assert(!getWordComposer().isComposingWord) { "Word composer should be reset" }
    }

    @Test
    fun `picking longer suggestion deletes correct number of chars`() {
        typeWord("teh")

        val info = SuggestedWordInfo(
            "the", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION, ""
        )

        inputLogic.onPickSuggestionManually(info, mockSettings)

        verify(mockConnection).deleteTextBeforeCursor(3)
        verify(mockConnection).commitText(eq("the"), eq(1))
    }

    // --- Pick typed word: no replacement ---

    @Test
    fun `picking typed word does not delete or re-commit, just adds space`() {
        typeWord("cat")

        val typedWordInfo = SuggestedWordInfo(
            "cat", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED, ""
        )

        inputLogic.onPickSuggestionManually(typedWordInfo, mockSettings)

        verify(mockConnection, never()).deleteTextBeforeCursor(any())
        verify(mockConnection).commitText(eq(" "), eq(1))
        assert(!getWordComposer().isComposingWord) { "Word composer should be reset" }
    }

    // --- Edge cases ---

    @Test
    fun `picking suggestion with empty composer just commits space`() {
        // No word typed — edge case
        val info = SuggestedWordInfo(
            "hello", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION, ""
        )

        inputLogic.onPickSuggestionManually(info, mockSettings)

        // Empty typed word should not trigger delete
        verify(mockConnection, never()).deleteTextBeforeCursor(any())
        // Should still commit a space
        verify(mockConnection).commitText(eq(" "), eq(1))
    }

    @Test
    fun `picking suggestion with single character typed word works`() {
        typeWord("t")

        val info = SuggestedWordInfo(
            "the", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION, ""
        )

        inputLogic.onPickSuggestionManually(info, mockSettings)

        verify(mockConnection).deleteTextBeforeCursor(1)
        verify(mockConnection).commitText(eq("the"), eq(1))
        verify(mockConnection).commitText(eq(" "), eq(1))
    }

    // --- State cleanup ---

    @Test
    fun `picking suggestion clears the suggestion strip`() {
        typeWord("t")

        val info = SuggestedWordInfo(
            "the", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION, ""
        )

        inputLogic.onPickSuggestionManually(info, mockSettings)

        verify(mockAccessor).setNeutralSuggestionStrip()
    }

    @Test
    fun `picking suggestion resets word composer`() {
        typeWord("hello")

        val info = SuggestedWordInfo(
            "hello", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED, ""
        )

        inputLogic.onPickSuggestionManually(info, mockSettings)

        val wc = getWordComposer()
        assertFalse(wc.isComposingWord)
        assertEquals("", wc.typedWord)
        assertEquals(0, wc.size())
    }

    @Test
    fun `after picking, internal suggested words is reset to EMPTY`() {
        typeWord("test")

        val info = SuggestedWordInfo(
            "testing", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION, ""
        )

        inputLogic.onPickSuggestionManually(info, mockSettings)

        val words = getSuggestedWordsField()
        assertTrue(words.isEmpty())
    }

    // --- Multiple picks in sequence ---

    @Test
    fun `can pick multiple suggestions sequentially`() {
        // First word
        typeWord("helo")
        inputLogic.onPickSuggestionManually(
            SuggestedWordInfo("hello", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION, ""),
            mockSettings
        )

        // Second word
        typeWord("wrld")
        inputLogic.onPickSuggestionManually(
            SuggestedWordInfo("world", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION, ""),
            mockSettings
        )

        // Verify both deletions and commits happened
        verify(mockConnection, times(2)).deleteTextBeforeCursor(4) // "helo" and "wrld" are both 4 chars
        verify(mockConnection).commitText(eq("hello"), eq(1))
        verify(mockConnection).commitText(eq("world"), eq(1))
        verify(mockConnection, times(2)).commitText(eq(" "), eq(1))
    }

    private fun assertFalse(value: Boolean) {
        org.junit.jupiter.api.Assertions.assertFalse(value)
    }

    private fun assertEquals(expected: Any?, actual: Any?) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual)
    }

    private fun assertTrue(value: Boolean) {
        org.junit.jupiter.api.Assertions.assertTrue(value)
    }
}
