/*
 * Copyright (C) 2013 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin.dictionary

import android.content.Context
import android.util.Log
import com.keysink.inputmethod.latin.NgramContext
import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.keysink.inputmethod.latin.common.ComposedData
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * v0.2 implementation of [DictionaryFacilitator] that manages a single main dictionary.
 *
 * Dictionary loading happens on a background thread; [hasAtLeastOneInitializedMainDictionary]
 * returns false until loading completes.
 */
class DictionaryFacilitatorImpl : DictionaryFacilitator {

    private val mMainDictionary = AtomicReference<BinaryDictionary?>(null)

    @Volatile
    private var mIsLoading = false

    override fun getSuggestionResults(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        sessionId: Int
    ): ArrayList<SuggestedWordInfo> {
        val results = ArrayList<SuggestedWordInfo>()
        val dict = mMainDictionary.get() ?: return results
        dict.getSuggestions(
            composedData,
            ngramContext,
            proximityInfoHandle,
            null,
            sessionId,
            DEFAULT_WEIGHT_FOR_LOCALE,
            results
        )
        return results
    }

    override fun isValidWord(word: String): Boolean {
        return mMainDictionary.get()?.isValidWord(word) ?: false
    }

    override fun hasAtLeastOneInitializedMainDictionary(): Boolean {
        val dict = mMainDictionary.get()
        return dict != null && dict.isValidDictionary
    }

    override fun resetDictionaries(context: Context, locale: Locale) {
        if (mIsLoading) return
        mIsLoading = true

        // Close existing dictionary first
        val oldDict = mMainDictionary.getAndSet(null)
        oldDict?.close()

        // Load new dictionary on a background thread
        Thread({
            try {
                val newDict = DictionaryFactory.createMainDictionary(context, locale)
                mMainDictionary.set(newDict)
                if (newDict != null) {
                    Log.i(TAG, "Main dictionary loaded for locale=${locale.language}")
                } else {
                    Log.w(TAG, "No dictionary available for locale=${locale.language}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dictionary for locale=${locale.language}", e)
            } finally {
                mIsLoading = false
            }
        }, "DictLoad-${locale.language}").start()
    }

    override fun closeDictionaries() {
        val dict = mMainDictionary.getAndSet(null)
        dict?.close()
    }

    companion object {
        private const val TAG = "DictFacilitatorImpl"
        private const val DEFAULT_WEIGHT_FOR_LOCALE = 1.0f
    }
}
