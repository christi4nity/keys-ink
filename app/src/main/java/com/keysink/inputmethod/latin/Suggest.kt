/*
 * Copyright (C) 2008 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin

import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.keysink.inputmethod.latin.dictionary.DictionaryFacilitator

/**
 * Orchestrates word suggestion generation by querying the [DictionaryFacilitator] and
 * post-processing the raw results into a [SuggestedWords] object.
 */
class Suggest(private val mDictionaryFacilitator: DictionaryFacilitator) {

    /**
     * Generates suggestions for the current word being composed.
     *
     * @param wordComposer          Tracks the word being typed, including touch coordinates.
     * @param ngramContext           Previous-words context for n-gram scoring.
     * @param proximityInfoHandle    Native handle to ProximityInfo for spatial correction.
     * @param isCorrectionEnabled    Whether auto-correction is allowed.
     * @return a [SuggestedWords] containing the typed word at index 0, followed by suggestions.
     */
    fun getSuggestedWords(
        wordComposer: WordComposer,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        isCorrectionEnabled: Boolean
    ): SuggestedWords {
        val typedWordString = wordComposer.typedWord
        val composedData = wordComposer.getComposedDataSnapshot()

        // Query the dictionary facilitator for raw suggestions
        val rawSuggestions = mDictionaryFacilitator.getSuggestionResults(
            composedData,
            ngramContext,
            proximityInfoHandle,
            SESSION_ID_TYPING
        )

        // Remove duplicates (case-insensitive) and the typed word itself
        SuggestedWordInfo.removeDupsAndTypedWord(typedWordString, rawSuggestions)

        // Build the typed word info entry
        val typedWordInfo = SuggestedWordInfo(
            typedWordString,
            ngramContext.extractPrevWordsContext(),
            SuggestedWordInfo.MAX_SCORE,
            SuggestedWordInfo.KIND_TYPED,
            ""
        )

        // Check if the typed word is valid in any dictionary
        val typedWordValid = mDictionaryFacilitator.isValidWord(typedWordString)

        // Determine auto-correction
        var willAutoCorrect = false
        if (isCorrectionEnabled
            && rawSuggestions.isNotEmpty()
            && typedWordString.isNotEmpty()
            && !typedWordValid
        ) {
            val topSuggestion = rawSuggestions[0]
            if (topSuggestion.mScore > AUTO_CORRECT_THRESHOLD
                && topSuggestion.isAppropriateForAutoCorrection()
            ) {
                willAutoCorrect = true
            }
        }

        // Assemble final list: typed word at index 0, then suggestions
        val finalSuggestions = ArrayList<SuggestedWordInfo>(rawSuggestions.size + 1)
        finalSuggestions.add(typedWordInfo)
        finalSuggestions.addAll(rawSuggestions)

        // Cap at MAX_SUGGESTIONS
        while (finalSuggestions.size > SuggestedWords.MAX_SUGGESTIONS) {
            finalSuggestions.removeAt(finalSuggestions.size - 1)
        }

        return SuggestedWords(
            mSuggestedWordInfoList = finalSuggestions,
            mTypedWordInfo = typedWordInfo,
            mTypedWordValid = typedWordValid,
            mWillAutoCorrect = willAutoCorrect,
            mIsObsoleteSuggestions = false,
            mInputStyle = SuggestedWords.INPUT_STYLE_TYPING,
            mSequenceNumber = SuggestedWords.NOT_A_SEQUENCE_NUMBER
        )
    }

    companion object {
        private const val SESSION_ID_TYPING = 0

        /**
         * Score threshold above which the top suggestion will trigger auto-correction
         * when the typed word is not in the dictionary.
         */
        private const val AUTO_CORRECT_THRESHOLD = 100_000
    }
}
