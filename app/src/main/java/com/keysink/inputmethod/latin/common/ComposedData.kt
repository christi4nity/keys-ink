/*
 * Copyright (C) 2014 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin.common

/**
 * An immutable snapshot of word composition data passed to the native suggestion engine.
 *
 * @param mInputPointers Touch coordinates for the composing word.
 * @param mIsBatchMode   True if the input was a gesture/batch stroke (always false for v0.2).
 * @param mTypedWord     The word as typed by the user.
 */
class ComposedData(
    @JvmField val mInputPointers: InputPointers,
    @JvmField val mIsBatchMode: Boolean,
    @JvmField val mTypedWord: String
) {
    /**
     * Copies the code points of the typed word (excluding any trailing single quotes) into
     * [destination].
     *
     * @return the number of code points copied, or -1 if [destination] is too small.
     */
    fun copyCodePointsExceptTrailingSingleQuotesAndReturnCodePointCount(
        destination: IntArray
    ): Int {
        val trailingQuotes = countTrailingSingleQuotes(mTypedWord)
        val lastIndex = mTypedWord.length - trailingQuotes
        if (lastIndex <= 0) return 0

        val codePointSize = Character.codePointCount(mTypedWord, 0, lastIndex)
        if (codePointSize > destination.size) return -1

        return StringUtils.copyCodePointsAndReturnCodePointCount(
            destination, mTypedWord, 0, lastIndex, /* normalizeCase= */ true
        )
    }

    private fun countTrailingSingleQuotes(word: String): Int {
        var count = 0
        var i = word.length - 1
        while (i >= 0 && word[i] == '\'') {
            count++
            i--
        }
        return count
    }
}
