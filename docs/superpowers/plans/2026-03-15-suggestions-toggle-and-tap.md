# Suggestions Toggle & Tap-to-Accept Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove auto-correct-on-separator behavior, wire up tap-to-accept suggestions, rename the setting to "Show suggestions" with a toggle to disable the entire suggestion pipeline, and add unit tests.

**Architecture:** The suggestion strip already displays candidates and the tap handler already calls through to `InputLogic.onPickSuggestionManually` — but that method is a TODO. We remove the `commitCurrentAutoCorrection` path (auto-replace on space/punctuation), implement the pick-suggestion method (delete typed word, commit picked word, reset), simplify the strip layout, and gate the whole pipeline on a renamed `PREF_SHOW_SUGGESTIONS` setting. Tests use JUnit 5 + Mockito with `unitTests.isReturnDefaultValues = true` (to stub Android framework classes like `Looper` and `Handler`).

**Tech Stack:** Java, Kotlin, Android SharedPreferences, JUnit 5, Mockito

---

## Chunk 1: Test Infrastructure, Suggest Tests, and Remove Auto-Correct

### Task 1: Add test infrastructure

**Files:**
- Modify: `app/build.gradle` (add test dependencies and config)

- [ ] **Step 1: Add JUnit 5 and Mockito dependencies to `app/build.gradle`**

In the `dependencies` block, add:

```groovy
testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
testImplementation 'org.mockito:mockito-core:5.15.2'
testImplementation 'org.mockito.kotlin:mockito-kotlin:5.4.0'
```

In the `android` block, add:

```groovy
testOptions {
    unitTests.all {
        useJUnitPlatform()
    }
    unitTests.isReturnDefaultValues = true
}
```

The `isReturnDefaultValues = true` is critical — without it, `android.os.Looper.getMainLooper()` throws `RuntimeException` in JUnit tests, which crashes `InputLogic`'s constructor (it creates a `Handler` on the main looper at field initialization time).

- [ ] **Step 2: Create test source directories**

```bash
mkdir -p app/src/test/java/com/keysink/inputmethod/latin
mkdir -p app/src/test/java/com/keysink/inputmethod/latin/inputlogic
```

- [ ] **Step 3: Verify the build still compiles**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle
git commit -m "chore: add JUnit 5 and Mockito test dependencies"
```

---

### Task 2: Write Suggest tests (RED — tests first)

**Files:**
- Create: `app/src/test/java/com/keysink/inputmethod/latin/SuggestTest.kt`

Write tests that assert `mWillAutoCorrect` is always false and suggestions are ordered correctly. These tests will **pass against current code** for ordering, but the `mWillAutoCorrect` test will **fail** because the current `Suggest.kt` still sets it to true when score exceeds threshold. This is our RED test for Task 3.

- [ ] **Step 1: Write the test file**

Create `app/src/test/java/com/keysink/inputmethod/latin/SuggestTest.kt`:

```kotlin
package com.keysink.inputmethod.latin

import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.keysink.inputmethod.latin.dictionary.DictionaryFacilitator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.*

class SuggestTest {

    private lateinit var mockFacilitator: DictionaryFacilitator
    private lateinit var suggest: Suggest

    @BeforeEach
    fun setUp() {
        mockFacilitator = mock()
        suggest = Suggest(mockFacilitator)
    }

    @Test
    fun `getSuggestedWords never sets willAutoCorrect`() {
        // High-scoring correction that would have triggered auto-correct before
        val topSuggestion = SuggestedWordInfo(
            "hello", "", Int.MAX_VALUE, SuggestedWordInfo.KIND_CORRECTION, ""
        )
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(arrayListOf(topSuggestion))
        whenever(mockFacilitator.isValidWord(any())).thenReturn(false)

        val wordComposer = WordComposer()
        wordComposer.addCodePoint('h'.code, -1, -1)
        wordComposer.addCodePoint('e'.code, -1, -1)
        wordComposer.addCodePoint('l'.code, -1, -1)
        wordComposer.addCodePoint('o'.code, -1, -1)

        val result = suggest.getSuggestedWords(
            wordComposer, NgramContext.EMPTY_PREV_WORDS_INFO, 0L
        )

        assertFalse(result.mWillAutoCorrect, "mWillAutoCorrect should always be false")
    }

