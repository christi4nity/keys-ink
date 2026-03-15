/*
 * Copyright (C) 2014 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin

import android.text.TextUtils
import com.keysink.inputmethod.latin.common.StringUtils

/**
 * Stores n-gram context (previous words) for prediction and suggestion scoring.
 *
 * The [mPrevWordsInfo] array holds the most recent words at lower indices
 * (index 0 = immediately preceding word).
 */
class NgramContext private constructor(
    private val maxPrevWordCount: Int,
    private vararg val mPrevWordsInfo: WordInfo
) {
    private val mPrevWordsCount: Int = mPrevWordsInfo.size

    // Convenience constructor using default max count
    constructor(vararg prevWordsInfo: WordInfo) :
        this(MAX_PREV_WORD_COUNT_FOR_N_GRAM, *prevWordsInfo)

    // -----------------------------------------------------------------------------------------
    // WordInfo
    // -----------------------------------------------------------------------------------------

    /**
     * Represents a single previous word entry.
     *
     * @param mWord                 The word string (empty when [mIsBeginningOfSentence] is true).
     * @param mIsBeginningOfSentence Whether this position marks the start of a sentence.
     */
    class WordInfo private constructor(
        val mWord: CharSequence?,
        val mIsBeginningOfSentence: Boolean
    ) {
        constructor(word: CharSequence?) : this(word, false)

        /** Returns true if this entry carries a valid (non-null) word. */
        fun isValid(): Boolean = mWord != null

        override fun hashCode(): Int = arrayOf(mWord, mIsBeginningOfSentence).contentHashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WordInfo) return false
            if (mWord == null || other.mWord == null) {
                return mWord === other.mWord && mIsBeginningOfSentence == other.mIsBeginningOfSentence
            }
            return TextUtils.equals(mWord, other.mWord) &&
                mIsBeginningOfSentence == other.mIsBeginningOfSentence
        }

        companion object {
            /** Sentinel: no context available for this position. */
            @JvmField
            val EMPTY_WORD_INFO = WordInfo(null, false)

            /** Sentinel: context is beginning-of-sentence. */
            @JvmField
            val BEGINNING_OF_SENTENCE_WORD_INFO = WordInfo("", true)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------------------------

    fun isValid(): Boolean = mPrevWordsCount > 0 && mPrevWordsInfo[0].isValid()

    fun isBeginningOfSentenceContext(): Boolean =
        mPrevWordsCount > 0 && mPrevWordsInfo[0].mIsBeginningOfSentence

    /** Returns the nth previous word (1-indexed), or null if out of range. */
    fun getNthPrevWord(n: Int): CharSequence? {
        if (n <= 0 || n > mPrevWordsCount) return null
        return mPrevWordsInfo[n - 1].mWord
    }

    /** Returns true if the nth previous word (1-indexed) is a beginning-of-sentence marker. */
    fun isNthPrevWordBeginningOfSentence(n: Int): Boolean {
        if (n <= 0 || n > mPrevWordsCount) return false
        return mPrevWordsInfo[n - 1].mIsBeginningOfSentence
    }

    fun getPrevWordCount(): Int = mPrevWordsCount

    /**
     * Creates the next context by prepending [wordInfo] to the current prev-words list,
     * dropping the oldest entry if we exceed [maxPrevWordCount].
     */
    fun getNextNgramContext(wordInfo: WordInfo): NgramContext {
        val nextCount = minOf(maxPrevWordCount, mPrevWordsCount + 1)
        val next = Array(nextCount) { i ->
            if (i == 0) wordInfo else mPrevWordsInfo[i - 1]
        }
        return NgramContext(maxPrevWordCount, *next)
    }

    /**
     * Returns the previous-words context as a space-separated string.
     * Iterates from oldest to newest, inserting [BEGINNING_OF_SENTENCE_TAG] where appropriate.
     */
    fun extractPrevWordsContext(): String {
        val terms = mutableListOf<String>()
        for (i in mPrevWordsInfo.indices.reversed()) {
            val info = mPrevWordsInfo[i]
            if (info.isValid()) {
                if (info.mIsBeginningOfSentence) {
                    terms.add(BEGINNING_OF_SENTENCE_TAG)
                } else {
                    val term = info.mWord.toString()
                    if (term.isNotEmpty()) terms.add(term)
                }
            }
        }
        return terms.joinToString(CONTEXT_SEPARATOR)
    }

    /**
     * Returns the previous-words context as a String array (oldest to newest).
     */
    fun extractPrevWordsContextArray(): Array<String> {
        val terms = mutableListOf<String>()
        for (i in mPrevWordsInfo.indices.reversed()) {
            val info = mPrevWordsInfo[i]
            if (info.isValid()) {
                if (info.mIsBeginningOfSentence) {
                    terms.add(BEGINNING_OF_SENTENCE_TAG)
                } else {
                    val term = info.mWord.toString()
                    if (term.isNotEmpty()) terms.add(term)
                }
            }
        }
        return terms.toTypedArray()
    }

    /**
     * Outputs the n-gram context into parallel arrays for native JNI consumption.
     */
    fun outputToArray(
        codePointArrays: Array<IntArray>,
        isBeginningOfSentenceArray: BooleanArray
    ) {
        for (i in 0 until mPrevWordsCount) {
            val info = mPrevWordsInfo[i]
            if (info.isValid()) {
                codePointArrays[i] = StringUtils.toCodePointArray(info.mWord!!)
                isBeginningOfSentenceArray[i] = info.mIsBeginningOfSentence
            } else {
                codePointArrays[i] = IntArray(0)
                isBeginningOfSentenceArray[i] = false
            }
        }
    }

    override fun hashCode(): Int {
        var hash = 0
        for (info in mPrevWordsInfo) {
            if (WordInfo.EMPTY_WORD_INFO == info) break
            hash = hash xor info.hashCode()
        }
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NgramContext) return false
        val minLen = minOf(mPrevWordsCount, other.mPrevWordsCount)
        for (i in 0 until minLen) {
            if (mPrevWordsInfo[i] != other.mPrevWordsInfo[i]) return false
        }
        val (longer, longerCount) = if (mPrevWordsCount > other.mPrevWordsCount)
            Pair(mPrevWordsInfo, mPrevWordsCount)
        else
            Pair(other.mPrevWordsInfo, other.mPrevWordsCount)
        for (i in minLen until longerCount) {
            if (WordInfo.EMPTY_WORD_INFO != longer[i]) return false
        }
        return true
    }

    override fun toString(): String = buildString {
        for (i in 0 until mPrevWordsCount) {
            val info = mPrevWordsInfo[i]
            append("PrevWord[$i]: ")
            when {
                !info.isValid() -> append("Empty. ")
                info.mIsBeginningOfSentence -> append("<BOS>. ")
                else -> append("${info.mWord}, isBeginningOfSentence=${info.mIsBeginningOfSentence}. ")
            }
        }
    }

    companion object {
        const val BEGINNING_OF_SENTENCE_TAG = "<S>"
        const val CONTEXT_SEPARATOR = " "

        // Must match DecoderSpecificConstants / native defines.h
        private const val MAX_PREV_WORD_COUNT_FOR_N_GRAM = 3

        @JvmField
        val EMPTY_PREV_WORDS_INFO = NgramContext(WordInfo.EMPTY_WORD_INFO)

        @JvmField
        val BEGINNING_OF_SENTENCE = NgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO)

        @JvmStatic
        fun getEmptyPrevWordsContext(maxPrevWordCount: Int): NgramContext =
            NgramContext(maxPrevWordCount, WordInfo.EMPTY_WORD_INFO)
    }
}
