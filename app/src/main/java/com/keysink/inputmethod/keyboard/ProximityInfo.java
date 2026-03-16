/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2020 wittmane
 * Copyright (C) 2019 Raimondas Rimkus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.keysink.inputmethod.keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProximityInfo {
    static {
        System.loadLibrary("jni_latinime");
    }

    static native long setProximityInfoNative(
            int displayWidth, int displayHeight, int gridWidth, int gridHeight,
            int mostCommonKeyWidth, int mostCommonKeyHeight, int[] proximityCharsArray,
            int keyCount, int[] keyXCoordinates, int[] keyYCoordinates,
            int[] keyWidths, int[] keyHeights, int[] keyCharCodes,
            float[] sweetSpotCenterXs, float[] sweetSpotCenterYs, float[] sweetSpotRadii);

    static native void releaseProximityInfoNative(long nativeProximityInfo);

    /** Must match MAX_PROXIMITY_CHARS_SIZE in defines.h */
    private static final int MAX_PROXIMITY_CHARS_SIZE = 16;

    private static final List<Key> EMPTY_KEY_LIST = Collections.emptyList();

    private final int mGridWidth;
    private final int mGridHeight;
    private final int mGridSize;
    private final int mCellWidth;
    private final int mCellHeight;
    private final int mKeyboardMinWidth;
    private final int mKeyboardHeight;
    private final List<Key> mSortedKeys;
    private final List<Key>[] mGridNeighbors;
    private long mNativeProximityInfo;

    @SuppressWarnings("unchecked")
    ProximityInfo(final int gridWidth, final int gridHeight, final int minWidth, final int height,
            final int mostCommonKeyWidth, final int mostCommonKeyHeight,
            final List<Key> sortedKeys) {
        mGridWidth = gridWidth;
        mGridHeight = gridHeight;
        mGridSize = mGridWidth * mGridHeight;
        mCellWidth = (minWidth + mGridWidth - 1) / mGridWidth;
        mCellHeight = (height + mGridHeight - 1) / mGridHeight;
        mKeyboardMinWidth = minWidth;
        mKeyboardHeight = height;
        mSortedKeys = sortedKeys;
        mGridNeighbors = new List[mGridSize];
        if (minWidth == 0 || height == 0) {
            // No proximity required. Keyboard might be more keys keyboard.
            mNativeProximityInfo = 0;
            return;
        }
        computeNearestNeighbors();
        mNativeProximityInfo = createNativeProximityInfo(mostCommonKeyWidth, mostCommonKeyHeight);
    }

    public long getNativeProximityInfo() {
        return mNativeProximityInfo;
    }

    public void release() {
        if (mNativeProximityInfo != 0) {
            releaseProximityInfoNative(mNativeProximityInfo);
            mNativeProximityInfo = 0;
        }
    }

    private long createNativeProximityInfo(final int mostCommonKeyWidth,
            final int mostCommonKeyHeight) {
        // Build list of non-spacer keys
        final List<Key> keys = new ArrayList<>();
        for (final Key key : mSortedKeys) {
            if (!key.isSpacer()) {
                keys.add(key);
            }
        }
        final int keyCount = keys.size();

        // Extract key data arrays
        final int[] keyXCoordinates = new int[keyCount];
        final int[] keyYCoordinates = new int[keyCount];
        final int[] keyWidths = new int[keyCount];
        final int[] keyHeights = new int[keyCount];
        final int[] keyCharCodes = new int[keyCount];
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys.get(i);
            keyXCoordinates[i] = key.getX();
            keyYCoordinates[i] = key.getY();
            keyWidths[i] = key.getWidth();
            keyHeights[i] = key.getHeight();
            keyCharCodes[i] = key.getCode();
        }

        // Build proximity chars array from grid neighbors
        final int[] proximityCharsArray = new int[mGridSize * MAX_PROXIMITY_CHARS_SIZE];
        for (int i = 0; i < mGridSize; i++) {
            final int baseIndex = i * MAX_PROXIMITY_CHARS_SIZE;
            final List<Key> neighbors = mGridNeighbors[i];
            int count = 0;
            if (neighbors != null) {
                for (int j = 0; j < neighbors.size() && count < MAX_PROXIMITY_CHARS_SIZE; j++) {
                    final int code = neighbors.get(j).getCode();
                    if (code > 0) {
                        proximityCharsArray[baseIndex + count] = code;
                        count++;
                    }
                }
            }
            // Fill remaining slots with 0 (NOT_A_CODE_POINT)
            for (int j = count; j < MAX_PROXIMITY_CHARS_SIZE; j++) {
                proximityCharsArray[baseIndex + j] = 0;
            }
        }

        // Sweet spot data — not used, pass empty arrays
        final float[] defaultCenterXs = new float[keyCount];
        final float[] defaultCenterYs = new float[keyCount];
        final float[] defaultRadii = new float[keyCount];
        for (int i = 0; i < keyCount; i++) {
            defaultCenterXs[i] = keyXCoordinates[i] + keyWidths[i] / 2.0f;
            defaultCenterYs[i] = keyYCoordinates[i] + keyHeights[i] / 2.0f;
            defaultRadii[i] = 0.0f;
        }

        return setProximityInfoNative(mKeyboardMinWidth, mKeyboardHeight,
                mGridWidth, mGridHeight, mostCommonKeyWidth, mostCommonKeyHeight,
                proximityCharsArray, keyCount, keyXCoordinates, keyYCoordinates,
                keyWidths, keyHeights, keyCharCodes,
                defaultCenterXs, defaultCenterYs, defaultRadii);
    }

    private void computeNearestNeighbors() {
        final int keyCount = mSortedKeys.size();
        final int gridSize = mGridNeighbors.length;
        final int maxKeyRight = mGridWidth * mCellWidth;
        final int maxKeyBottom = mGridHeight * mCellHeight;

        final Key[] neighborsFlatBuffer = new Key[gridSize * keyCount];
        final int[] neighborCountPerCell = new int[gridSize];
        for (final Key key : mSortedKeys) {
            if (key.isSpacer()) continue;

            final int keyX = key.getX();
            final int keyY = key.getY();
            final int keyTop = keyY - key.getTopPadding();
            final int keyBottom = Math.min(keyY + key.getHeight() + key.getBottomPadding(),
                    maxKeyBottom);
            final int keyLeft = keyX - key.getLeftPadding();
            final int keyRight = Math.min(keyX + key.getWidth() + key.getRightPadding(),
                    maxKeyRight);
            final int yDeltaToGrid = keyTop % mCellHeight;
            final int xDeltaToGrid = keyLeft % mCellWidth;
            final int yStart = keyTop - yDeltaToGrid;
            final int xStart = keyLeft - xDeltaToGrid;
            int baseIndexOfCurrentRow = (yStart / mCellHeight) * mGridWidth + (xStart / mCellWidth);
            for (int cellTop = yStart; cellTop < keyBottom; cellTop += mCellHeight) {
                int index = baseIndexOfCurrentRow;
                for (int cellLeft = xStart; cellLeft < keyRight; cellLeft += mCellWidth) {
                    neighborsFlatBuffer[index * keyCount + neighborCountPerCell[index]] = key;
                    ++neighborCountPerCell[index];
                    ++index;
                }
                baseIndexOfCurrentRow += mGridWidth;
            }
        }

        for (int i = 0; i < gridSize; ++i) {
            final int indexStart = i * keyCount;
            final int indexEnd = indexStart + neighborCountPerCell[i];
            final ArrayList<Key> neighbors = new ArrayList<>(indexEnd - indexStart);
            for (int index = indexStart; index < indexEnd; index++) {
                neighbors.add(neighborsFlatBuffer[index]);
            }
            mGridNeighbors[i] = Collections.unmodifiableList(neighbors);
        }
    }

    public List<Key> getNearestKeys(final int x, final int y) {
        if (x >= 0 && x < mKeyboardMinWidth && y >= 0 && y < mKeyboardHeight) {
            int index = (y / mCellHeight) * mGridWidth + (x / mCellWidth);
            if (index < mGridSize) {
                return mGridNeighbors[index];
            }
        }
        return EMPTY_KEY_LIST;
    }
}
