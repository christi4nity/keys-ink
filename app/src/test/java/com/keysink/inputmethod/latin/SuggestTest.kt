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

    private fun compose(word: String): WordComposer {
        val wc = WordComposer()
        for (ch in word) {
            wc.addCodePoint(ch.code, -1, -1)
        }
        return wc
    }

    private fun getSuggestions(typed: String): SuggestedWords {
        return suggest.getSuggestedWords(
            compose(typed), NgramContext.EMPTY_PREV_WORDS_INFO, 0L
        )
    }

    // --- Auto-correct ---

    @Test
    fun `willAutoCorrect is always false`() {
        val topSuggestion = SuggestedWordInfo(
            "hello", "", Int.MAX_VALUE,
            SuggestedWordInfo.KIND_CORRECTION or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
            ""
        )
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(arrayListOf(topSuggestion))
        whenever(mockFacilitator.isValidWord(any())).thenReturn(false)

        val result = getSuggestions("helo")
        assertFalse(result.mWillAutoCorrect)
    }

    // --- Basic ordering ---

    @Test
    fun `typed word is at index 0`() {
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(ArrayList())
        whenever(mockFacilitator.isValidWord(any())).thenReturn(true)

        val result = getSuggestions("cat")
        assertEquals("cat", result.getWord(0))
        assertTrue(result.mTypedWordValid)
    }

    @Test
    fun `typed word valid is false when not in dictionary`() {
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(ArrayList())
        whenever(mockFacilitator.isValidWord(any())).thenReturn(false)

        val result = getSuggestions("xyzzy")
        assertFalse(result.mTypedWordValid)
    }

    @Test
    fun `native suggestions follow typed word in results`() {
        val suggestion = SuggestedWordInfo(
            "hello", "", 200_000, SuggestedWordInfo.KIND_CORRECTION, ""
        )
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(arrayListOf(suggestion))
        whenever(mockFacilitator.isValidWord(any())).thenReturn(false)

        val result = getSuggestions("helo")
        assertTrue(result.size() >= 2)
        assertEquals("helo", result.getWord(0))
        assertEquals("hello", result.getWord(1))
    }

    @Test
    fun `single character typed word produces no edit-distance corrections`() {
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(ArrayList())
        whenever(mockFacilitator.isValidWord(any())).thenReturn(false)

        val result = getSuggestions("h")
        // Only the typed word, no edit-distance corrections (length < 2)
        assertEquals(1, result.size())
        assertEquals("h", result.getWord(0))
    }

    // --- Edit-distance corrections ---

    @Test
    fun `edit-distance finds insertion corrections`() {
        // Typing "helo" should find "hello" via insertion of 'l'
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(ArrayList())
        whenever(mockFacilitator.isValidWord(any())).thenAnswer { invocation ->
            val word = invocation.getArgument<String>(0)
            word == "hello"
        }

        val result = getSuggestions("helo")
        val words = (0 until result.size()).map { result.getWord(it) }
        assertTrue("hello" in words, "Should find 'hello' via insertion: $words")
    }

    @Test
    fun `edit-distance finds deletion corrections`() {
        // Typing "helllo" should find "hello" via deletion of extra 'l'
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(ArrayList())
        whenever(mockFacilitator.isValidWord(any())).thenAnswer { invocation ->
            val word = invocation.getArgument<String>(0)
            word == "hello"
        }

        val result = getSuggestions("helllo")
        val words = (0 until result.size()).map { result.getWord(it) }
        assertTrue("hello" in words, "Should find 'hello' via deletion: $words")
    }

    @Test
    fun `edit-distance finds substitution corrections`() {
        // Typing "hallo" should find "hello" via substitution of 'a' -> 'e'
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(ArrayList())
        whenever(mockFacilitator.isValidWord(any())).thenAnswer { invocation ->
            val word = invocation.getArgument<String>(0)
            word == "hello"
        }

        val result = getSuggestions("hallo")
        val words = (0 until result.size()).map { result.getWord(it) }
        assertTrue("hello" in words, "Should find 'hello' via substitution: $words")
    }

    @Test
    fun `edit-distance finds transposition corrections`() {
        // Typing "hlelo" should find "hello" via transposition of 'l' and 'e'
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(ArrayList())
        whenever(mockFacilitator.isValidWord(any())).thenAnswer { invocation ->
            val word = invocation.getArgument<String>(0)
            word == "hello"
        }

        val result = getSuggestions("hlelo")
        val words = (0 until result.size()).map { result.getWord(it) }
        assertTrue("hello" in words, "Should find 'hello' via transposition: $words")
    }

    @Test
    fun `edit-distance corrections are placed before native suggestions`() {
        val nativeSuggestion = SuggestedWordInfo(
            "help", "", 50_000, SuggestedWordInfo.KIND_CORRECTION, ""
        )
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(arrayListOf(nativeSuggestion))
        whenever(mockFacilitator.isValidWord(any())).thenAnswer { invocation ->
            val word = invocation.getArgument<String>(0)
            word == "hello"
        }

        val result = getSuggestions("helo")
        val words = (0 until result.size()).map { result.getWord(it) }
        // Index 0 = typed word, then edit-distance corrections, then native
        assertEquals("helo", words[0])
        val helloIdx = words.indexOf("hello")
        val helpIdx = words.indexOf("help")
        assertTrue(helloIdx > 0 && helloIdx < helpIdx,
            "Edit-distance 'hello' should come before native 'help': $words")
    }

    @Test
    fun `edit-distance does not duplicate native suggestions`() {
        // If native engine already returns "hello", edit-distance should not add it again
        val nativeSuggestion = SuggestedWordInfo(
            "hello", "", 200_000, SuggestedWordInfo.KIND_CORRECTION, ""
        )
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(arrayListOf(nativeSuggestion))
        whenever(mockFacilitator.isValidWord(any())).thenAnswer { invocation ->
            val word = invocation.getArgument<String>(0)
            word == "hello"
        }

        val result = getSuggestions("helo")
        val words = (0 until result.size()).map { result.getWord(it) }
        val helloCount = words.count { it == "hello" }
        assertEquals(1, helloCount, "Should not have duplicate 'hello': $words")
    }

    // --- Dedup and max ---

    @Test
    fun `duplicates from native engine are removed`() {
        val suggestions = arrayListOf(
            SuggestedWordInfo("hello", "", 200_000, SuggestedWordInfo.KIND_CORRECTION, ""),
            SuggestedWordInfo("hello", "", 100_000, SuggestedWordInfo.KIND_CORRECTION, "")
        )
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(suggestions)
        whenever(mockFacilitator.isValidWord(any())).thenReturn(false)

        val result = getSuggestions("helo")
        val helloCount = (0 until result.size()).count { result.getWord(it) == "hello" }
        assertEquals(1, helloCount)
    }

    @Test
    fun `results are capped at MAX_SUGGESTIONS`() {
        val manySuggestions = ArrayList<SuggestedWordInfo>()
        for (i in 0 until SuggestedWords.MAX_SUGGESTIONS + 5) {
            manySuggestions.add(SuggestedWordInfo(
                "word$i", "", 100_000 - i, SuggestedWordInfo.KIND_CORRECTION, ""
            ))
        }
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(manySuggestions)
        whenever(mockFacilitator.isValidWord(any())).thenReturn(false)

        val result = getSuggestions("wo")
        assertTrue(result.size() <= SuggestedWords.MAX_SUGGESTIONS,
            "Should be capped at ${SuggestedWords.MAX_SUGGESTIONS} but was ${result.size()}")
    }

    // --- Input style ---

    @Test
    fun `result has INPUT_STYLE_TYPING`() {
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(ArrayList())
        whenever(mockFacilitator.isValidWord(any())).thenReturn(false)

        val result = getSuggestions("test")
        assertEquals(SuggestedWords.INPUT_STYLE_TYPING, result.mInputStyle)
    }
}
