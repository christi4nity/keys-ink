/*
 * Copyright (C) 2008 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin

import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.keysink.inputmethod.latin.common.ComposedData
import com.keysink.inputmethod.latin.common.InputPointers

/**
 * Tracks the word currently being composed, including the key-press coordinates needed by the
 * native proximity-correction engine.
 *
 * Gesture/batch-input mode is explicitly not supported in v0.2; [isBatchMode] is always false.
 *
 * Not thread-safe.
 */
class WordComposer() {

    /** Copy constructor — creates a snapshot for background thread use. */
    constructor(other: WordComposer) : this() {
        System.arraycopy(other.codePoints, 0, codePoints, 0, other.mCodePointSize.coerceAtMost(MAX_WORD_LENGTH))
        mCodePointSize = other.mCodePointSize
        mTypedWordCache = other.mTypedWordCache
        mIsOnlyFirstCharCapitalized = other.mIsOnlyFirstCharCapitalized
        mCapsCount = other.mCapsCount
        mDigitsCount = other.mDigitsCount
        mCapitalizedMode = other.mCapitalizedMode
        mIsResumed = other.mIsResumed
    }

    // --------------------------------------------------------------------------------------------
    // State
    // --------------------------------------------------------------------------------------------

    /** Code points in the composing word, in order. Capped at [MAX_WORD_LENGTH]. */
    private val codePoints = IntArray(MAX_WORD_LENGTH)

    /** Touch coordinates parallel to [codePoints], stored in [mInputPointers]. */
    private val mInputPointers = InputPointers(MAX_WORD_LENGTH)

    /** Total number of code points entered so far (may exceed [MAX_WORD_LENGTH]). */
    private var mCodePointSize = 0

    /** Cached String form of the composing word. Rebuilt on every structural change. */
    private var mTypedWordCache: String = ""

    /** Whether the first character is uppercased (and no subsequent char is). */
    private var mIsOnlyFirstCharCapitalized = false

    /** Count of uppercase code points in the composing word. */
    private var mCapsCount = 0

    /** Count of digit code points in the composing word. */
    private var mDigitsCount = 0

    /** Caps mode in effect at composing start (CAPS_MODE_* constant). */
    private var mCapitalizedMode = CAPS_MODE_OFF

    /** Auto-correction chosen by the suggestion engine for this composing word. */
    private var mAutoCorrection: SuggestedWordInfo? = null

    /** True when the session was resumed on an already-committed word. */
    private var mIsResumed = false

    // --------------------------------------------------------------------------------------------
    // Public read-only properties
    // --------------------------------------------------------------------------------------------

    /** The word as typed by the user. Never null. */
    val typedWord: String get() = mTypedWordCache

    /** True if there is at least one code point in the composing word. */
    val isComposingWord: Boolean get() = mCodePointSize > 0

    /** Always false — gesture input not supported in v0.2. */
    val isBatchMode: Boolean = false

    // --------------------------------------------------------------------------------------------
    // Mutation API
    // --------------------------------------------------------------------------------------------

    /**
     * Appends [codePoint] to the composing word, recording touch coordinates ([keyX], [keyY])
     * for proximity correction.
     */
    fun addCodePoint(codePoint: Int, keyX: Int, keyY: Int) {
        val index = mCodePointSize
        if (index < MAX_WORD_LENGTH) {
            codePoints[index] = codePoint
            // pointer ID and timestamp are not used by v0.2 suggestion engine
            mInputPointers.addPointerAt(index, keyX, keyY, 0, 0)
        }
        mCodePointSize++
        updateCapsTracking(codePoint, index)
        mAutoCorrection = null
        rebuildTypedWordCache()
    }

    /**
     * Removes the last code point from the composing word.
     */
    fun deleteLast() {
        if (mCodePointSize <= 0) return
        mCodePointSize--
        if (mCodePointSize == 0) {
            mIsOnlyFirstCharCapitalized = false
            mCapsCount = 0
            mDigitsCount = 0
        }
        mAutoCorrection = null
        rebuildTypedWordCache()
    }

    /**
     * Resets the composer to an empty state.
     */
    fun reset() {
        mCodePointSize = 0
        mInputPointers.reset()
        mTypedWordCache = ""
        mIsOnlyFirstCharCapitalized = false
        mCapsCount = 0
        mDigitsCount = 0
        mCapitalizedMode = CAPS_MODE_OFF
        mAutoCorrection = null
        mIsResumed = false
    }

