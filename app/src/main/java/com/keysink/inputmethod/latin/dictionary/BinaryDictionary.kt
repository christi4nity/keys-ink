/*
 * Copyright (C) 2008 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin.dictionary

import android.util.Log
import com.keysink.inputmethod.latin.NgramContext
import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.keysink.inputmethod.latin.common.ComposedData
import com.keysink.inputmethod.latin.common.NativeSuggestOptions
import com.keysink.inputmethod.latin.common.StringUtils
import java.util.Locale

/**
 * JNI wrapper around the native binary dictionary engine.
 *
 * All native method signatures MUST match the JNINativeMethod entries in
 * com_android_inputmethod_latin_BinaryDictionary.cpp (registered under
 * [com/keysink/inputmethod/latin/dictionary/BinaryDictionary]).
 */
class BinaryDictionary(
    filename: String,
    offset: Long,
    length: Long,
    isUpdatable: Boolean,
    dictType: String,
    private val mLocale: Locale
) : Dictionary(dictType) {

    private var mNativeDict: Long = openNative(filename, offset, length, isUpdatable)
    private var mDicTraverseSession: DicTraverseSession? = null

    val isValidDictionary: Boolean get() = mNativeDict != 0L

    // ------------------------------------------------------------------
    // Dictionary overrides
    // ------------------------------------------------------------------

    override fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: Any?,
        sessionId: Int,
        weightForLocale: Float,
        outResults: ArrayList<SuggestedWordInfo>
    ): ArrayList<SuggestedWordInfo> {
        if (!isValidDictionary) return outResults

        val inputPointers = composedData.mInputPointers
        val inputSize = if (composedData.mIsBatchMode) {
            inputPointers.getPointerSize()
        } else {
            composedData.mTypedWord.length
        }

        val xCoordinates = inputPointers.getXCoordinates()
        val yCoordinates = inputPointers.getYCoordinates()
        val times = inputPointers.getTimes()
        val pointerIds = inputPointers.getPointerIds()

        // Build code point array for the typed word
        val inputCodePoints = IntArray(MAX_WORD_LENGTH)
        val codePointCount = composedData.copyCodePointsExceptTrailingSingleQuotesAndReturnCodePointCount(
            inputCodePoints
        )
        val effectiveInputSize = if (codePointCount < 0) 0 else inputSize

        // Build suggest options
        val suggestOptions = NativeSuggestOptions()
        suggestOptions.setIsGesture(composedData.mIsBatchMode)
        suggestOptions.setUseFullEditDistance(false)
        suggestOptions.setBlockOffensiveWords(false)
        suggestOptions.setWeightForLocale(weightForLocale)

        // Build previous-word arrays for n-gram context
        val prevWordCount = ngramContext.getPrevWordCount()
        val prevWordCodePointArrays = Array(prevWordCount) { IntArray(0) }
        val isBeginningOfSentenceArray = BooleanArray(prevWordCount)
        ngramContext.outputToArray(prevWordCodePointArrays, isBeginningOfSentenceArray)

        // Output arrays
        val outSuggestionCount = IntArray(1)
        val outCodePoints = IntArray(MAX_WORD_LENGTH * MAX_RESULTS)
        val outScores = IntArray(MAX_RESULTS)
        val outSpaceIndices = IntArray(MAX_RESULTS)
        val outTypes = IntArray(MAX_RESULTS)
        val outAutoCommitFirstWordConfidence = IntArray(1)
        val inOutWeightOfLangModelVsSpatialModel = floatArrayOf(weightForLocale)

        // Get or create traversal session
        val traverseSession = getOrCreateTraverseSession()

        getSuggestionsNative(
            mNativeDict,
            proximityInfoHandle,
            traverseSession.getHandle(),
            xCoordinates,
            yCoordinates,
            times,
            pointerIds,
            inputCodePoints,
            effectiveInputSize,
            suggestOptions.getOptions(),
            prevWordCodePointArrays,
            isBeginningOfSentenceArray,
            prevWordCount,
            outSuggestionCount,
            outCodePoints,
            outScores,
            outSpaceIndices,
            outTypes,
            outAutoCommitFirstWordConfidence,
            inOutWeightOfLangModelVsSpatialModel
        )

        val count = outSuggestionCount[0]
        for (i in 0 until count) {
            val wordCodePoints = IntArray(MAX_WORD_LENGTH)
            System.arraycopy(
                outCodePoints, i * MAX_WORD_LENGTH,
                wordCodePoints, 0,
                MAX_WORD_LENGTH
            )
            // Find the actual length (null-terminated)
            var wordLen = 0
            while (wordLen < MAX_WORD_LENGTH && wordCodePoints[wordLen] != 0) {
                wordLen++
            }
            if (wordLen == 0) continue
            val word = String(wordCodePoints, 0, wordLen)
            val score = outScores[i]
            val kindAndFlags = outTypes[i]

            val prevWordsContext = ngramContext.extractPrevWordsContext()
            outResults.add(
                SuggestedWordInfo(
                    word,
                    prevWordsContext,
                    score,
                    kindAndFlags,
                    mDictType
                )
            )
        }
        return outResults
    }

    override fun isValidWord(word: String): Boolean {
        if (!isValidDictionary) return false
        val codePoints = StringUtils.toCodePointArray(word)
        return getProbabilityNative(mNativeDict, codePoints) != NOT_A_PROBABILITY
    }

    override fun close() {
        mDicTraverseSession?.release()
        mDicTraverseSession = null
        if (mNativeDict != 0L) {
            closeNative(mNativeDict)
            mNativeDict = 0L
        }
    }

    // ------------------------------------------------------------------
    // Additional public API
    // ------------------------------------------------------------------

    fun flush(filePath: String): Boolean {
        if (!isValidDictionary) return false
        return flushNative(mNativeDict, filePath)
    }

    fun needsToRunGC(mindsBlockByGC: Boolean): Boolean {
        if (!isValidDictionary) return false
        return needsToRunGCNative(mNativeDict, mindsBlockByGC)
    }

    fun isCorrupted(): Boolean {
        if (!isValidDictionary) return false
        return isCorruptedNative(mNativeDict)
    }

    fun getFormatVersion(): Int {
        if (!isValidDictionary) return 0
        return getFormatVersionNative(mNativeDict)
    }

    fun getProperty(query: String): String {
        if (!isValidDictionary) return ""
        return getPropertyNative(mNativeDict, query)
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private fun getOrCreateTraverseSession(): DicTraverseSession {
        val existing = mDicTraverseSession
        if (existing != null) return existing
        val session = DicTraverseSession.create(mLocale, 0L)
        mDicTraverseSession = session
        return session
    }

    protected fun finalize() {
        try {
            close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close BinaryDictionary in finalizer", e)
        }
    }

    // ------------------------------------------------------------------
    // Native methods
    // ------------------------------------------------------------------
    // JNI descriptor comments show the exact signatures registered in C++.

    companion object {
        private const val TAG = "BinaryDictionary"

        /** Must match MAX_WORD_LENGTH in defines.h */
        const val MAX_WORD_LENGTH = 48

        /** Must match MAX_RESULTS in defines.h */
        const val MAX_RESULTS = 18

        init {
            System.loadLibrary("jni_latinime")
        }

        // openNative: (Ljava/lang/String;JJZ)J
        @JvmStatic
        private external fun openNative(
            sourceDir: String,
            dictOffset: Long,
            dictSize: Long,
            isUpdatable: Boolean
        ): Long

        // createOnMemoryNative: (JLjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)J
        @JvmStatic
        private external fun createOnMemoryNative(
            formatVersion: Long,
            locale: String,
            attributeKeys: Array<String>,
            attributeValues: Array<String>
        ): Long

        // closeNative: (J)V
        @JvmStatic
        private external fun closeNative(dict: Long)

        // getFormatVersionNative: (J)I
        @JvmStatic
        private external fun getFormatVersionNative(dict: Long): Int

        // getHeaderInfoNative: (J[I[ILjava/util/ArrayList;Ljava/util/ArrayList;)V
        @JvmStatic
        private external fun getHeaderInfoNative(
            dict: Long,
            outHeaderSize: IntArray,
            outFormatVersion: IntArray,
            outAttributeKeys: ArrayList<IntArray>,
            outAttributeValues: ArrayList<IntArray>
        )

        // flushNative: (JLjava/lang/String;)Z
        @JvmStatic
        private external fun flushNative(dict: Long, filePath: String): Boolean

        // needsToRunGCNative: (JZ)Z
        @JvmStatic
        private external fun needsToRunGCNative(dict: Long, mindsBlockByGC: Boolean): Boolean

        // flushWithGCNative: (JLjava/lang/String;)Z
        @JvmStatic
        private external fun flushWithGCNative(dict: Long, filePath: String): Boolean

        // getSuggestionsNative: (JJJ[I[I[I[I[II[I[[I[ZI[I[I[I[I[I[I[F)V
        @JvmStatic
        private external fun getSuggestionsNative(
            dict: Long,
            proximityInfo: Long,
            dicTraverseSession: Long,
            xCoordinates: IntArray,
            yCoordinates: IntArray,
            times: IntArray,
            pointerIds: IntArray,
            inputCodePoints: IntArray,
            inputSize: Int,
            suggestOptions: IntArray,
            prevWordCodePointArrays: Array<IntArray>,
            isBeginningOfSentenceArray: BooleanArray,
            prevWordCount: Int,
            outSuggestionCount: IntArray,
            outCodePoints: IntArray,
            outScores: IntArray,
            outSpaceIndices: IntArray,
            outTypes: IntArray,
            outAutoCommitFirstWordConfidence: IntArray,
            inOutWeightOfLangModelVsSpatialModel: FloatArray
        )

        // getProbabilityNative: (J[I)I
        @JvmStatic
        private external fun getProbabilityNative(dict: Long, word: IntArray): Int

        // getMaxProbabilityOfExactMatchesNative: (J[I)I
        @JvmStatic
        private external fun getMaxProbabilityOfExactMatchesNative(dict: Long, word: IntArray): Int

        // getNgramProbabilityNative: (J[[I[Z[I)I
        @JvmStatic
        private external fun getNgramProbabilityNative(
            dict: Long,
            prevWordCodePointArrays: Array<IntArray>,
            isBeginningOfSentenceArray: BooleanArray,
            word: IntArray
        ): Int

        // getWordPropertyNative: (J[IZ[I[Z[I Ljava/util/ArrayList; x6)V
        @JvmStatic
        private external fun getWordPropertyNative(
            dict: Long,
            word: IntArray,
            isBeginningOfSentence: Boolean,
            outCodePoints: IntArray,
            outFlags: BooleanArray,
            outProbabilityInfo: IntArray,
            outNgramPrevWordsArray: ArrayList<IntArray>,
            outNgramPrevWordIsBeginningOfSentenceArray: ArrayList<BooleanArray>,
            outNgramTargets: ArrayList<IntArray>,
            outNgramProbabilityInfo: ArrayList<IntArray>,
            outShortcutTargets: ArrayList<IntArray>,
            outShortcutProbabilities: ArrayList<IntArray>
        )

        // getNextWordNative: (JI[I[Z)I
        @JvmStatic
        private external fun getNextWordNative(
            dict: Long,
            token: Int,
            outCodePoints: IntArray,
            outIsBeginningOfSentence: BooleanArray
        ): Int

        // addUnigramEntryNative: (J[II[IIZZZI)Z
        @JvmStatic
        private external fun addUnigramEntryNative(
            dict: Long,
            word: IntArray,
            probability: Int,
            shortcutTarget: IntArray,
            shortcutProbability: Int,
            isBeginningOfSentence: Boolean,
            isNotAWord: Boolean,
            isPossiblyOffensive: Boolean,
            timestamp: Int
        ): Boolean

        // removeUnigramEntryNative: (J[I)Z
        @JvmStatic
        private external fun removeUnigramEntryNative(dict: Long, word: IntArray): Boolean

        // addNgramEntryNative: (J[[I[Z[III)Z
        @JvmStatic
        private external fun addNgramEntryNative(
            dict: Long,
            prevWordCodePointArrays: Array<IntArray>,
            isBeginningOfSentenceArray: BooleanArray,
            word: IntArray,
            probability: Int,
            timestamp: Int
        ): Boolean

        // removeNgramEntryNative: (J[[I[Z[I)Z
        @JvmStatic
        private external fun removeNgramEntryNative(
            dict: Long,
            prevWordCodePointArrays: Array<IntArray>,
            isBeginningOfSentenceArray: BooleanArray,
            word: IntArray
        ): Boolean

        // updateEntriesForWordWithNgramContextNative: (J[[I[Z[IZII)Z
        @JvmStatic
        private external fun updateEntriesForWordWithNgramContextNative(
            dict: Long,
            prevWordCodePointArrays: Array<IntArray>,
            isBeginningOfSentenceArray: BooleanArray,
            word: IntArray,
            isValidWord: Boolean,
            count: Int,
            timestamp: Int
        ): Boolean

        // updateEntriesForInputEventsNative:
        //   (J[Lcom/keysink/inputmethod/latin/dictionary/WordInputEventForPersonalization;I)I
        @JvmStatic
        private external fun updateEntriesForInputEventsNative(
            dict: Long,
            inputEvents: Array<WordInputEventForPersonalization>,
            startIndex: Int
        ): Int

        // getPropertyNative: (JLjava/lang/String;)Ljava/lang/String;
        @JvmStatic
        private external fun getPropertyNative(dict: Long, query: String): String

        // isCorruptedNative: (J)Z
        @JvmStatic
        private external fun isCorruptedNative(dict: Long): Boolean

        // migrateNative: (JLjava/lang/String;J)Z
        @JvmStatic
        private external fun migrateNative(
            dict: Long,
            dictFilePath: String,
            newFormatVersion: Long
        ): Boolean

    }
}