    @Test
    fun `typed word is at index 0`() {
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(ArrayList())
        whenever(mockFacilitator.isValidWord(any())).thenReturn(true)

        val wordComposer = WordComposer()
        wordComposer.addCodePoint('c'.code, -1, -1)
        wordComposer.addCodePoint('a'.code, -1, -1)
        wordComposer.addCodePoint('t'.code, -1, -1)

        val result = suggest.getSuggestedWords(
            wordComposer, NgramContext.EMPTY_PREV_WORDS_INFO, 0L
        )

        assertEquals("cat", result.getWord(0))
        assertTrue(result.mTypedWordValid)
    }

    @Test
    fun `suggestions follow typed word in results`() {
        val suggestion = SuggestedWordInfo(
            "hello", "", 200_000, SuggestedWordInfo.KIND_CORRECTION, ""
        )
        whenever(mockFacilitator.getSuggestionResults(any(), any(), any(), any()))
            .thenReturn(arrayListOf(suggestion))
        whenever(mockFacilitator.isValidWord(any())).thenReturn(false)

        val wordComposer = WordComposer()
        wordComposer.addCodePoint('h'.code, -1, -1)
        wordComposer.addCodePoint('e'.code, -1, -1)
        wordComposer.addCodePoint('l'.code, -1, -1)
        wordComposer.addCodePoint('o'.code, -1, -1)

        val result = suggest.getSuggestedWords(
            wordComposer, NgramContext.EMPTY_PREV_WORDS_INFO, 0L
        )

        assertTrue(result.size() >= 2, "Should have typed word + at least one suggestion")
        assertEquals("helo", result.getWord(0)) // typed word
        assertEquals("hello", result.getWord(1)) // suggestion
    }
}
```

Note: The current `getSuggestedWords` signature has 4 params (includes `isCorrectionEnabled`). The test calls it with 4 params to match the current API. After Task 3 removes that param, we update the test.

**Actually — the test above calls with 3 params, which won't compile against current code.** Write the test matching the current 4-param signature first:

```kotlin
val result = suggest.getSuggestedWords(
    wordComposer, NgramContext.EMPTY_PREV_WORDS_INFO, 0L, true
)
```

- [ ] **Step 2: Run the tests**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "*.SuggestTest" --info`
Expected: `willAutoCorrect` test FAILS (current code sets it to true for high-scoring corrections). Other tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/keysink/inputmethod/latin/SuggestTest.kt
git commit -m "test: add Suggest unit tests (willAutoCorrect test is RED)"
```

---

### Task 3: Remove auto-correct logic from Suggest

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/Suggest.kt`
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java:627-631`
- Modify: `app/src/test/java/com/keysink/inputmethod/latin/SuggestTest.kt` (update call sites)

- [ ] **Step 1: Remove auto-correct determination from Suggest.kt**

In `Suggest.kt`, delete the `willAutoCorrect` variable and logic (lines 64-76). Change line 92 from `mWillAutoCorrect = willAutoCorrect` to `mWillAutoCorrect = false`.

The block to delete (lines 63-76):

```kotlin
        // Determine auto-correction
        var willAutoCorrect = false
        if (isCorrectionEnabled
            && rawSuggestions.isNotEmpty()
            && typedWordString.isNotEmpty()
            && !typedWordValid
        ) {
            val topSuggestion = rawSuggestions[0]
            if (topSuggestion.mScore > AUTO_CORRECT_THRESHOLD
                && topSuggestion.isAppropriateForAutoCorrection()
            ) {
                willAutoCorrect = true
            }
        }
