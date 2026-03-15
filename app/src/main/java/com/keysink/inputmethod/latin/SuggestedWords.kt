/*
 * Copyright (C) 2010 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin

import com.keysink.inputmethod.latin.common.StringUtils

/**
 * Holds a list of word suggestions and metadata about the current suggestion state.
 */
class SuggestedWords(
    @JvmField val mSuggestedWordInfoList: ArrayList<SuggestedWordInfo>,
    @JvmField val mTypedWordInfo: SuggestedWordInfo?,
    @JvmField val mTypedWordValid: Boolean,
    @JvmField val mWillAutoCorrect: Boolean,
    @JvmField val mIsObsoleteSuggestions: Boolean,
    @JvmField val mInputStyle: Int,
    @JvmField val mSequenceNumber: Int
) {
    fun isEmpty(): Boolean = mSuggestedWordInfoList.isEmpty()

    fun size(): Int = mSuggestedWordInfoList.size

    fun getWord(index: Int): String = mSuggestedWordInfoList[index].mWord

    fun getInfo(index: Int): SuggestedWordInfo = mSuggestedWordInfoList[index]

    fun isPrediction(): Boolean =
        mInputStyle == INPUT_STYLE_PREDICTION ||
        mInputStyle == INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION

    fun getTypedWordInfoOrNull(): SuggestedWordInfo? {
        if (INDEX_OF_TYPED_WORD >= size()) return null
        val info = getInfo(INDEX_OF_TYPED_WORD)
        return if (info.getKind() == SuggestedWordInfo.KIND_TYPED) info else null
    }

    override fun toString(): String =
        "SuggestedWords: mTypedWordValid=$mTypedWordValid mWillAutoCorrect=$mWillAutoCorrect " +
        "mInputStyle=$mInputStyle words=${mSuggestedWordInfoList.toTypedArray().contentToString()}"

    // -----------------------------------------------------------------------------------------
    // SuggestedWordInfo
    // -----------------------------------------------------------------------------------------

    /**
     * Metadata for a single suggestion entry.
     *
     * @param mWord            The suggested string.
     * @param mPrevWordsContext Previous-words context used to generate this suggestion.
     * @param mScore           Confidence score (higher is better).
     * @param mKindAndFlags    Kind constant (lower byte) OR-ed with flag bits.
     * @param mSourceDictType  Tag string identifying the source dictionary.
     */
    class SuggestedWordInfo(
        val mWord: String,
        val mPrevWordsContext: String,
        val mScore: Int,
        val mKindAndFlags: Int,
        val mSourceDictType: String
    ) {
        val mCodePointCount: Int = StringUtils.codePointCount(mWord)

        private var mDebugString: String = ""

        fun getKind(): Int = mKindAndFlags and KIND_MASK_KIND

        fun isKindOf(kind: Int): Boolean = getKind() == kind

        fun isPossiblyOffensive(): Boolean = (mKindAndFlags and KIND_FLAG_POSSIBLY_OFFENSIVE) != 0

        fun isExactMatch(): Boolean = (mKindAndFlags and KIND_FLAG_EXACT_MATCH) != 0

        fun isAppropriateForAutoCorrection(): Boolean =
            (mKindAndFlags and KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION) != 0

        fun setDebugString(str: String) {
            mDebugString = str
        }

        fun getDebugString(): String = mDebugString

        fun getWord(): String = mWord

        override fun toString(): String =
            if (mDebugString.isEmpty()) mWord else "$mWord ($mDebugString)"

        companion object {
            const val NOT_AN_INDEX = -1
            const val NOT_A_CONFIDENCE = -1
            const val MAX_SCORE = Int.MAX_VALUE

            private const val KIND_MASK_KIND = 0xFF

            const val KIND_TYPED = 0
            const val KIND_CORRECTION = 1
            const val KIND_COMPLETION = 2
            const val KIND_WHITELIST = 3
            const val KIND_BLACKLIST = 4
            const val KIND_HARDCODED = 5
            const val KIND_APP_DEFINED = 6
            const val KIND_SHORTCUT = 7
            const val KIND_PREDICTION = 8
            const val KIND_RESUMED = 9
            const val KIND_OOV_CORRECTION = 10

            const val KIND_FLAG_POSSIBLY_OFFENSIVE = 0x80000000.toInt()
            const val KIND_FLAG_EXACT_MATCH = 0x40000000
            const val KIND_FLAG_EXACT_MATCH_WITH_INTENTIONAL_OMISSION = 0x20000000
            const val KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION = 0x10000000

            /**
             * Removes duplicates and all occurrences of [typedWord] from [candidates].
             * Higher-index duplicates are removed.
             *
             * @return position of the first occurrence of [typedWord] in the original list, or -1.
             */
            fun removeDupsAndTypedWord(
                typedWord: String?,
                candidates: ArrayList<SuggestedWordInfo>
            ): Int {
                if (candidates.isEmpty()) return -1

                var firstOccurrence = -1
                if (!typedWord.isNullOrEmpty()) {
                    firstOccurrence = removeSuggestedWordInfoFromList(typedWord, candidates, -1)
                }
                var i = 0
                while (i < candidates.size) {
                    removeSuggestedWordInfoFromList(candidates[i].mWord, candidates, i)
                    i++
                }
                return firstOccurrence
            }

            private fun removeSuggestedWordInfoFromList(
                word: String,
                candidates: ArrayList<SuggestedWordInfo>,
                startIndexExclusive: Int
            ): Int {
                var firstOccurrence = -1
                var i = startIndexExclusive + 1
                while (i < candidates.size) {
                    if (word == candidates[i].mWord) {
                        if (firstOccurrence == -1) firstOccurrence = i
                        candidates.removeAt(i)
                        i--
                    }
                    i++
                }
                return firstOccurrence
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Companion
    // -----------------------------------------------------------------------------------------

    companion object {
        const val INDEX_OF_TYPED_WORD = 0
        const val INDEX_OF_AUTO_CORRECTION = 1
        const val NOT_A_SEQUENCE_NUMBER = -1

        const val INPUT_STYLE_NONE = 0
        const val INPUT_STYLE_TYPING = 1
        const val INPUT_STYLE_UPDATE_BATCH = 2
        const val INPUT_STYLE_TAIL_BATCH = 3
        const val INPUT_STYLE_APPLICATION_SPECIFIED = 4
        const val INPUT_STYLE_RECORRECTION = 5
        const val INPUT_STYLE_PREDICTION = 6
        const val INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION = 7

        const val MAX_SUGGESTIONS = 18

        @JvmField
        val EMPTY: SuggestedWords = SuggestedWords(
            mSuggestedWordInfoList = ArrayList(0),
            mTypedWordInfo = null,
            mTypedWordValid = false,
            mWillAutoCorrect = false,
            mIsObsoleteSuggestions = false,
            mInputStyle = INPUT_STYLE_NONE,
            mSequenceNumber = NOT_A_SEQUENCE_NUMBER
        )

        @JvmStatic
        fun getEmptyInstance(): SuggestedWords = EMPTY

        /**
         * Builds a merged candidate list from a new typed word plus the previous suggestions,
         * deduplicating as we go.
         */
        @JvmStatic
        fun getTypedWordAndPreviousSuggestions(
            typedWordInfo: SuggestedWordInfo,
            previousSuggestions: SuggestedWords
        ): ArrayList<SuggestedWordInfo> {
            val result = ArrayList<SuggestedWordInfo>()
            val seen = HashSet<String>()
            result.add(typedWordInfo)
            seen.add(typedWordInfo.mWord)
            for (i in 1 until previousSuggestions.size()) {
                val prev = previousSuggestions.getInfo(i)
                if (seen.add(prev.mWord)) {
                    result.add(prev)
                }
            }
            return result
        }
    }
}
