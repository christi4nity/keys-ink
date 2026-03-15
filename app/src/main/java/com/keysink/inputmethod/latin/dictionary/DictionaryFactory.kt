/*
 * Copyright (C) 2013 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin.dictionary

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Creates dictionary instances from bundled assets.
 *
 * The native BinaryDictionary requires a file path (it cannot read from an Android AssetInputStream
 * directly), so we copy the `.dict` asset to the app's cache directory on first use.
 */
object DictionaryFactory {

    private const val TAG = "DictionaryFactory"
    private const val DICT_ASSET_DIR = "dicts"
    private const val DICT_FILE_PREFIX = "main_"
    private const val DICT_FILE_SUFFIX = ".dict"

    /**
     * Creates the main dictionary for the given [locale].
     *
     * @return a [BinaryDictionary] instance, or null if the asset does not exist or loading fails.
     */
    fun createMainDictionary(context: Context, locale: Locale): BinaryDictionary? {
        val assetName = "$DICT_ASSET_DIR/${DICT_FILE_PREFIX}${locale.language}$DICT_FILE_SUFFIX"
        val cacheFile = File(context.cacheDir, "${DICT_FILE_PREFIX}${locale.language}$DICT_FILE_SUFFIX")

        try {
            if (!cacheFile.exists()) {
                copyAssetToCache(context, assetName, cacheFile)
            }

            val dict = BinaryDictionary(
                filename = cacheFile.absolutePath,
                offset = 0L,
                length = cacheFile.length(),
                isUpdatable = false,
                dictType = Dictionary.TYPE_MAIN,
                mLocale = locale
            )

            if (!dict.isValidDictionary) {
                Log.w(TAG, "Failed to open dictionary: $assetName")
                dict.close()
                return null
            }

            return dict
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create main dictionary for locale=${locale.language}", e)
            return null
        }
    }

    private fun copyAssetToCache(context: Context, assetName: String, cacheFile: File) {
        context.assets.open(assetName).use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
