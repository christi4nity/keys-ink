# v0.2 Autocorrect + Suggestions Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a suggestion strip and autocorrect to keys, ink by porting the AOSP/HeliBoard native dictionary engine, English-only, with an e-ink-optimized UI.

**Architecture:** Port HeliBoard's native C++ dictionary engine (Patricia trie, proximity correction, frequency scoring) via JNI, wire it through a Kotlin suggestion pipeline into an e-ink-styled suggestion strip. Auto-correct on space/punctuation with undo-on-backspace. Settings toggle to disable auto-correct.

**Tech Stack:** C++ (native dictionary engine via NDK/ndk-build), Kotlin (new suggestion pipeline + UI), Java (existing keyboard code + JNI wrappers), AOSP binary dictionary format v2.

---

## Reference Material

- **HeliBoard source**: github.com/Helium314/HeliBoard (primary reference for all ported code)
- **AOSP dictionaries**: codeberg.org/Helium314/aosp-dictionaries (English `.dict` files, Apache 2.0)
- **Existing codebase**: Package `com.keysink.inputmethod`, pure Java, no native code currently
- **JDK for builds**: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"`
- **Device**: Boox NoteAir 5C connected via USB, adb at `~/Library/Android/sdk/platform-tools/adb`
- **Install command**: `~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`

## File Map

### Native C++ (copy from HeliBoard)

All files under `app/src/main/jni/`:

| Directory | Purpose | Approx Files |
|-----------|---------|------|
| `jni/Android.mk`, `NativeFileList.mk`, `Application.mk`, `CleanupNativeFileList.mk` | NDK build config | 4 |
| `jni/com_android_inputmethod_*.cpp/.h` | JNI bridge (5 binding pairs) | 10 |
| `jni/jni_common.cpp/.h` | JNI_OnLoad, method registration | 2 |
| `jni/src/defines.h` | Global defines | 1 |
| `jni/src/suggest/core/` | Core suggestion algorithm, trie traversal, proximity, scoring | ~30 |
| `jni/src/suggest/policyimpl/typing/` | Typing-specific scoring/traversal/weighting | 10 |
| `jni/src/suggest/policyimpl/gesture/` | Gesture policy factory (stub, needed for compilation) | 2 |
| `jni/src/dictionary/` | Dictionary format parsing, v2+v4 Patricia trie, ngram, header | ~40 |
| `jni/src/utils/` | Autocorrection thresholds, char utils, JNI data utils | ~10 |

### New Kotlin Files (write from scratch, referencing HeliBoard)

| File | Purpose |
|------|---------|
| `app/src/main/java/com/keysink/inputmethod/latin/Suggest.kt` | Suggestion orchestrator: calls dictionary, ranks results, evaluates auto-correct |
| `app/src/main/java/com/keysink/inputmethod/latin/WordComposer.kt` | Tracks composing word: codepoints + key coordinates |
| `app/src/main/java/com/keysink/inputmethod/latin/SuggestedWords.kt` | Immutable suggestion results container |
| `app/src/main/java/com/keysink/inputmethod/latin/NgramContext.kt` | Previous-word context for n-gram prediction |
| `app/src/main/java/com/keysink/inputmethod/latin/dictionary/Dictionary.kt` | Abstract base: `getSuggestions()` contract |
| `app/src/main/java/com/keysink/inputmethod/latin/dictionary/BinaryDictionary.kt` | JNI wrapper for native dictionary |
| `app/src/main/java/com/keysink/inputmethod/latin/dictionary/DictionaryFacilitator.kt` | Interface for multi-dictionary management |
| `app/src/main/java/com/keysink/inputmethod/latin/dictionary/DictionaryFacilitatorImpl.kt` | Implementation: loads main dict, delegates suggestions |
| `app/src/main/java/com/keysink/inputmethod/latin/dictionary/DictionaryFactory.kt` | Creates dictionary instances from assets |
| `app/src/main/java/com/keysink/inputmethod/latin/common/InputPointers.kt` | Stores x/y touch coordinates per key press |
| `app/src/main/java/com/keysink/inputmethod/latin/common/ComposedData.kt` | Bundles InputPointers + codepoints for native layer |
| `app/src/main/java/com/keysink/inputmethod/latin/common/NativeSuggestOptions.kt` | Option flags passed to native layer |
| `app/src/main/java/com/keysink/inputmethod/latin/suggestions/SuggestionStripView.kt` | E-ink suggestion strip: 3 text slots, tap to pick |
| `app/src/main/java/com/keysink/inputmethod/latin/suggestions/SuggestionStripLayoutHelper.kt` | Measures/positions suggestion TextViews |
| `app/src/main/java/com/keysink/inputmethod/latin/suggestions/SuggestionStripViewAccessor.kt` | Interface bridging InputLogic to strip |

### Modified Existing Files

| File | Change |
|------|--------|
| `app/build.gradle` | Add `externalNativeBuild` for ndk-build, Kotlin plugin, NDK version |
| `build.gradle` | Add Kotlin classpath dependency |
| `app/src/main/java/com/keysink/inputmethod/latin/LatinIME.java` | Implement `SuggestionStripView.Listener`, wire suggestion pipeline |
| `app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java` | Add `WordComposer`, call `Suggest` on key events, handle auto-correct commit/undo |
| `app/src/main/java/com/keysink/inputmethod/latin/settings/Settings.java` | Add `PREF_AUTO_CORRECT` constant |
| `app/src/main/java/com/keysink/inputmethod/latin/settings/SettingsValues.java` | Read auto-correct pref |
| `app/src/main/java/com/keysink/inputmethod/keyboard/ProximityInfo.java` | Add JNI native method declarations for proximity data |
| `app/src/main/java/com/keysink/inputmethod/keyboard/MainKeyboardView.java` | Pass key coordinates to InputLogic on key press |
| `app/src/main/res/layout/input_view.xml` | Add SuggestionStripView above MainKeyboardView |
| `app/src/main/res/xml/prefs_screen_preferences.xml` | Add auto-correct toggle |