```

Replace with nothing — just let the code flow to the final list assembly.

- [ ] **Step 2: Remove `AUTO_CORRECT_THRESHOLD` constant**

Delete from companion object: `private const val AUTO_CORRECT_THRESHOLD = 100_000`

- [ ] **Step 3: Remove `KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION` from edit-distance corrections**

In `Suggest.kt` line 128, change:
```kotlin
SuggestedWordInfo.KIND_CORRECTION or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
```
to:
```kotlin
SuggestedWordInfo.KIND_CORRECTION,
```

- [ ] **Step 4: Remove `isCorrectionEnabled` parameter**

Change the `getSuggestedWords` signature to remove the 4th parameter. Update the edit-distance guard from `if (isCorrectionEnabled && typedWordString.length >= 2)` to `if (typedWordString.length >= 2)`.

- [ ] **Step 5: Update caller in InputLogic.java**

In `InputLogic.java` lines 627-632, remove the `true` argument:

```java
final SuggestedWords words = mSuggest.getSuggestedWords(
        composerSnapshot,
        NgramContext.EMPTY_PREV_WORDS_INFO,
        proxInfo
);
```

- [ ] **Step 6: Update test call sites in SuggestTest.kt**

Change all `suggest.getSuggestedWords(..., true)` calls to `suggest.getSuggestedWords(...)` (3 params).

- [ ] **Step 7: Run the tests — should go GREEN**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "*.SuggestTest" --info`
Expected: ALL PASS (including the `willAutoCorrect` test)

- [ ] **Step 8: Verify the build compiles**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/Suggest.kt \
       app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java \
       app/src/test/java/com/keysink/inputmethod/latin/SuggestTest.kt
git commit -m "refactor: remove auto-correct logic from Suggest"
```

---

### Task 4: Remove auto-correct-on-separator from InputLogic

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java:344-373`

- [ ] **Step 1: Remove the `commitCurrentAutoCorrection()` call in `handleSeparatorEvent`**

In `InputLogic.java`, remove the `commitCurrentAutoCorrection();` call from `handleSeparatorEvent` (line 346). The method becomes:

```java
private void handleSeparatorEvent(final Event event, final InputTransaction inputTransaction) {
    if (mWordComposer.isComposingWord()) {
        mWordComposer.reset();
        clearSuggestionStrip();
    }
    sendKeyCodePoint(event.mCodePoint);

    inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW);
}
```

- [ ] **Step 2: Delete the `commitCurrentAutoCorrection()` method entirely**

Remove the method and its Javadoc (lines 355-373):

```java
/**
 * If auto-correct is active, replace the typed word with the correction.
 * Called just before committing a separator (space, punctuation).
 */
private void commitCurrentAutoCorrection() {
    ...
}
```

- [ ] **Step 3: Verify the build compiles**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java
git commit -m "fix: remove auto-correct-on-separator from InputLogic"
```

---

### Task 5: Simplify SuggestionStripView layout

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/suggestions/SuggestionStripView.kt:43-76`

With `mWillAutoCorrect` always false, the auto-correct branches in `setSuggestions` and `onClick` are dead code. The layout is now always: **center = best suggestion (index 1)**, **left = typed word (index 0)**, **right = 2nd suggestion (index 2)**.

Note: `MainKeyboardView.java:handleSuggestionStripTap` already uses this layout — no changes needed there.

- [ ] **Step 1: Simplify `setSuggestions` in SuggestionStripView.kt**

Replace the method (lines 43-57) with:

```kotlin
fun setSuggestions(suggestedWords: SuggestedWords) {
    this.suggestedWords = suggestedWords
    val count = suggestedWords.size()

    // Center: best suggestion if available, otherwise typed word
    // Left: typed word (when suggestions exist)
    // Right: 2nd suggestion
    if (count > 1) {
        centerView.text = suggestedWords.getWord(1)
        leftView.text = suggestedWords.getWord(0)
        rightView.text = if (count > 2) suggestedWords.getWord(2) else ""
    } else {
        centerView.text = if (count > 0) suggestedWords.getWord(0) else ""
        leftView.text = ""
        rightView.text = ""
    }
}
```

- [ ] **Step 2: Simplify `onClick` in SuggestionStripView.kt**

Replace the method (lines 66-76) with:

