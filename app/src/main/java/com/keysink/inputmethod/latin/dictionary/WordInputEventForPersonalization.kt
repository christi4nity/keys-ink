/*
 * Copyright (C) 2014 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin.dictionary

/**
 * Data class referenced by the JNI layer for personalization input events.
 * The field names and types must match what the native code expects via reflection
 * (see updateEntriesForInputEventsNative in BinaryDictionary JNI).
 */
class WordInputEventForPersonalization(
    @JvmField val mTargetWord: IntArray,
    @JvmField val mPrevWordsCount: Int,
    @JvmField val mPrevWordArray: Array<IntArray>,
    @JvmField val mIsPrevWordBeginningOfSentenceArray: BooleanArray,
    @JvmField val mIsValid: Boolean,
    @JvmField val mTimestamp: Int
)
