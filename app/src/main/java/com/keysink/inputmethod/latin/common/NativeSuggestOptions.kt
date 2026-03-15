/*
 * Copyright (C) 2013 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin.common

/**
 * An IntArray wrapper that packs option flags for the native suggestion JNI layer.
 *
 * The array indices must stay in sync with suggest_options.h in the native code.
 * Order: IS_GESTURE, USE_FULL_EDIT_DISTANCE, BLOCK_OFFENSIVE_WORDS,
 *        SPACE_AWARE_GESTURE_ENABLED, WEIGHT_FOR_LOCALE_IN_THOUSANDS
 */
class NativeSuggestOptions {

    private val mOptions = IntArray(OPTIONS_SIZE)

    /** Always false for v0.2 (no gesture input). */
    fun setIsGesture(value: Boolean) = setBooleanOption(IS_GESTURE, value)

    /** Always false for v0.2 (no gesture input). */
    fun setIsSpaceAwareGesture(value: Boolean) = setBooleanOption(SPACE_AWARE_GESTURE_ENABLED, value)

    fun setUseFullEditDistance(value: Boolean) = setBooleanOption(USE_FULL_EDIT_DISTANCE, value)

    fun setBlockOffensiveWords(value: Boolean) = setBooleanOption(BLOCK_OFFENSIVE_WORDS, value)

    /**
     * Sets the weight for locale as a fixed-point value in thousandths (e.g. 1.0f → 1000).
     * Decoded on the native side by SuggestOptions#weightForLocale().
     */
    fun setWeightForLocale(value: Float) {
        setIntegerOption(WEIGHT_FOR_LOCALE_IN_THOUSANDS, (value * 1000).toInt())
    }

    /** Returns the raw options array for passing to JNI. */
    fun getOptions(): IntArray = mOptions

    private fun setBooleanOption(key: Int, value: Boolean) {
        mOptions[key] = if (value) 1 else 0
    }

    private fun setIntegerOption(key: Int, value: Int) {
        mOptions[key] = value
    }

    companion object {
        // These indices MUST match suggest_options.h
        private const val IS_GESTURE = 0
        private const val USE_FULL_EDIT_DISTANCE = 1
        private const val BLOCK_OFFENSIVE_WORDS = 2
        private const val SPACE_AWARE_GESTURE_ENABLED = 3
        private const val WEIGHT_FOR_LOCALE_IN_THOUSANDS = 4
        private const val OPTIONS_SIZE = 5
    }
}