```kotlin
override fun onClick(v: View) {
    val index = when (v.id) {
        R.id.suggestion_center -> if (suggestedWords.size() > 1) 1 else 0
        R.id.suggestion_left -> 0
        R.id.suggestion_right -> 2
        else -> return
    }
    if (index < suggestedWords.size()) {
        listener?.pickSuggestionManually(suggestedWords.getInfo(index))
    }
}
```

- [ ] **Step 3: Verify the build compiles**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/suggestions/SuggestionStripView.kt
git commit -m "refactor: simplify suggestion strip layout, remove auto-correct reordering"
```

---

## Chunk 2: Tap-to-Accept, Settings Toggle, and Verification

### Task 6: Implement `onPickSuggestionManually` (TDD)

**Files:**
- Create: `app/src/test/java/com/keysink/inputmethod/latin/inputlogic/InputLogicPickSuggestionTest.kt`
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java:649-652`
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/LatinIME.java:898-900`

When the user taps a suggestion:
1. Delete the typed word's characters from the editor (committed char-by-char — Boox constraint)
2. Commit the picked suggestion word
3. Commit a trailing space
4. Reset word composer and clear suggestion strip

Note: `mConnection` is `public final` (declared on line 57 of InputLogic.java), so we can access it directly in tests. However, it's a real `RichInputConnection` instance created in the constructor, not a mock. With `unitTests.isReturnDefaultValues = true`, its methods will be no-ops (returning default values). We inject a mock via `sun.misc.Unsafe` to replace the final field and verify actual method calls.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/keysink/inputmethod/latin/inputlogic/InputLogicPickSuggestionTest.kt`:

```kotlin
package com.keysink.inputmethod.latin.inputlogic

import com.keysink.inputmethod.latin.LatinIME
import com.keysink.inputmethod.latin.RichInputConnection
import com.keysink.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.keysink.inputmethod.latin.WordComposer
import com.keysink.inputmethod.latin.settings.SettingsValues
import com.keysink.inputmethod.latin.suggestions.SuggestionStripViewAccessor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import sun.misc.Unsafe

class InputLogicPickSuggestionTest {

    private lateinit var inputLogic: InputLogic
    private lateinit var mockConnection: RichInputConnection
    private lateinit var mockAccessor: SuggestionStripViewAccessor
    private lateinit var mockSettings: SettingsValues

    @BeforeEach
    fun setUp() {
        val mockLatinIME: LatinIME = mock()
        mockConnection = mock()
        mockAccessor = mock()
        mockSettings = mock()

        inputLogic = InputLogic(mockLatinIME)
        inputLogic.initSuggest(null, mockAccessor)

        // Replace the final mConnection field with our mock using Unsafe
        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as Unsafe
        val connectionField = InputLogic::class.java.getDeclaredField("mConnection")
        val offset = unsafe.objectFieldOffset(connectionField)
        unsafe.putObject(inputLogic, offset, mockConnection)
    }

    private fun getWordComposer(): WordComposer {
        val field = InputLogic::class.java.getDeclaredField("mWordComposer")
        field.isAccessible = true
        return field.get(inputLogic) as WordComposer
    }

    @Test
    fun `picking suggestion deletes typed word and commits picked word plus space`() {
        val wordComposer = getWordComposer()
        wordComposer.addCodePoint('h'.code, -1, -1)
        wordComposer.addCodePoint('e'.code, -1, -1)
        wordComposer.addCodePoint('l'.code, -1, -1)
        wordComposer.addCodePoint('o'.code, -1, -1)

        val suggestionInfo = SuggestedWordInfo(
            "hello", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION, ""
        )

        inputLogic.onPickSuggestionManually(suggestionInfo, mockSettings)

        // Should delete 4 chars ("helo"), commit "hello", then commit " "
        val inOrder = inOrder(mockConnection)
        inOrder.verify(mockConnection).deleteTextBeforeCursor(4)
        inOrder.verify(mockConnection).commitText(eq("hello"), eq(1))
        inOrder.verify(mockConnection).commitText(eq(" "), eq(1))

        // Word composer should be reset
        assert(!wordComposer.isComposingWord) { "Word composer should be reset" }
    }

    @Test
    fun `picking typed word does not delete or re-commit, just adds space`() {
        val wordComposer = getWordComposer()
        wordComposer.addCodePoint('c'.code, -1, -1)
        wordComposer.addCodePoint('a'.code, -1, -1)
        wordComposer.addCodePoint('t'.code, -1, -1)

        val typedWordInfo = SuggestedWordInfo(
            "cat", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED, ""
        )

        inputLogic.onPickSuggestionManually(typedWordInfo, mockSettings)

        // Should NOT call deleteTextBeforeCursor (word matches what's in editor)
        verify(mockConnection, never()).deleteTextBeforeCursor(any())
        // Should commit just a space
        verify(mockConnection).commitText(eq(" "), eq(1))

        assert(!wordComposer.isComposingWord) { "Word composer should be reset" }
    }

    @Test
    fun `picking suggestion clears the suggestion strip`() {
        val wordComposer = getWordComposer()
        wordComposer.addCodePoint('t'.code, -1, -1)

        val info = SuggestedWordInfo(
            "the", "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION, ""
        )

        inputLogic.onPickSuggestionManually(info, mockSettings)

        verify(mockAccessor).setNeutralSuggestionStrip()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "*.InputLogicPickSuggestionTest" --info`
