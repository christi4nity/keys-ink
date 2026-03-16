package com.keysink.inputmethod.latin

import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.keysink.inputmethod.latin.dictionary.DictionaryFacilitator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.*

class SuggestTest {

    private lateinit var mockFacilitator: DictionaryFacilitator
    private lateinit var suggest: Suggest

    @BeforeEach
    fun setUp() {
        mockFacilitator = mock()
        suggest = Suggest(mockFacilitator)
    }

    @Test
    fun `getSuggestedWords never sets willAutoCorrect`() {
        // High-scoring correction with auto-correct flag — would have triggered auto-correct before
        val topSuggestion = SuggestedWordInfo(
            "hello", "", Int.MAX_VALUE,
            SuggestedWordInfo.KIND_CORRECTION or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
            ""
        )
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(arrayListOf(topSuggestion))
        whenever(mockFacilitator.isValidWord(any())).thenReturn(false)

        val wordComposer = WordComposer()
        wordComposer.addCodePoint('h'.code, -1, -1)
        wordComposer.addCodePoint('e'.code, -1, -1)
        wordComposer.addCodePoint('l'.code, -1, -1)
        wordComposer.addCodePoint('o'.code, -1, -1)

        val result = suggest.getSuggestedWords(
            wordComposer, NgramContext.EMPTY_PREV_WORDS_INFO, 0L, true
        )

        assertFalse(result.mWillAutoCorrect, "mWillAutoCorrect should always be false")
    }

    @Test
    fun `typed word is at index 0`() {
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(ArrayList())
        whenever(mockFacilitator.isValidWord(any())).thenReturn(true)

        val wordComposer = WordComposer()
        wordComposer.addCodePoint('c'.code, -1, -1)
        wordComposer.addCodePoint('a'.code, -1, -1)
        wordComposer.addCodePoint('t'.code, -1, -1)

        val result = suggest.getSuggestedWords(
            wordComposer, NgramContext.EMPTY_PREV_WORDS_INFO, 0L, true
        )

        assertEquals("cat", result.getWord(0))
        assertTrue(result.mTypedWordValid)
    }

    @Test
    fun `suggestions follow typed word in results`() {
        val suggestion = SuggestedWordInfo(
            "hello", "", 200_000, SuggestedWordInfo.KIND_CORRECTION, ""
        )
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(arrayListOf(suggestion))
        whenever(mockFacilitator.isValidWord(any())).thenReturn(false)

        val wordComposer = WordComposer()
        wordComposer.addCodePoint('h'.code, -1, -1)
        wordComposer.addCodePoint('e'.code, -1, -1)
        wordComposer.addCodePoint('l'.code, -1, -1)
        wordComposer.addCodePoint('o'.code, -1, -1)

        val result = suggest.getSuggestedWords(
            wordComposer, NgramContext.EMPTY_PREV_WORDS_INFO, 0L, true
        )

        assertTrue(result.size() >= 2, "Should have typed word + at least one suggestion")
        assertEquals("helo", result.getWord(0)) // typed word
        assertEquals("hello", result.getWord(1)) // suggestion
    }
}