### New Resource Files

| File | Purpose |
|------|---------|
| `app/src/main/res/layout/suggestion_strip.xml` | Suggestion strip layout: 3 TextViews in a LinearLayout |
| `app/src/main/res/values/suggestion-strip-attrs.xml` | Custom attributes for suggestion strip styling |
| `app/src/main/assets/dicts/main_en.dict` | English AOSP dictionary (~3-4MB, Apache 2.0) |

---

## Chunk 1: NDK Build Setup + Native Code

### Task 1: Add Kotlin support to the build

**Files:**
- Modify: `build.gradle` (root)
- Modify: `app/build.gradle`
- Modify: `gradle.properties`

- [ ] **Step 1: Add Kotlin plugin to root `build.gradle`**

Add the Kotlin Gradle plugin to the buildscript dependencies:

```groovy
buildscript {
    ext.kotlin_version = '2.1.10'
    repositories {
        mavenCentral()
        maven {
            url 'https://maven.google.com/'
            name 'Google'
        }
        google()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:9.0.0'
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}
```

- [ ] **Step 2: Apply Kotlin plugin in `app/build.gradle`**

Add at the top, after the Android plugin:

```groovy
apply plugin: 'kotlin-android'
```

Add Kotlin stdlib to dependencies:

```groovy
dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
}
```

- [ ] **Step 3: Enable Kotlin in `gradle.properties`**

Remove or change `android.builtInKotlin=false` to `android.builtInKotlin=true` (or simply remove the line — Kotlin is enabled by applying the plugin).

- [ ] **Step 4: Create a test Kotlin file to verify**

Create `app/src/main/java/com/keysink/inputmethod/latin/common/ComposedData.kt`:

```kotlin
package com.keysink.inputmethod.latin.common

/**
 * Bundles composed input data for passing to the native suggestion engine.
 * Placeholder — will be filled in Task 5.
 */
class ComposedData
```

- [ ] **Step 5: Build and verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL (Kotlin compiles alongside Java)

- [ ] **Step 6: Commit**

```bash
git add build.gradle app/build.gradle gradle.properties app/src/main/java/com/keysink/inputmethod/latin/common/ComposedData.kt
git commit -m "feat: add Kotlin support to build"
```

---

### Task 2: Copy native C++ source from HeliBoard

**Files:**
- Create: `app/src/main/jni/` (entire directory tree, ~90 files)

This task copies HeliBoard's native code wholesale. No modifications to C++ — it's a proven, tested codebase.

- [ ] **Step 1: Clone HeliBoard repo (shallow)**

```bash
git clone --depth 1 https://github.com/Helium314/HeliBoard.git /tmp/heliboard
```

- [ ] **Step 2: Copy the native JNI directory**

```bash
cp -R /tmp/heliboard/app/src/main/jni app/src/main/jni
```

- [ ] **Step 3: Verify the directory structure**

```bash
ls app/src/main/jni/Android.mk
ls app/src/main/jni/NativeFileList.mk
ls app/src/main/jni/Application.mk
ls app/src/main/jni/src/defines.h
ls app/src/main/jni/src/suggest/core/suggest.cpp
ls app/src/main/jni/src/dictionary/structure/v2/patricia_trie_policy.cpp
```

All files should exist.

- [ ] **Step 4: Review `Application.mk` for ABI filters**

Ensure it contains:

```makefile
APP_STL := c++_static
APP_ABI := all
```

If it has `APP_ABI := all`, consider narrowing to `arm64-v8a armeabi-v7a` to reduce APK size (e-ink devices are ARM). This is optional.

- [ ] **Step 5: Commit native code**

```bash
git add app/src/main/jni/
git commit -m "feat: add HeliBoard native JNI dictionary engine"
```

---

### Task 3: Configure NDK build in Gradle

**Files:**
- Modify: `app/build.gradle`

- [ ] **Step 1: Add externalNativeBuild and NDK config**

In the `android {}` block of `app/build.gradle`, add:

```groovy
android {
    // ... existing config ...

    ndkVersion '27.0.12077973'

    externalNativeBuild {
        ndkBuild {
            path file('src/main/jni/Android.mk')
        }
    }

    defaultConfig {
        // ... existing config ...
        ndk {
            abiFilters 'arm64-v8a', 'armeabi-v7a'
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
```

Note: Check the locally installed NDK version with `ls ~/Library/Android/sdk/ndk/` and use that version string. If no NDK is installed, install one: `~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager "ndk;27.0.12077973"` (or the latest available).

- [ ] **Step 2: Build to verify native compilation**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`

This will take longer than usual (native compile). Watch for:
- `libjni_latinime.so` being built for each ABI
- BUILD SUCCESSFUL

If it fails with NDK-related errors, check:
- NDK is installed: `ls ~/Library/Android/sdk/ndk/`
- `ndkVersion` matches installed version
- `Android.mk` references are correct

- [ ] **Step 3: Verify the .so is in the APK**

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libjni_latinime
```