Expected: Tests FAIL because `onPickSuggestionManually` is a TODO no-op.

- [ ] **Step 3: Implement `onPickSuggestionManually` in InputLogic.java**

Replace the TODO method (lines 649-652) with:

```java
public void onPickSuggestionManually(final SuggestedWords.SuggestedWordInfo wordInfo,
        final SettingsValues settingsValues) {
    final String typedWord = mWordComposer.getTypedWord();
    final String pickedWord = wordInfo.getWord();

    // If the picked word differs from what was typed, replace it
    if (!pickedWord.equals(typedWord) && !typedWord.isEmpty()) {
        mConnection.deleteTextBeforeCursor(typedWord.length());
        mConnection.commitText(pickedWord, 1);
    }

    // Commit a trailing space after the picked word
    mConnection.commitText(" ", 1);

    // Reset state
    mWordComposer.reset();
    clearSuggestionStrip();
}
```

- [ ] **Step 4: Wire `LatinIME.pickSuggestionManually` to InputLogic**

In `LatinIME.java`, replace lines 898-900. First check the field name — look for `mInputLogic` in LatinIME.java. Replace:

```java
@Override
public void pickSuggestionManually(final SuggestedWords.SuggestedWordInfo wordInfo) {
    // TODO: wire to InputLogic
}
```

with:

```java
@Override
public void pickSuggestionManually(final SuggestedWords.SuggestedWordInfo wordInfo) {
    mInputLogic.onPickSuggestionManually(wordInfo, Settings.getInstance().getCurrent());
}
```

Ensure `Settings` is imported (it should already be — check existing imports).

- [ ] **Step 5: Run tests — should go GREEN**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "*.InputLogicPickSuggestionTest" --info`
Expected: ALL PASS

- [ ] **Step 6: Run all tests**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --info`
Expected: ALL PASS

- [ ] **Step 7: Verify the build compiles**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java \
       app/src/main/java/com/keysink/inputmethod/latin/LatinIME.java \
       app/src/test/java/com/keysink/inputmethod/latin/inputlogic/InputLogicPickSuggestionTest.kt
