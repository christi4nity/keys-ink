/*
 * Copyright (C) 2013 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin.dictionary

import com.keysink.inputmethod.latin.NgramContext
import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.keysink.inputmethod.latin.common.ComposedData

/**
 * Abstract base class for all dictionary types. Each dictionary can produce word suggestions
 * and validate whether a word exists.
 */
abstract class Dictionary(val mDictType: String) {

    /**
     * Searches for suggestions given the current composition, n-gram context, and proximity data.
     *
     * @param composedData       Current composition snapshot (typed word + coordinates).
     * @param ngramContext        Previous-words context for n-gram scoring.
     * @param proximityInfoHandle Native pointer to the ProximityInfo object.
     * @param settingsValuesForSuggestion Not used in v0.2; reserved for future settings.
     * @param sessionId          Suggestion session identifier.
     * @param weightForLocale    Weight multiplier for this locale (0.0-1.0).
     * @param outResults         Output list that will be populated with suggestions.
     * @return the list of suggestions (same reference as [outResults]).
     */
    abstract fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: Any?,
        sessionId: Int,
        weightForLocale: Float,
        outResults: ArrayList<SuggestedWordInfo>
    ): ArrayList<SuggestedWordInfo>

    /**
     * Returns true if [word] is a valid entry in this dictionary.
     */
    abstract fun isValidWord(word: String): Boolean

    /**
     * Releases native resources held by this dictionary. After calling close, the dictionary
     * must not be used.
     */
    abstract fun close()

    companion object {
        const val TYPE_MAIN = "main"
        const val TYPE_USER_HISTORY = "history"
        const val TYPE_USER = "user"
        const val TYPE_CONTACTS = "contacts"

        /** Sentinel value indicating no valid probability. */
        const val NOT_A_PROBABILITY = -1
    }
}
