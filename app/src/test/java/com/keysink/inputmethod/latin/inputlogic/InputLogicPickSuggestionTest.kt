package com.keysink.inputmethod.latin.inputlogic

import com.keysink.inputmethod.latin.LatinIME
import com.keysink.inputmethod.latin.RichInputConnection
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

    @Test
    fun `picking suggestion deletes typed word and commits picked word plus space`() {
        val wordComposer = getWordComposer()
        wordComposer.addCodePoint('h'.code, -1, -1)
        wordComposer.addCodePoint('e'.code, -1, -1)
        wordComposer.addCodePoint('l'.code, -1, -1)
        wordComposer.addCodePoint('o'.code, -1, -1)

        val suggestionInfo = SuggestedWordInfo(
            "hello", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION, ""
        )

        inputLogic.onPickSuggestionManually(suggestionInfo, mockSettings)

        // Should delete 4 chars ("helo"), commit "hello", then commit " "
        val inOrder = inOrder(mockConnection)
        inOrder.verify(mockConnection).deleteTextBeforeCursor(4)
        inOrder.verify(mockConnection).commitText(eq("hello"), eq(1))
        inOrder.verify(mockConnection).commitText(eq(" "), eq(1))

        // Word composer should be reset
        assert(!wordComposer.isComposingWord) { "Word composer should be reset" }
    }

    @Test
    fun `picking typed word does not delete or re-commit, just adds space`() {
        val wordComposer = getWordComposer()
        wordComposer.addCodePoint('c'.code, -1, -1)
        wordComposer.addCodePoint('a'.code, -1, -1)
        wordComposer.addCodePoint('t'.code, -1, -1)

        val typedWordInfo = SuggestedWordInfo(
            "cat", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED, ""
        )

        inputLogic.onPickSuggestionManually(typedWordInfo, mockSettings)

        // Should NOT call deleteTextBeforeCursor (word matches what's in editor)
        verify(mockConnection, never()).deleteTextBeforeCursor(any())
        // Should commit just a space
        verify(mockConnection).commitText(eq(" "), eq(1))

        assert(!wordComposer.isComposingWord) { "Word composer should be reset" }
    }

    @Test
    fun `picking suggestion clears the suggestion strip`() {
        val wordComposer = getWordComposer()
        wordComposer.addCodePoint('t'.code, -1, -1)

        val info = SuggestedWordInfo(
            "the", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION, ""
        )

        inputLogic.onPickSuggestionManually(info, mockSettings)

        verify(mockAccessor).setNeutralSuggestionStrip()
    }
}
