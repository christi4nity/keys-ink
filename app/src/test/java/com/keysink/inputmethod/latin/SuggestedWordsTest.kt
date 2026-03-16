package com.keysink.inputmethod.latin

import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SuggestedWordsTest {

    private fun makeInfo(word: String, kind: Int = SuggestedWordInfo.KIND_CORRECTION, score: Int = 100): SuggestedWordInfo {
        return SuggestedWordInfo(word, "", score, kind, "")
    }

    private fun makeSuggestedWords(
        words: List<SuggestedWordInfo>,
        typedWordValid: Boolean = false,
        willAutoCorrect: Boolean = false
    ): SuggestedWords {
        val typedWordInfo = if (words.isNotEmpty()) words[0] else null
        return SuggestedWords(
            ArrayList(words), typedWordInfo, typedWordValid, willAutoCorrect,
            false, SuggestedWords.INPUT_STYLE_TYPING, SuggestedWords.NOT_A_SEQUENCE_NUMBER
        )
    }

    // --- EMPTY ---

    @Test
    fun `EMPTY has no suggestions`() {
        assertTrue(SuggestedWords.EMPTY.isEmpty())
        assertEquals(0, SuggestedWords.EMPTY.size())
    }

    @Test
    fun `EMPTY has willAutoCorrect false`() {
        assertFalse(SuggestedWords.EMPTY.mWillAutoCorrect)
    }

    // --- size and getWord ---

    @Test
    fun `size reflects number of entries`() {
        val sw = makeSuggestedWords(listOf(
            makeInfo("typed", SuggestedWordInfo.KIND_TYPED),
            makeInfo("suggestion1"),
            makeInfo("suggestion2")
        ))
        assertEquals(3, sw.size())
    }

    @Test
    fun `getWord returns correct word at each index`() {
        val sw = makeSuggestedWords(listOf(
            makeInfo("typed", SuggestedWordInfo.KIND_TYPED),
            makeInfo("hello"),
            makeInfo("help")
        ))
        assertEquals("typed", sw.getWord(0))
        assertEquals("hello", sw.getWord(1))
        assertEquals("help", sw.getWord(2))
    }

    // --- getTypedWordInfoOrNull ---

    @Test
    fun `getTypedWordInfoOrNull returns typed word at index 0`() {
        val typed = makeInfo("cat", SuggestedWordInfo.KIND_TYPED)
        val sw = makeSuggestedWords(listOf(typed, makeInfo("catch")))
        val result = sw.getTypedWordInfoOrNull()
        assertNotNull(result)
        assertEquals("cat", result!!.mWord)
    }

    @Test
    fun `getTypedWordInfoOrNull returns null if index 0 is not KIND_TYPED`() {
        val sw = makeSuggestedWords(listOf(makeInfo("hello", SuggestedWordInfo.KIND_CORRECTION)))
        assertNull(sw.getTypedWordInfoOrNull())
    }

    @Test
    fun `getTypedWordInfoOrNull returns null for empty`() {
        assertNull(SuggestedWords.EMPTY.getTypedWordInfoOrNull())
    }

    // --- removeDupsAndTypedWord ---

    @Test
    fun `removeDupsAndTypedWord removes typed word from candidates`() {
        val candidates = arrayListOf(
            makeInfo("hello"),
            makeInfo("cat"),
            makeInfo("hello") // duplicate
        )
        val pos = SuggestedWordInfo.removeDupsAndTypedWord("cat", candidates)
        // "cat" should be removed, "hello" duplicate should be removed
        assertEquals(1, candidates.size)
        assertEquals("hello", candidates[0].mWord)
        assertEquals(1, pos) // first occurrence of "cat" was at index 1
    }

    @Test
    fun `removeDupsAndTypedWord removes case-sensitive duplicates`() {
        val candidates = arrayListOf(
            makeInfo("Hello"),
            makeInfo("Hello") // exact duplicate
        )
        SuggestedWordInfo.removeDupsAndTypedWord(null, candidates)
        assertEquals(1, candidates.size)
    }

    @Test
    fun `removeDupsAndTypedWord handles empty list`() {
        val candidates = ArrayList<SuggestedWordInfo>()
        val pos = SuggestedWordInfo.removeDupsAndTypedWord("test", candidates)
        assertEquals(-1, pos)
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `removeDupsAndTypedWord with null typed word only deduplicates`() {
        val candidates = arrayListOf(
            makeInfo("abc"),
            makeInfo("def"),
            makeInfo("abc") // dup
        )
        val pos = SuggestedWordInfo.removeDupsAndTypedWord(null, candidates)
        assertEquals(-1, pos)
        assertEquals(2, candidates.size)
        assertEquals("abc", candidates[0].mWord)
        assertEquals("def", candidates[1].mWord)
    }

    // --- getTypedWordAndPreviousSuggestions ---

    @Test
    fun `getTypedWordAndPreviousSuggestions merges without duplicates`() {
        val newTyped = makeInfo("he", SuggestedWordInfo.KIND_TYPED)
        val prev = makeSuggestedWords(listOf(
            makeInfo("h", SuggestedWordInfo.KIND_TYPED),
            makeInfo("hello"),
            makeInfo("help"),
            makeInfo("he") // same as new typed — should be deduped
        ))
        val result = SuggestedWords.getTypedWordAndPreviousSuggestions(newTyped, prev)
        assertEquals("he", result[0].mWord)
        // "hello" and "help" from previous, "he" deduped
        assertEquals(3, result.size)
        assertEquals("hello", result[1].mWord)
        assertEquals("help", result[2].mWord)
    }

    // --- SuggestedWordInfo flags ---

    @Test
    fun `SuggestedWordInfo getKind extracts kind from flags`() {
        val info = SuggestedWordInfo(
            "test", "", 100,
            SuggestedWordInfo.KIND_CORRECTION or SuggestedWordInfo.KIND_FLAG_EXACT_MATCH,
            ""
        )
        assertEquals(SuggestedWordInfo.KIND_CORRECTION, info.getKind())
        assertTrue(info.isExactMatch())
        assertFalse(info.isPossiblyOffensive())
    }

    @Test
    fun `isAppropriateForAutoCorrection checks flag`() {
        val withFlag = SuggestedWordInfo(
            "test", "", 100,
            SuggestedWordInfo.KIND_CORRECTION or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
            ""
        )
        assertTrue(withFlag.isAppropriateForAutoCorrection())

        val withoutFlag = SuggestedWordInfo("test", "", 100, SuggestedWordInfo.KIND_CORRECTION, "")
        assertFalse(withoutFlag.isAppropriateForAutoCorrection())
    }

    @Test
    fun `codePointCount matches word length`() {
        val info = makeInfo("hello")
        assertEquals(5, info.mCodePointCount)
    }

    // --- isPrediction ---

    @Test
    fun `isPrediction returns true for prediction input styles`() {
        val prediction = SuggestedWords(
            ArrayList(), null, false, false, false,
            SuggestedWords.INPUT_STYLE_PREDICTION, SuggestedWords.NOT_A_SEQUENCE_NUMBER
        )
        assertTrue(prediction.isPrediction())

        val beginningPrediction = SuggestedWords(
            ArrayList(), null, false, false, false,
            SuggestedWords.INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION,
            SuggestedWords.NOT_A_SEQUENCE_NUMBER
        )
        assertTrue(beginningPrediction.isPrediction())
    }

    @Test
    fun `isPrediction returns false for typing input style`() {
        val typing = SuggestedWords(
            ArrayList(), null, false, false, false,
            SuggestedWords.INPUT_STYLE_TYPING, SuggestedWords.NOT_A_SEQUENCE_NUMBER
        )
        assertFalse(typing.isPrediction())
    }
}
