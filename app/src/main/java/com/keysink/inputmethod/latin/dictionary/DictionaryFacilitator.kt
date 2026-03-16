/*
 * Copyright (C) 2013 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin.dictionary

import android.content.Context
import com.keysink.inputmethod.latin.NgramContext
import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.keysink.inputmethod.latin.common.ComposedData
import java.util.Locale

/**
 * Interface for managing one or more dictionaries and routing suggestion queries to them.
 */
interface DictionaryFacilitator {

    /**
     * Collects suggestions from all active dictionaries for the given composition.
     */
    fun getSuggestionResults(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        sessionId: Int
    ): ArrayList<SuggestedWordInfo>

    /**
     * Returns true if [word] is valid in any active dictionary.
     */
    fun isValidWord(word: String): Boolean

    /**
     * Returns true if at least one main dictionary has been loaded.
     */
    fun hasAtLeastOneInitializedMainDictionary(): Boolean

    /**
     * Loads (or reloads) dictionaries for the given [locale].
     * Dictionary loading may happen asynchronously on a background thread.
     */
    fun resetDictionaries(context: Context, locale: Locale)

    /**
     * Closes all dictionaries and releases native resources.
     */
    fun closeDictionaries()
}
