/*
 * Copyright (C) 2012 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin.dictionary

import java.util.Locale

/**
 * JNI wrapper for the native DicTraverseSession. Each session holds state used during
 * a single suggestion traversal pass.
 *
 * The native methods are registered via
 * [com/keysink/inputmethod/latin/dictionary/DicTraverseSession] in the C++ JNI layer.
 */
class DicTraverseSession private constructor(
    private val mNativeHandle: Long
) {

    /**
     * Initializes the traversal session for a specific dictionary.
     *
     * @param dictHandle  Native pointer to the Dictionary object.
     * @param previousWord  Previous word code points (for context), or null.
     * @param previousWordLength  Length of [previousWord] array.
     */
    fun initSession(dictHandle: Long, previousWord: IntArray?, previousWordLength: Int) {
        initDicTraverseSessionNative(mNativeHandle, dictHandle, previousWord, previousWordLength)
    }

    /** Returns the native pointer for passing to getSuggestionsNative. */
    fun getHandle(): Long = mNativeHandle

    /** Releases the native traversal session. */
    fun release() {
        releaseDicTraverseSessionNative(mNativeHandle)
    }

    // JNI signatures must match sMethods in
    // com_android_inputmethod_latin_DicTraverseSession.cpp
    //
    // setDicTraverseSessionNative: (Ljava/lang/String;J)J
    // initDicTraverseSessionNative: (JJ[II)V
    // releaseDicTraverseSessionNative: (J)V

    companion object {
        init {
            System.loadLibrary("jni_latinime")
        }

        /**
         * Creates a new DicTraverseSession for the given locale and dictionary size.
         */
        fun create(locale: Locale, dictSize: Long): DicTraverseSession {
            val handle = setDicTraverseSessionNative(locale.toString(), dictSize)
            return DicTraverseSession(handle)
        }

        @JvmStatic
        private external fun setDicTraverseSessionNative(locale: String, dictSize: Long): Long

        @JvmStatic
        private external fun initDicTraverseSessionNative(
            traverseSession: Long,
            dictionary: Long,
            previousWord: IntArray?,
            previousWordLength: Int
        )

        @JvmStatic
        private external fun releaseDicTraverseSessionNative(traverseSession: Long)
    }
}
