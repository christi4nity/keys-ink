package com.keysink.inputmethod.latin.suggestions

import com.keysink.inputmethod.latin.SuggestedWords

interface SuggestionStripViewAccessor {
    fun showSuggestionStrip(suggestedWords: SuggestedWords)
    fun setNeutralSuggestionStrip()
}
