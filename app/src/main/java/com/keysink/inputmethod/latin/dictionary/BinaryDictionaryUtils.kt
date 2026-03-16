/*
 * Copyright (C) 2014 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin.dictionary

/**
 * JNI wrapper for native dictionary utility functions.
 *
 * The native methods are registered via
 * [com/keysink/inputmethod/latin/dictionary/BinaryDictionaryUtils] in the C++ JNI layer.
 *
 * JNI signatures (from sMethods in com_android_inputmethod_latin_BinaryDictionaryUtils.cpp):
 *   createEmptyDictFileNative: (Ljava/lang/String;JLjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)Z
 *   calcNormalizedScoreNative: ([I[II)F
 *   setCurrentTimeForTestNative: (I)I
 */
object BinaryDictionaryUtils {

    init {
        System.loadLibrary("jni_latinime")
    }

    /**
     * Creates an empty dictionary file at [filePath].
     */
    fun createEmptyDictFile(
        filePath: String,
        dictVersion: Long,
        locale: String,
        attributeKeys: Array<String>,
        attributeValues: Array<String>
    ): Boolean {
        return createEmptyDictFileNative(
            filePath, dictVersion, locale, attributeKeys, attributeValues
        )
    }

    /**
     * Calculates a normalized edit-distance score between two code point arrays.
     */
    fun calcNormalizedScore(before: IntArray, after: IntArray, score: Int): Float {
        return calcNormalizedScoreNative(before, after, score)
    }

    @JvmStatic
    private external fun createEmptyDictFileNative(
        filePath: String,
        dictVersion: Long,
        locale: String,
        attributeKeys: Array<String>,
        attributeValues: Array<String>
    ): Boolean

    @JvmStatic
    private external fun calcNormalizedScoreNative(
        before: IntArray,
        after: IntArray,
        score: Int
    ): Float

    @JvmStatic
    private external fun setCurrentTimeForTestNative(currentTime: Int): Int
}