Expected: `lib/arm64-v8a/libjni_latinime.so` and `lib/armeabi-v7a/libjni_latinime.so`

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle
git commit -m "feat: configure NDK build for native dictionary engine"
```

---

### Task 4: Update JNI package references

The HeliBoard native code registers JNI methods for classes in `com.android.inputmethod.*` (AOSP package) or `helium314.keyboard.*` (HeliBoard package). Our package is `com.keysink.inputmethod.*`. The JNI bridge files need their class path strings updated.

**Files:**
- Modify: `app/src/main/jni/com_android_inputmethod_latin_BinaryDictionary.cpp`
- Modify: `app/src/main/jni/com_android_inputmethod_latin_BinaryDictionaryUtils.cpp`
- Modify: `app/src/main/jni/com_android_inputmethod_latin_DicTraverseSession.cpp`
- Modify: `app/src/main/jni/com_android_inputmethod_keyboard_ProximityInfo.cpp`
- Modify: `app/src/main/jni/jni_common.cpp`

- [ ] **Step 1: Identify the JNI class path strings**

Search all `.cpp` files in `jni/` for class path strings. They'll look like:
- `"com/android/inputmethod/latin/BinaryDictionary"` or
- `"helium314/keyboard/latin/BinaryDictionary"`

```bash
grep -rn 'inputmethod\|helium314' app/src/main/jni/*.cpp app/src/main/jni/*.h
```

- [ ] **Step 2: Replace package paths in JNI bridge files**

For each file, replace the HeliBoard/AOSP package path with `com/keysink/inputmethod`:

In `jni_common.cpp` — update the class registration table.
In each `com_android_inputmethod_*.cpp` — update the `JAVA_CLASS_NAME` or equivalent constant.

The exact strings will be one of:
- `"helium314/keyboard/latin/BinaryDictionary"` → `"com/keysink/inputmethod/latin/dictionary/BinaryDictionary"`
- `"helium314/keyboard/latin/DicTraverseSession"` → `"com/keysink/inputmethod/latin/dictionary/DicTraverseSession"`
- `"helium314/keyboard/latin/utils/BinaryDictionaryUtils"` → `"com/keysink/inputmethod/latin/dictionary/BinaryDictionaryUtils"`
- `"helium314/keyboard/keyboard/ProximityInfo"` → `"com/keysink/inputmethod/keyboard/ProximityInfo"`

- [ ] **Step 3: Build to verify JNI compilation still succeeds**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL (native code compiles — Java classes don't exist yet, but that's OK, JNI registration happens at runtime)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/jni/
git commit -m "refactor: update JNI class paths to com.keysink.inputmethod"
```

---

## Chunk 2: Dictionary Pipeline (Kotlin)

### Task 5: Core data classes

**Files:**
- Create: `app/src/main/java/com/keysink/inputmethod/latin/common/ComposedData.kt` (replace placeholder)
- Create: `app/src/main/java/com/keysink/inputmethod/latin/common/InputPointers.kt`
- Create: `app/src/main/java/com/keysink/inputmethod/latin/common/NativeSuggestOptions.kt`
- Create: `app/src/main/java/com/keysink/inputmethod/latin/SuggestedWords.kt`
- Create: `app/src/main/java/com/keysink/inputmethod/latin/NgramContext.kt`
- Create: `app/src/main/java/com/keysink/inputmethod/latin/WordComposer.kt`

These are data/utility classes with no dependencies on Android UI. Port from HeliBoard, converting Java to Kotlin where applicable. Keep the same public API surface that the native JNI layer expects.

- [ ] **Step 1: Write `InputPointers.kt`**

Port from HeliBoard's `InputPointers.java`. Stores parallel int arrays for x-coordinates, y-coordinates, pointer IDs, and timestamps. Backed by `ResizableIntArray` (can be inlined as growable `IntArray` wrapper in Kotlin).

Key API:
- `addPointer(index: Int, x: Int, y: Int, pointerId: Int, time: Int)`
- `getXCoordinates(): IntArray`, `getYCoordinates(): IntArray`, etc.
- `reset()`, `getPointerSize(): Int`

- [ ] **Step 2: Write `ComposedData.kt`**

Replace the placeholder. Simple data class:

```kotlin
package com.keysink.inputmethod.latin.common

data class ComposedData(
    val inputPointers: InputPointers,
    val isBatchMode: Boolean,
    val codePointCount: Int,
    val codePoints: IntArray
)
```

- [ ] **Step 3: Write `NativeSuggestOptions.kt`**

Port from HeliBoard. An IntArray wrapper that packs boolean flags into indexed positions for the native layer.

- [ ] **Step 4: Write `SuggestedWords.kt`**

Port from HeliBoard's `SuggestedWords.java`. Key elements:
- `SuggestedWordInfo` data class: `word: String`, `score: Int`, `kindAndFlags: Int`, `sourceDictionary: Dictionary?`
- `SuggestedWords` class: `ArrayList<SuggestedWordInfo>`, `willAutoCorrect: Boolean`, `typedWordValid: Boolean`
- Constants: `INDEX_OF_TYPED_WORD = 0`, `INDEX_OF_AUTO_CORRECTION = 1`
- Kind flags: `KIND_TYPED`, `KIND_CORRECTION`, `KIND_COMPLETION`, `KIND_PREDICTION`

- [ ] **Step 5: Write `NgramContext.kt`**

Port from HeliBoard. Stores previous word(s) for n-gram context. Key API:
- `prevWordsInfo: Array<WordInfo?>` (up to 3 previous words)
- `getNgramContextForPrediction(): NgramContext`
- `EMPTY_PREV_WORDS_INFO` constant
- JNI-compatible: `toIntArrays()` method for native calls

- [ ] **Step 6: Write `WordComposer.kt`**

Port from HeliBoard. Tracks composing word state. Key API:
- `addCodePoint(codePoint: Int, keyX: Int, keyY: Int)`
- `deleteLast()` (backspace)
- `reset()` (commit/cancel)
- `getComposedDataSnapshot(): ComposedData`
- `typedWord: String` (current typed text)
- `isComposingWord: Boolean`
- `isBatchMode: Boolean` (false for now — needed for swipe later)

- [ ] **Step 7: Build to verify compilation**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/common/ app/src/main/java/com/keysink/inputmethod/latin/SuggestedWords.kt app/src/main/java/com/keysink/inputmethod/latin/NgramContext.kt app/src/main/java/com/keysink/inputmethod/latin/WordComposer.kt
git commit -m "feat: add core suggestion data classes"
```

---

### Task 6: Dictionary abstraction + JNI wrapper

**Files:**
- Create: `app/src/main/java/com/keysink/inputmethod/latin/dictionary/Dictionary.kt`
- Create: `app/src/main/java/com/keysink/inputmethod/latin/dictionary/BinaryDictionary.kt`
- Create: `app/src/main/java/com/keysink/inputmethod/latin/dictionary/DicTraverseSession.kt`
- Create: `app/src/main/java/com/keysink/inputmethod/latin/dictionary/BinaryDictionaryUtils.kt`

- [ ] **Step 1: Write `Dictionary.kt`**

Abstract base class:

```kotlin
package com.keysink.inputmethod.latin.dictionary

import com.keysink.inputmethod.latin.NgramContext
import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.keysink.inputmethod.latin.common.ComposedData

abstract class Dictionary(val dictType: String, val locale: java.util.Locale) {
    companion object {
        const val TYPE_MAIN = "main"
        const val TYPE_USER_HISTORY = "history"
        const val TYPE_USER = "user"
        const val TYPE_CONTACTS = "contacts"
    }

    abstract fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: Any?, // simplified for v0.2
        sessionId: Int,
        weightForLocale: Float,
        outSuggestionResults: ArrayList<SuggestedWordInfo>
    )

    abstract fun isValidWord(word: String): Boolean

    open fun close() {}
}
```

- [ ] **Step 2: Write `DicTraverseSession.kt`**

JNI wrapper for native traversal session:

```kotlin
package com.keysink.inputmethod.latin.dictionary

class DicTraverseSession(locale: java.util.Locale, dictSize: Long) {
    val nativeHandle: Long

    init {
        nativeHandle = setDicTraverseSessionNative(locale.toString(), dictSize)
    }

    fun close() {
        releaseDicTraverseSessionNative(nativeHandle)
    }

    companion object {
        init { System.loadLibrary("jni_latinime") }

        @JvmStatic private external fun setDicTraverseSessionNative(locale: String, dictSize: Long): Long
        @JvmStatic private external fun releaseDicTraverseSessionNative(handle: Long)
    }
}
```

- [ ] **Step 3: Write `BinaryDictionary.kt`**

JNI wrapper for the native dictionary. Port from HeliBoard. Key native methods:
- `openNative(path, offset, length, isUpdatable): Long` — returns dict handle
- `getSuggestionsNative(dictHandle, traverseSessionHandle, xCoords, yCoords, times, pointerIds, codePoints, ..., outWords, outScores, ...)`
- `isValidWordNative(dictHandle, word): Boolean`
- `closeNative(dictHandle)`

The class wraps these in Kotlin methods matching the `Dictionary` abstract API.

- [ ] **Step 4: Write `BinaryDictionaryUtils.kt`**

JNI wrapper for utility functions (dictionary version checking, etc.). Minimal for v0.2.

- [ ] **Step 5: Build and verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL (JNI native methods declared but not yet called at runtime — that's fine)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/dictionary/
git commit -m "feat: add Dictionary abstraction and JNI wrappers"
```

---

### Task 7: Dictionary facilitator + factory

**Files:**
- Create: `app/src/main/java/com/keysink/inputmethod/latin/dictionary/DictionaryFacilitator.kt`
- Create: `app/src/main/java/com/keysink/inputmethod/latin/dictionary/DictionaryFacilitatorImpl.kt`
- Create: `app/src/main/java/com/keysink/inputmethod/latin/dictionary/DictionaryFactory.kt`

- [ ] **Step 1: Write `DictionaryFacilitator.kt`**

Interface:

```kotlin
package com.keysink.inputmethod.latin.dictionary

import com.keysink.inputmethod.latin.NgramContext
import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.keysink.inputmethod.latin.common.ComposedData
import java.util.Locale

interface DictionaryFacilitator {
    fun getSuggestionResults(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        sessionId: Int
    ): ArrayList<SuggestedWordInfo>

    fun isValidWord(word: String): Boolean
    fun hasAtLeastOneInitializedMainDictionary(): Boolean
    fun resetDictionaries(context: android.content.Context, locale: Locale)
    fun closeDictionaries()
}
```

- [ ] **Step 2: Write `DictionaryFactory.kt`**

Loads `.dict` files from `assets/dicts/`:

```kotlin
package com.keysink.inputmethod.latin.dictionary

import android.content.Context
import java.io.File
import java.util.Locale

object DictionaryFactory {
    fun createMainDictionary(context: Context, locale: Locale): BinaryDictionary? {
        val assetName = "dicts/main_${locale.language}.dict"
        // Copy from assets to cache dir (native code needs a file path)
        val cacheFile = File(context.cacheDir, assetName)
        if (!cacheFile.exists()) {
            cacheFile.parentFile?.mkdirs()
            try {
                context.assets.open(assetName).use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                return null // Dictionary not available for this locale
            }
        }
        return BinaryDictionary(cacheFile.absolutePath, 0, cacheFile.length(), false, locale, Dictionary.TYPE_MAIN)
    }
}
```

- [ ] **Step 3: Write `DictionaryFacilitatorImpl.kt`**

Implementation. For v0.2, only manages a single main dictionary:
- `resetDictionaries()` — calls `DictionaryFactory.createMainDictionary()`
- `getSuggestionResults()` — delegates to main dictionary's `getSuggestions()`
- `closeDictionaries()` — closes main dictionary

Async loading: load dictionary on a background thread, expose `hasAtLeastOneInitializedMainDictionary()` flag.

- [ ] **Step 4: Build and verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/dictionary/DictionaryFacilitator.kt app/src/main/java/com/keysink/inputmethod/latin/dictionary/DictionaryFacilitatorImpl.kt app/src/main/java/com/keysink/inputmethod/latin/dictionary/DictionaryFactory.kt
git commit -m "feat: add DictionaryFacilitator for dictionary management"
```

---

### Task 8: Suggest engine

**Files:**
- Create: `app/src/main/java/com/keysink/inputmethod/latin/Suggest.kt`

- [ ] **Step 1: Write `Suggest.kt`**

Port from HeliBoard's `Suggest.kt`. Core suggestion orchestrator:

```kotlin
package com.keysink.inputmethod.latin

import com.keysink.inputmethod.latin.common.ComposedData
import com.keysink.inputmethod.latin.dictionary.DictionaryFacilitator

class Suggest(private val dictionaryFacilitator: DictionaryFacilitator) {

    companion object {
        private const val AUTOCORRECT_THRESHOLD = 0.185f // HeliBoard default, tune later
    }

    fun getSuggestedWords(
        wordComposer: WordComposer,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        isCorrectionEnabled: Boolean
    ): SuggestedWords {
        // 1. Get raw suggestions from dictionary
        // 2. Remove duplicates
        // 3. Apply capitalization transforms
        // 4. Evaluate auto-correct threshold
        // 5. Build SuggestedWords with willAutoCorrect flag
        // Reference HeliBoard's Suggest.kt for the full algorithm
    }
}
```

Key behaviors:
- If `isCorrectionEnabled` is false, still return suggestions but set `willAutoCorrect = false`
- Typed word always at index 0
- Top auto-correct candidate at index 1 (if score exceeds threshold)
- Dedup by case-insensitive comparison

- [ ] **Step 2: Build and verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/Suggest.kt
git commit -m "feat: add Suggest engine for suggestion orchestration"
```

---

### Task 9: Add English dictionary asset

**Files:**
- Create: `app/src/main/assets/dicts/main_en.dict`

- [ ] **Step 1: Download the English AOSP dictionary**

From the HeliBoard repo or Codeberg:

```bash
mkdir -p app/src/main/assets/dicts
curl -L -o app/src/main/assets/dicts/main_en.dict \
  "https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/dictionaries/en_US/main_en_US.dict"
```

If the URL has changed, check https://codeberg.org/Helium314/aosp-dictionaries and find the English v2 dictionary. Alternative: extract from HeliBoard's clone at `/tmp/heliboard/app/src/main/assets/dicts/`.

- [ ] **Step 2: Verify the file**

```bash
ls -la app/src/main/assets/dicts/main_en.dict
# Should be ~3-5 MB
hexdump -C app/src/main/assets/dicts/main_en.dict | head -1
# Should start with magic bytes
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/dicts/main_en.dict
git commit -m "feat: add English AOSP dictionary (Apache 2.0)"
```

---

## Chunk 3: Suggestion Strip UI

### Task 10: Suggestion strip layout XML

**Files:**
- Create: `app/src/main/res/layout/suggestion_strip.xml`
- Modify: `app/src/main/res/layout/input_view.xml`

- [ ] **Step 1: Create `suggestion_strip.xml`**

E-ink optimized: white background, black text, no dividers, center candidate bold.

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/suggestion_strip"
    android:layout_width="match_parent"
    android:layout_height="@dimen/config_suggestion_strip_height"
    android:orientation="horizontal"
    android:background="@color/background_eink"
    android:gravity="center_vertical">

    <TextView
        android:id="@+id/suggestion_left"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:gravity="center"
        android:textColor="@color/key_text_color_eink"
        android:textSize="16sp"
        android:background="?android:attr/selectableItemBackground"
        android:singleLine="true"
        android:ellipsize="end" />

    <TextView
        android:id="@+id/suggestion_center"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:gravity="center"
        android:textColor="@color/key_text_color_eink"
        android:textSize="16sp"
        android:textStyle="bold"
        android:background="?android:attr/selectableItemBackground"
        android:singleLine="true"
        android:ellipsize="end" />

    <TextView
        android:id="@+id/suggestion_right"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:gravity="center"
        android:textColor="@color/key_text_color_eink"
        android:textSize="16sp"
        android:background="?android:attr/selectableItemBackground"
        android:singleLine="true"
        android:ellipsize="end" />

</LinearLayout>
```

- [ ] **Step 2: Add suggestion strip height dimension**

Add to `app/src/main/res/values/config.xml`:

```xml
<dimen name="config_suggestion_strip_height">40dp</dimen>
```

- [ ] **Step 3: Update `input_view.xml` to include the suggestion strip**

Wrap the existing layout in a vertical `LinearLayout` and add the strip above the keyboard:

```xml
<com.keysink.inputmethod.latin.InputView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    style="?attr/inputViewStyle">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <include layout="@layout/suggestion_strip" />

        <com.keysink.inputmethod.keyboard.MainKeyboardView
            android:id="@+id/keyboard_view"
            android:layoutDirection="ltr"
            android:layout_gravity="bottom"
            style="?attr/mainKeyboardViewStyle"
            android:fitsSystemWindows="true"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />
    </LinearLayout>

</com.keysink.inputmethod.latin.InputView>
```

- [ ] **Step 4: Build and verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Install and visual check**

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The suggestion strip should appear as an empty white bar above the keyboard. Verify it doesn't break keyboard rendering.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/suggestion_strip.xml app/src/main/res/layout/input_view.xml app/src/main/res/values/config.xml
git commit -m "feat: add e-ink suggestion strip layout"
```

---

### Task 11: SuggestionStripView Kotlin class

**Files:**
- Create: `app/src/main/java/com/keysink/inputmethod/latin/suggestions/SuggestionStripView.kt`
- Create: `app/src/main/java/com/keysink/inputmethod/latin/suggestions/SuggestionStripViewAccessor.kt`

- [ ] **Step 1: Write `SuggestionStripViewAccessor.kt`**

```kotlin
package com.keysink.inputmethod.latin.suggestions

import com.keysink.inputmethod.latin.SuggestedWords

interface SuggestionStripViewAccessor {
    fun showSuggestionStrip(suggestedWords: SuggestedWords)
    fun setNeutralSuggestionStrip()
}
```

- [ ] **Step 2: Write `SuggestionStripView.kt`**

Custom view that manages the 3 suggestion TextViews:

```kotlin
package com.keysink.inputmethod.latin.suggestions

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.keysink.inputmethod.R
import com.keysink.inputmethod.latin.SuggestedWords
import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo

class SuggestionStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), View.OnClickListener {

    interface Listener {
        fun pickSuggestionManually(wordInfo: SuggestedWordInfo)
    }

    private var listener: Listener? = null
    private var suggestedWords: SuggestedWords = SuggestedWords.EMPTY

    private lateinit var leftView: TextView
    private lateinit var centerView: TextView
    private lateinit var rightView: TextView

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        leftView = findViewById(R.id.suggestion_left)
        centerView = findViewById(R.id.suggestion_center)
        rightView = findViewById(R.id.suggestion_right)

        leftView.setOnClickListener(this)
        centerView.setOnClickListener(this)
        rightView.setOnClickListener(this)
    }

    fun setSuggestions(suggestedWords: SuggestedWords) {
        this.suggestedWords = suggestedWords
        // Map suggestions to views:
        // Index 0 = typed word (or auto-correct if willAutoCorrect)
        // Index 1 = top suggestion
        // Index 2 = second suggestion
        // Center always gets the primary candidate
        val count = suggestedWords.size()

        centerView.text = if (count > 0) suggestedWords.getWord(0) else ""
        leftView.text = if (count > 1) suggestedWords.getWord(1) else ""
        rightView.text = if (count > 2) suggestedWords.getWord(2) else ""

        // If auto-correcting, center shows the correction (index 1),
        // left shows typed word (index 0)
        if (suggestedWords.willAutoCorrect && count > 1) {
            centerView.text = suggestedWords.getWord(1)
            leftView.text = suggestedWords.getWord(0)
            rightView.text = if (count > 2) suggestedWords.getWord(2) else ""
        }
    }

    fun clear() {
        leftView.text = ""
        centerView.text = ""
        rightView.text = ""
        suggestedWords = SuggestedWords.EMPTY
    }

    override fun onClick(v: View) {
        val index = when (v.id) {
            R.id.suggestion_center -> if (suggestedWords.willAutoCorrect) 1 else 0
            R.id.suggestion_left -> if (suggestedWords.willAutoCorrect) 0 else 1
            R.id.suggestion_right -> 2
            else -> return
        }
        if (index < suggestedWords.size()) {
            listener?.pickSuggestionManually(suggestedWords.getInfo(index))
        }
    }
}
```

- [ ] **Step 3: Build and verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/suggestions/
git commit -m "feat: add SuggestionStripView with e-ink styling"
```

---

## Chunk 4: Integration + Auto-correct

### Task 12: Settings toggle for auto-correct

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/settings/Settings.java`
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/settings/SettingsValues.java`
- Modify: `app/src/main/res/xml/prefs_screen_preferences.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add pref constant to `Settings.java`**

Add after the existing `PREF_DELETE_SWIPE` line:

```java
public static final String PREF_AUTO_CORRECT = "pref_auto_correct";
```

- [ ] **Step 2: Read the pref in `SettingsValues.java`**

Add a field:

```java
public final boolean mAutoCorrectionEnabled;
```

Initialize in constructor (find where other boolean prefs are read):

```java
mAutoCorrectionEnabled = prefs.getBoolean(Settings.PREF_AUTO_CORRECT, true);
```

- [ ] **Step 3: Add string resource**

In `app/src/main/res/values/strings.xml`, add:

```xml
<string name="auto_correct">Auto-correction</string>
<string name="auto_correct_summary">Automatically correct misspelled words</string>
```

- [ ] **Step 4: Add toggle to preferences screen**

In `app/src/main/res/xml/prefs_screen_preferences.xml`, add after the auto-cap preference:

```xml
<SwitchPreference
    android:key="pref_auto_correct"
    android:title="@string/auto_correct"
    android:summary="@string/auto_correct_summary"
    android:defaultValue="true"
    android:persistent="true" />
```

- [ ] **Step 5: Build and install**

```bash
JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug && ~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Verify the toggle appears in Preferences settings.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/settings/Settings.java app/src/main/java/com/keysink/inputmethod/latin/settings/SettingsValues.java app/src/main/res/xml/prefs_screen_preferences.xml app/src/main/res/values/strings.xml
git commit -m "feat: add auto-correct settings toggle"
```

---

### Task 13: Wire suggestion pipeline into InputLogic

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java`

This is the most complex integration task. `InputLogic` needs to:
1. Maintain a `WordComposer` tracking the current composing word
2. After each key event, request suggestions from `Suggest`
3. On space/punctuation: auto-commit top suggestion (if auto-correct enabled)
4. On backspace after auto-correct: undo the correction

- [ ] **Step 1: Add fields to `InputLogic`**

```java
private WordComposer mWordComposer;
private Suggest mSuggest;
private SuggestedWords mSuggestedWords = SuggestedWords.EMPTY;
private String mLastComposedWord = "";
private boolean mIsAutoCorrected = false;
```

- [ ] **Step 2: Add initialization method**

```java
public void initSuggest(Suggest suggest) {
    mSuggest = suggest;
    mWordComposer = new WordComposer();
}
```

Called from `LatinIME.onCreate()` after `DictionaryFacilitator` is ready.

- [ ] **Step 3: Update `onCodeInput` to track composing word**

In the existing `onCodeInput()` method, after the character is committed to the editor, add composing word tracking:

- If the code point is a letter: add to `mWordComposer`, set composing text on the editor via `mConnection.setComposingText()`, request suggestion update
- If it's a separator (space, punctuation): commit the current word (with auto-correct if applicable), reset `mWordComposer`
- If it's backspace: handle undo-auto-correct if applicable, otherwise `mWordComposer.deleteLast()`

This requires careful integration with the existing `onCodeInput` flow. Reference HeliBoard's `InputLogic.java` for the control flow.

- [ ] **Step 4: Add suggestion update method**

```java
private void performUpdateSuggestionStrip(SettingsValues settingsValues, SuggestionStripViewAccessor accessor) {
    if (mSuggest == null || !mWordComposer.isComposingWord()) {
        accessor.setNeutralSuggestionStrip();
        return;
    }
    NgramContext ngramContext = mConnection.getNgramContext();
    long proximityInfoHandle = 0; // TODO: get from keyboard
    SuggestedWords suggestedWords = mSuggest.getSuggestedWords(
        mWordComposer, ngramContext, proximityInfoHandle,
        settingsValues.mAutoCorrectionEnabled
    );
    mSuggestedWords = suggestedWords;
    accessor.showSuggestionStrip(suggestedWords);
}
```

- [ ] **Step 5: Add auto-correct commit on separator**

When space or punctuation is typed and `mSuggestedWords.willAutoCorrect`:
1. Get the auto-correct word from `mSuggestedWords.getWord(1)`
2. Replace composing text with the correction via `mConnection.commitText()`
3. Set `mIsAutoCorrected = true`, save `mLastComposedWord`

- [ ] **Step 6: Add undo-auto-correct on backspace**

When backspace is pressed and `mIsAutoCorrected`:
1. Delete the committed correction + separator
2. Re-set composing text to `mLastComposedWord`
3. Reset `mIsAutoCorrected = false`

- [ ] **Step 7: Build and verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java
git commit -m "feat: wire suggestion pipeline into InputLogic with auto-correct"
```

---

### Task 14: Wire everything in LatinIME

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/LatinIME.java`

- [ ] **Step 1: Add SuggestionStripView.Listener implementation**

Add to the class declaration:

```java
public class LatinIME extends InputMethodService implements KeyboardActionListener,
        RichInputMethodManager.SubtypeChangedListener,
        SuggestionStripView.Listener,
        SuggestionStripViewAccessor {
```

- [ ] **Step 2: Add fields**

```java
private SuggestionStripView mSuggestionStripView;
private DictionaryFacilitatorImpl mDictionaryFacilitator;
private Suggest mSuggest;
```

- [ ] **Step 3: Initialize in `onCreate()`**

After existing initialization:

```java
mDictionaryFacilitator = new DictionaryFacilitatorImpl();
mDictionaryFacilitator.resetDictionaries(this, Locale.ENGLISH);
mSuggest = new Suggest(mDictionaryFacilitator);
mInputLogic.initSuggest(mSuggest);
```

- [ ] **Step 4: Wire SuggestionStripView in `onCreateInputView()`**

After the input view is inflated, find and configure the strip:

```java
mSuggestionStripView = inputView.findViewById(R.id.suggestion_strip);
if (mSuggestionStripView != null) {
    mSuggestionStripView.setListener(this);
}
```

Note: The `suggestion_strip` ID comes from `suggestion_strip.xml` which is included in `input_view.xml`.

- [ ] **Step 5: Implement SuggestionStripView.Listener**

```java
@Override
public void pickSuggestionManually(SuggestedWords.SuggestedWordInfo wordInfo) {
    mInputLogic.onPickSuggestionManually(wordInfo, mSettings.getCurrent());
}
```

- [ ] **Step 6: Implement SuggestionStripViewAccessor**

```java
@Override
public void showSuggestionStrip(SuggestedWords suggestedWords) {
    if (mSuggestionStripView != null) {
        mSuggestionStripView.setSuggestions(suggestedWords);
    }
}

@Override
public void setNeutralSuggestionStrip() {
    if (mSuggestionStripView != null) {
        mSuggestionStripView.clear();
    }
}
```

- [ ] **Step 7: Close dictionaries in `onDestroy()`**

```java
@Override
public void onDestroy() {
    mDictionaryFacilitator.closeDictionaries();
    super.onDestroy();
}
```

- [ ] **Step 8: Build, install, and test on device**

```bash
JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug && ~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Test:
1. Open any text field
2. Type "helo" — suggestion strip should show "hello" as center suggestion
3. Press space — should auto-correct to "hello"
4. Immediately press backspace — should undo to "helo"
5. Go to Settings > Preferences > toggle off Auto-correction
6. Type "helo" + space — should stay as "helo"

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/LatinIME.java
git commit -m "feat: wire suggestion strip and auto-correct in LatinIME"
```

---

## Chunk 5: Polish + Ship

### Task 15: ProximityInfo integration

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/keyboard/ProximityInfo.java`
- Modify: `app/src/main/java/com/keysink/inputmethod/keyboard/MainKeyboardView.java`
- Modify: `app/src/main/java/com/keysink/inputmethod/keyboard/Keyboard.java`

Proximity correction (knowing which keys are near the pressed key) dramatically improves suggestion quality. The native code needs a `proximityInfoHandle` from the current keyboard layout.

- [ ] **Step 1: Add JNI native methods to `ProximityInfo.java`**

```java
private static native long setProximityInfoNative(
    int displayWidth, int displayHeight, int gridWidth, int gridHeight,
    int mostCommonKeyWidth, int mostCommonKeyHeight, int[] proximityCharsArray,
    int keyCount, int[] keyXCoordinates, int[] keyYCoordinates,
    int[] keyWidths, int[] keyHeights, int[] keyCharCodes,
    float[] sweetSpotCenterXs, float[] sweetSpotCenterYs, float[] sweetSpotRadii);

private static native void releaseProximityInfoNative(long nativeProximityInfo);

static { System.loadLibrary("jni_latinime"); }
```

- [ ] **Step 2: Build proximity data from current `Keyboard`**

Add a method to `ProximityInfo` that takes the current `Keyboard` object and builds the proximity arrays from its `Key` list. Call `setProximityInfoNative()` to get a handle.

Reference HeliBoard's `ProximityInfo.java` for the exact array construction.

- [ ] **Step 3: Pass proximity handle through to `Suggest`**

`Keyboard` creates `ProximityInfo` on layout. `KeyboardSwitcher` exposes it. `LatinIME` passes the handle when requesting suggestions.

- [ ] **Step 4: Build, install, and test**

```bash
JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug && ~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Test: Type with intentional near-misses (e.g., "thrn" for "then") — proximity correction should improve suggestions.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/keyboard/ProximityInfo.java app/src/main/java/com/keysink/inputmethod/keyboard/MainKeyboardView.java app/src/main/java/com/keysink/inputmethod/keyboard/Keyboard.java
git commit -m "feat: integrate ProximityInfo for touch correction"
```

---

### Task 16: Hide suggestion strip when not applicable

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/LatinIME.java`
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/suggestions/SuggestionStripView.kt`

- [ ] **Step 1: Hide strip for password fields**

In `LatinIME.onStartInput()` or `onStartInputView()`, check `EditorInfo.inputType`:

```java
boolean shouldShowStrip = !InputTypeUtils.isPasswordInputType(editorInfo.inputType)
    && !InputTypeUtils.isVisiblePasswordInputType(editorInfo.inputType);
mSuggestionStripView.setVisibility(shouldShowStrip ? View.VISIBLE : View.GONE);
```

- [ ] **Step 2: Hide strip for number/phone keyboards**

Check `KeyboardId.mElementId` — hide strip when in phone or number mode.

- [ ] **Step 3: Build, install, test**

Verify: strip hidden in password fields and phone dialers, visible in normal text fields.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/LatinIME.java app/src/main/java/com/keysink/inputmethod/latin/suggestions/SuggestionStripView.kt
git commit -m "feat: hide suggestion strip for passwords and number fields"
```

---

### Task 17: Final device testing + edge cases

**Files:** None new — bug fixes only.

- [ ] **Step 1: Test matrix on NoteAir 5C**

Test each scenario and fix issues found:

| Scenario | Expected |
|----------|----------|
| Type "the" + space | Commits "the" (already correct, no correction) |
| Type "teh" + space | Auto-corrects to "the" |
| Type "teh" + space + backspace | Undoes to "teh" |
| Type "hello" | Suggestion strip shows "hello" centered |
| Type in password field | No suggestion strip |
| Rotate device | Suggestion strip reappears correctly |
| Switch language/layout | Strip clears |
| Long text field | No performance lag |
| Auto-correct toggle off | Suggestions show, space commits as-typed |
| Type "I" + space | Stays "I" (single letter, no correction) |
| Type numbers | No suggestions |

- [ ] **Step 2: Fix any issues found**

- [ ] **Step 3: Final build and install**

```bash
JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug && ~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: address edge cases in suggestion pipeline"
```

---

## Summary

| Chunk | Tasks | What it delivers |
|-------|-------|-----------------|
| 1: NDK Build | 1-4 | Kotlin support, native C++ compiles, JNI paths updated |
| 2: Dictionary Pipeline | 5-9 | Core data classes, BinaryDictionary JNI, Suggest engine, English dict |
| 3: Suggestion Strip UI | 10-11 | E-ink suggestion strip layout + SuggestionStripView |
| 4: Integration | 12-14 | Settings toggle, InputLogic wiring, LatinIME glue |
| 5: Polish | 15-17 | Proximity correction, field-type hiding, edge case fixes |