    /**
     * Sets the composing word from an existing array of code points and their coordinates.
     * Used when resuming suggestion on a previously committed word.
     *
     * @param inputCodePoints The code points to restore.
     * @param coordinates     Flat [x, y, x, y, …] coordinate array in CoordinateUtils format.
     */
    fun setComposingWord(inputCodePoints: IntArray, coordinates: IntArray) {
        reset()
        val len = inputCodePoints.size
        for (i in 0 until len) {
            val cp = inputCodePoints[i]
            val x = if (coordinates.size > i * 2) coordinates[i * 2] else NOT_A_COORDINATE
            val y = if (coordinates.size > i * 2 + 1) coordinates[i * 2 + 1] else NOT_A_COORDINATE
            addCodePoint(cp, x, y)
        }
        mIsResumed = true
    }

    // --------------------------------------------------------------------------------------------
    // Snapshot for JNI layer
    // --------------------------------------------------------------------------------------------

    /**
     * Returns an immutable snapshot of the current composition state for the native suggestion
     * engine. Callers should not hold onto this across mutations.
     */
    fun getComposedDataSnapshot(): ComposedData {
        val snapshot = InputPointers(mCodePointSize.coerceAtLeast(1))
        snapshot.copy(mInputPointers)
        return ComposedData(snapshot, isBatchMode, mTypedWordCache)
    }

    // --------------------------------------------------------------------------------------------
    // Caps / capitalization helpers
    // --------------------------------------------------------------------------------------------

    fun setCapitalizedModeAtStartComposingTime(mode: Int) {
        mCapitalizedMode = mode
    }

    fun adviseCapitalizedModeBeforeFetchingSuggestions(mode: Int) {
        if (!isComposingWord) mCapitalizedMode = mode
    }

    fun isOrWillBeOnlyFirstCharCapitalized(): Boolean =
        if (isComposingWord) mIsOnlyFirstCharCapitalized else mCapitalizedMode != CAPS_MODE_OFF

    fun isAllUpperCase(): Boolean {
        if (mCodePointSize <= 1) {
            return mCapitalizedMode == CAPS_MODE_AUTO_SHIFT_LOCKED ||
                mCapitalizedMode == CAPS_MODE_MANUAL_SHIFT_LOCKED
        }
        return mCapsCount == mCodePointSize
    }

    fun isMostlyCaps(): Boolean = mCapsCount > 1

    fun hasDigits(): Boolean = mDigitsCount > 0

    fun wasAutoCapitalized(): Boolean =
        mCapitalizedMode == CAPS_MODE_AUTO_SHIFTED ||
        mCapitalizedMode == CAPS_MODE_AUTO_SHIFT_LOCKED

    // --------------------------------------------------------------------------------------------
    // Auto-correction bookkeeping
    // --------------------------------------------------------------------------------------------

    fun setAutoCorrection(autoCorrection: SuggestedWordInfo?) {
        mAutoCorrection = autoCorrection
    }

    fun getAutoCorrectionOrNull(): SuggestedWordInfo? = mAutoCorrection

    fun isResumed(): Boolean = mIsResumed

    fun size(): Int = mCodePointSize

    fun isSingleLetter(): Boolean = mCodePointSize == 1

    fun getInputPointers(): InputPointers = mInputPointers

    // --------------------------------------------------------------------------------------------
    // Private helpers
    // --------------------------------------------------------------------------------------------

    private fun updateCapsTracking(codePoint: Int, positionIndex: Int) {
        if (positionIndex == 0) {
            mIsOnlyFirstCharCapitalized = Character.isUpperCase(codePoint)
        } else {
            mIsOnlyFirstCharCapitalized =
                mIsOnlyFirstCharCapitalized && !Character.isUpperCase(codePoint)
        }
        if (Character.isUpperCase(codePoint)) mCapsCount++
        if (Character.isDigit(codePoint)) mDigitsCount++
    }

    private fun rebuildTypedWordCache() {
        val effectiveSize = minOf(mCodePointSize, MAX_WORD_LENGTH)
        mTypedWordCache = String(codePoints, 0, effectiveSize)
    }

    // --------------------------------------------------------------------------------------------
    // Constants
    // --------------------------------------------------------------------------------------------

    companion object {
        // Must match DICTIONARY_MAX_WORD_LENGTH in native defines.h
        const val MAX_WORD_LENGTH = 48

        const val CAPS_MODE_OFF = 0
        const val CAPS_MODE_MANUAL_SHIFTED = 0x1
        const val CAPS_MODE_MANUAL_SHIFT_LOCKED = 0x3
        const val CAPS_MODE_AUTO_SHIFTED = 0x5
        const val CAPS_MODE_AUTO_SHIFT_LOCKED = 0x7

        /** Sentinel value for unknown touch coordinates. */
        /** Must match NOT_A_COORDINATE in defines.h */
        const val NOT_A_COORDINATE = -1
    }
}