git commit -m "feat: implement tap-to-accept suggestion picking"
```

---

### Task 7: Rename setting from "Auto-correction" to "Show suggestions"

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/settings/Settings.java:67`
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/settings/SettingsValues.java:52,95`
- Modify: `app/src/main/res/xml/prefs_screen_preferences.xml:26-30`
- Modify: `app/src/main/res/values/strings.xml:123-124`

We keep the same SharedPreferences key value (`"pref_auto_correct"`) for backwards compatibility with existing installs, but rename the Java constant and user-facing strings.

References to update (exhaustive — no other production code references exist):
- `Settings.java:67` — constant declaration
- `SettingsValues.java:52` — field declaration
- `SettingsValues.java:95` — field initialization

- [ ] **Step 1: Rename the constant in Settings.java**

Change line 67 from:
```java
public static final String PREF_AUTO_CORRECT = "pref_auto_correct";
```
to:
```java
public static final String PREF_SHOW_SUGGESTIONS = "pref_auto_correct";
```

Note: the string value stays `"pref_auto_correct"` so existing user preferences are preserved.

- [ ] **Step 2: Rename the field in SettingsValues.java**

Line 52: change `public final boolean mAutoCorrectionEnabled;` to `public final boolean mShowSuggestions;`

Line 95: change `mAutoCorrectionEnabled = prefs.getBoolean(Settings.PREF_AUTO_CORRECT, true);` to `mShowSuggestions = prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true);`

- [ ] **Step 3: Update the preference XML**

In `prefs_screen_preferences.xml`, change lines 27-28 from:
```xml
android:title="@string/auto_correct"
android:summary="@string/auto_correct_summary"
```
to:
```xml
android:title="@string/show_suggestions"
android:summary="@string/show_suggestions_summary"
```

(The `android:key` stays `"pref_auto_correct"` — it matches the constant's value.)

- [ ] **Step 4: Update the string resources**

In `strings.xml`, change lines 123-124 from:
```xml
<string name="auto_correct">Auto-correction</string>
<string name="auto_correct_summary">Automatically correct misspelled words</string>
```
to:
```xml
<string name="show_suggestions">Show suggestions</string>
<string name="show_suggestions_summary">Show word suggestions while typing</string>
```

- [ ] **Step 5: Verify the build compiles**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/settings/Settings.java \
       app/src/main/java/com/keysink/inputmethod/latin/settings/SettingsValues.java \
       app/src/main/res/xml/prefs_screen_preferences.xml \
       app/src/main/res/values/strings.xml
git commit -m "refactor: rename auto-correct setting to 'Show suggestions'"
```

---

### Task 8: Gate suggestion pipeline on the setting

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java:613-616`

When `mShowSuggestions` is false, `requestSuggestionsAsync` should be a no-op. Rather than stashing `SettingsValues` as a field (which introduces race conditions with the background executor and misses backspace-triggered requests), fetch the current settings directly from the `Settings` singleton.

- [ ] **Step 1: Gate `requestSuggestionsAsync` on the setting**

At the top of `requestSuggestionsAsync` (line 614), change:
```java
if (mSuggest == null || !mWordComposer.isComposingWord()) {
    return;
}
```
to:
```java
final SettingsValues currentSettings = Settings.getInstance().getCurrent();
if (mSuggest == null || !mWordComposer.isComposingWord()
        || (currentSettings != null && !currentSettings.mShowSuggestions)) {
    return;
}
```

Add the import for `Settings` if not already present:
```java
import com.keysink.inputmethod.latin.settings.Settings;
```

(Check existing imports — `SettingsValues` is already imported on line 40, but `Settings` may not be.)

- [ ] **Step 2: Verify the build compiles**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all tests**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --info`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java
git commit -m "feat: gate suggestion pipeline on Show Suggestions setting"
```

---

### Task 9: Verify on device

Manual verification on Boox NoteAir 5C.

- [ ] **Step 1: Build and install**

```bash
JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Verify suggestions appear while typing**

Open any text editor on the Boox. Type "helo" — suggestion strip should show "helo" (left) and "hello" (center) as a suggestion.

- [ ] **Step 3: Verify tap-to-accept works**

Tap "hello" in the suggestion strip. "helo" should be replaced with "hello " (with trailing space).

- [ ] **Step 4: Verify space does NOT auto-correct**

Type "helo" then press space. The editor should contain "helo " — NOT "hello ".

- [ ] **Step 5: Verify the setting toggle works**

Go to Settings → Keys, Ink → Show suggestions → toggle OFF. Type "helo" — no suggestion strip should appear. Toggle back ON — suggestions should return.

- [ ] **Step 6: Verify tapping typed word just adds space**

Type "cat" (a valid word). Tap "cat" in the suggestion strip. Editor should have "cat " — no replacement, just a trailing space.
