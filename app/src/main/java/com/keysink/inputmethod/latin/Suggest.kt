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

        // Supplement with edit-distance corrections the native engine may have missed
        if (isCorrectionEnabled && typedWordString.length >= 2) {
            addEditDistanceCorrections(typedWordString, ngramContext, rawSuggestions)
        }

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

    /**
     * Generates edit-distance-1 mutations of [typed] and injects any valid dictionary words
     * into [suggestions] at the front. Covers insertions, deletions, substitutions, and
     * transpositions.
     */
    private fun addEditDistanceCorrections(
        typed: String,
        ngramContext: NgramContext,
        suggestions: ArrayList<SuggestedWordInfo>
    ) {
        val lower = typed.lowercase()
        val existing = HashSet<String>(suggestions.size + 1)
        existing.add(lower)
        for (info in suggestions) {
            existing.add(info.mWord.lowercase())
        }

        val corrections = ArrayList<SuggestedWordInfo>()
        val prevWordsContext = ngramContext.extractPrevWordsContext()

        // Insertions: add each letter a-z at each position
        for (i in 0..lower.length) {
            for (c in 'a'..'z') {
                val candidate = lower.substring(0, i) + c + lower.substring(i)
                if (candidate.lowercase() !in existing && mDictionaryFacilitator.isValidWord(candidate)) {
                    existing.add(candidate.lowercase())
                    corrections.add(SuggestedWordInfo(
                        candidate, prevWordsContext,
                        EDIT_DISTANCE_SCORE,
                        SuggestedWordInfo.KIND_CORRECTION or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
                        ""
                    ))
                }
            }
        }

        // Deletions: remove each character
        for (i in lower.indices) {
            val candidate = lower.substring(0, i) + lower.substring(i + 1)
            if (candidate.isNotEmpty() && candidate !in existing && mDictionaryFacilitator.isValidWord(candidate)) {
                existing.add(candidate)
                corrections.add(SuggestedWordInfo(
                    candidate, prevWordsContext,
                    EDIT_DISTANCE_SCORE - 50_000,
                    SuggestedWordInfo.KIND_CORRECTION,
                    ""
                ))
            }
        }

        // Substitutions: replace each character with a-z
        for (i in lower.indices) {
            for (c in 'a'..'z') {
                if (c == lower[i]) continue
                val candidate = lower.substring(0, i) + c + lower.substring(i + 1)
                if (candidate !in existing && mDictionaryFacilitator.isValidWord(candidate)) {
                    existing.add(candidate)
                    corrections.add(SuggestedWordInfo(
                        candidate, prevWordsContext,
                        EDIT_DISTANCE_SCORE - 25_000,
                        SuggestedWordInfo.KIND_CORRECTION,
                        ""
                    ))
                }
            }
        }

        // Transpositions: swap adjacent characters
        for (i in 0 until lower.length - 1) {
            val chars = lower.toCharArray()
            val tmp = chars[i]
            chars[i] = chars[i + 1]
            chars[i + 1] = tmp
            val candidate = String(chars)
            if (candidate !in existing && mDictionaryFacilitator.isValidWord(candidate)) {
                existing.add(candidate)
                corrections.add(SuggestedWordInfo(
                    candidate, prevWordsContext,
                    EDIT_DISTANCE_SCORE - 10_000,
                    SuggestedWordInfo.KIND_CORRECTION,
                    ""
                ))
            }
        }

        // Insert corrections at the front of the suggestion list
        if (corrections.isNotEmpty()) {
            // Sort by score descending
            corrections.sortByDescending { it.mScore }
            suggestions.addAll(0, corrections)
        }
    }

    companion object {
        private const val SESSION_ID_TYPING = 0

        /**
         * Score threshold above which the top suggestion will trigger auto-correction
         * when the typed word is not in the dictionary.
         */
        private const val AUTO_CORRECT_THRESHOLD = 100_000

        /** Score assigned to edit-distance corrections (high enough to rank above native results). */
        private const val EDIT_DISTANCE_SCORE = 200_000
    }
}
