# Voice Input (v0.3) Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add tap-to-toggle voice-to-text input using whisper.cpp (base.en), with model download on first use, respecting all Boox e-ink constraints.

**Architecture:** New `VoiceInputController` (Kotlin) orchestrates a 5-state machine across `AudioRecorder`, `WhisperEngine` (JNI), and `ModelDownloadManager`. Recording/transcription run on background threads. Visual feedback drawn on `MainKeyboardView` canvas in the suggestion strip area. Settings flow handles RECORD_AUDIO permission and model download via `VoiceInputSettingsFragment`.

**Tech Stack:** Kotlin (new code), Java (existing code modifications), C++ (whisper.cpp JNI bridge), ndk-build (Android.mk), JUnit 5 + Mockito (tests)

**Spec:** `docs/superpowers/specs/2026-03-15-voice-input-design.md`

---

## Chunk 1: Foundation — Constants, State Enum, Mic Key Layout

### Task 1: Add `CODE_VOICE_INPUT` constant

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/common/Constants.java:106` (after `CODE_UNSPECIFIED`)

- [ ] **Step 1: Add the constant**

In `Constants.java`, after `CODE_UNSPECIFIED = -13` (line 106), add:

```java
public static final int CODE_VOICE_INPUT = -14;
```

- [ ] **Step 2: Add debug label in `printableCode()`**

In the `printableCode()` method (around line 112-135), add a case to the switch:

```java
case CODE_VOICE_INPUT: return "voice";
```

- [ ] **Step 3: Build to verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/common/Constants.java
git commit -m "feat: add CODE_VOICE_INPUT constant (-14)"
```

---

### Task 2: Create `VoiceInputState` enum

**Files:**
- Create: `app/src/main/java/com/keysink/inputmethod/latin/voice/VoiceInputState.kt`
- Test: `app/src/test/java/com/keysink/inputmethod/latin/voice/VoiceInputStateTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.keysink.inputmethod.latin.voice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VoiceInputStateTest {

    @Test
    fun `IDLE is the initial default state`() {
        assertEquals(VoiceInputState.IDLE, VoiceInputState.values()[0])
    }

    @Test
    fun `all five states exist`() {
        val states = VoiceInputState.values()
        assertEquals(5, states.size)
        assertTrue(states.contains(VoiceInputState.IDLE))
        assertTrue(states.contains(VoiceInputState.NOT_READY))
        assertTrue(states.contains(VoiceInputState.RECORDING))
        assertTrue(states.contains(VoiceInputState.TRANSCRIBING))
        assertTrue(states.contains(VoiceInputState.ERROR))
    }

    @Test
    fun `showsMicIcon returns true for IDLE, NOT_READY, TRANSCRIBING, ERROR`() {
        assertTrue(VoiceInputState.IDLE.showsMicIcon)
        assertTrue(VoiceInputState.NOT_READY.showsMicIcon)
        assertTrue(VoiceInputState.TRANSCRIBING.showsMicIcon)
        assertTrue(VoiceInputState.ERROR.showsMicIcon)
    }

    @Test
    fun `showsMicIcon returns false for RECORDING`() {
        assertFalse(VoiceInputState.RECORDING.showsMicIcon)
    }

    @Test
    fun `statusText returns correct labels`() {
        assertNull(VoiceInputState.IDLE.statusText)
        assertNull(VoiceInputState.NOT_READY.statusText)
        assertEquals("Recording", VoiceInputState.RECORDING.statusText)
        assertEquals("Transcribing", VoiceInputState.TRANSCRIBING.statusText)
        assertNull(VoiceInputState.ERROR.statusText)
    }

    @Test
    fun `showsAnimatedDots returns true only for RECORDING and TRANSCRIBING`() {
        assertFalse(VoiceInputState.IDLE.showsAnimatedDots)
        assertFalse(VoiceInputState.NOT_READY.showsAnimatedDots)
        assertTrue(VoiceInputState.RECORDING.showsAnimatedDots)
        assertTrue(VoiceInputState.TRANSCRIBING.showsAnimatedDots)
        assertFalse(VoiceInputState.ERROR.showsAnimatedDots)
    }

    @Test
    fun `hidesSuggestions returns true for RECORDING, TRANSCRIBING, ERROR`() {
        assertFalse(VoiceInputState.IDLE.hidesSuggestions)
        assertFalse(VoiceInputState.NOT_READY.hidesSuggestions)
        assertTrue(VoiceInputState.RECORDING.hidesSuggestions)
        assertTrue(VoiceInputState.TRANSCRIBING.hidesSuggestions)
        assertTrue(VoiceInputState.ERROR.hidesSuggestions)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "com.keysink.inputmethod.latin.voice.VoiceInputStateTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Implement the enum**

```kotlin
package com.keysink.inputmethod.latin.voice

enum class VoiceInputState(
    val showsMicIcon: Boolean,
    val statusText: String?,
    val showsAnimatedDots: Boolean,
    val hidesSuggestions: Boolean
) {
    IDLE(
        showsMicIcon = true,
        statusText = null,
        showsAnimatedDots = false,
        hidesSuggestions = false
    ),
    NOT_READY(
        showsMicIcon = true,
        statusText = null,
        showsAnimatedDots = false,
        hidesSuggestions = false
    ),
    RECORDING(
        showsMicIcon = false,
        statusText = "Recording",
        showsAnimatedDots = true,
        hidesSuggestions = true
    ),
    TRANSCRIBING(
        showsMicIcon = true,
        statusText = "Transcribing",
        showsAnimatedDots = true,
        hidesSuggestions = true
    ),
    ERROR(
        showsMicIcon = true,
        statusText = null,
        showsAnimatedDots = false,
        hidesSuggestions = true
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "com.keysink.inputmethod.latin.voice.VoiceInputStateTest" --info`
Expected: All 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/voice/VoiceInputState.kt \
       app/src/test/java/com/keysink/inputmethod/latin/voice/VoiceInputStateTest.kt
git commit -m "feat: add VoiceInputState enum with display properties"
```

---

### Task 3: Add mic key to keyboard layout

**Files:**
- Create: `app/src/main/res/drawable/sym_keyboard_mic.xml`
- Create: `app/src/main/res/drawable/sym_keyboard_stop.xml`
- Create: `app/src/main/res/xml/key_mic.xml`
- Modify: `app/src/main/res/xml/key_styles_common.xml` (add `micKeyStyle`)
- Modify: `app/src/main/res/xml/row_qwerty4.xml` (add mic key)
- Modify: `app/src/main/res/xml-sw600dp/row_qwerty4.xml` (add mic key if exists)
- Modify: `app/src/main/java/com/keysink/inputmethod/keyboard/internal/KeyboardIconsSet.java` (register icons)

**Scope note:** Mic key is added only to the QWERTY alphabet bottom row (`row_qwerty4.xml`). It does NOT appear in symbols keyboards (`row_symbols4.xml`, `row_symbols_shift4.xml`) or non-QWERTY layouts (Azerty, Colemak, etc.). Other layouts can be added in a follow-up.

- [ ] **Step 1: Create mic icon drawable**

Create `app/src/main/res/drawable/sym_keyboard_mic.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <!-- Microphone icon: simple outline mic -->
    <path
        android:fillColor="@android:color/black"
        android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6C9,12.66 10.34,14 12,14zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.49 6,-3.31 6,-6.72H17.3z"/>
</vector>
```

- [ ] **Step 2: Create stop icon drawable**

Create `app/src/main/res/drawable/sym_keyboard_stop.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <!-- Stop icon: filled square -->
    <path
        android:fillColor="@android:color/black"
        android:pathData="M6,6h12v12H6z"/>
</vector>
```

- [ ] **Step 3: Register icons in `KeyboardIconsSet.java`**

In `KeyboardIconsSet.java`, add constants alongside the existing `NAME_*` constants:

```java
public static final String NAME_MIC_KEY = "mic_key";
public static final String NAME_STOP_KEY = "stop_key";
```

Add entries to the `NAMES_AND_ATTR_IDS` array:

```java
NAME_MIC_KEY,  R.drawable.sym_keyboard_mic,
NAME_STOP_KEY, R.drawable.sym_keyboard_stop,
```

- [ ] **Step 4: Add mic key style to `key_styles_common.xml`**

Add after the existing key style definitions (near the end, before closing `</merge>`):

```xml
<key-style
    latin:styleName="micKeyStyle"
    latin:keySpec="!icon/mic_key|!code/key_voice_input"
    latin:keyActionFlags="noKeyPreview"
    latin:backgroundType="functional" />
```

Note: `!code/key_voice_input` must be mapped to `Constants.CODE_VOICE_INPUT` (-14) in `KeyboardCodesSet.java` — see Step 8 below.

- [ ] **Step 5: Create `key_mic.xml`**

Create `app/src/main/res/xml/key_mic.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:latin="http://schemas.android.com/apk/res-auto">
    <Key
        latin:keyStyle="micKeyStyle"
        latin:keyWidth="10%p" />
</merge>
```

- [ ] **Step 6: Add mic key to `row_qwerty4.xml`**

Insert `<include latin:keyboardLayout="@xml/key_mic" />` between the period include and the enter key:

```xml
<Row latin:keyWidth="10%p">
    <Key latin:keyStyle="toSymbolKeyStyle" latin:keyWidth="15%p" />
    <include latin:keyboardLayout="@xml/key_comma" />
    <include latin:keyboardLayout="@xml/key_space_5kw" />
    <include latin:keyboardLayout="@xml/key_period" />
    <include latin:keyboardLayout="@xml/key_mic" />
    <Key latin:keyStyle="enterKeyStyle" latin:keyWidth="fillRight" />
</Row>
```

- [ ] **Step 7: Add mic key to tablet variant `xml-sw600dp/row_qwerty4.xml`**

If it exists, add the mic key include between period and the spacer/enter key, same pattern.

- [ ] **Step 8: Register the key code in `KeyboardCodesSet.java`**

File: `app/src/main/java/com/keysink/inputmethod/keyboard/internal/KeyboardCodesSet.java`

**IMPORTANT:** The `ID_TO_NAME` and `DEFAULT` arrays must have matching lengths — each name at index N maps to the code at `DEFAULT[N]`. There is a pre-existing bug: `ID_TO_NAME` has two dead entries (`"key_left"`, `"key_right"`) with no corresponding `DEFAULT` entries. Fix this while adding our entry.

Remove the dead entries from `ID_TO_NAME` and add `"key_voice_input"`:

```java
private static final String[] ID_TO_NAME = {
    "key_tab",
    "key_enter",
    "key_space",
    "key_shift",
    "key_capslock",
    "key_switch_alpha_symbol",
    "key_output_text",
    "key_delete",
    "key_settings",
    "key_paste",
    "key_action_next",
    "key_action_previous",
    "key_shift_enter",
    "key_language_switch",
    "key_unspecified",
    "key_voice_input",       // NEW — must match DEFAULT index
};
```

Add `Constants.CODE_VOICE_INPUT` at the end of `DEFAULT`:

```java
private static final int[] DEFAULT = {
    Constants.CODE_TAB,
    Constants.CODE_ENTER,
    Constants.CODE_SPACE,
    Constants.CODE_SHIFT,
    Constants.CODE_CAPSLOCK,
    Constants.CODE_SWITCH_ALPHA_SYMBOL,
    Constants.CODE_OUTPUT_TEXT,
    Constants.CODE_DELETE,
    Constants.CODE_SETTINGS,
    Constants.CODE_PASTE,
    Constants.CODE_ACTION_NEXT,
    Constants.CODE_ACTION_PREVIOUS,
    Constants.CODE_SHIFT_ENTER,
    Constants.CODE_LANGUAGE_SWITCH,
    Constants.CODE_UNSPECIFIED,
    Constants.CODE_VOICE_INPUT,  // NEW — index 15, matches ID_TO_NAME
};
```

Both arrays are now 16 entries each. Verify `ID_TO_NAME.length == DEFAULT.length`.

- [ ] **Step 9: Build and verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add app/src/main/res/drawable/sym_keyboard_mic.xml \
       app/src/main/res/drawable/sym_keyboard_stop.xml \
       app/src/main/res/xml/key_mic.xml \
       app/src/main/res/xml/key_styles_common.xml \
       app/src/main/res/xml/row_qwerty4.xml \
       app/src/main/java/com/keysink/inputmethod/keyboard/internal/KeyboardIconsSet.java \
       app/src/main/java/com/keysink/inputmethod/keyboard/internal/KeyboardCodesSet.java
git commit -m "feat: add mic key to bottom row with icon and key style"
```

---

## Chunk 2: Audio Recording & Native Whisper Layer

### Task 4: Implement `AudioRecorder`

**Files:**
- Create: `app/src/main/java/com/keysink/inputmethod/latin/voice/AudioRecorder.kt`
- Test: `app/src/test/java/com/keysink/inputmethod/latin/voice/AudioRecorderTest.kt`

- [ ] **Step 1: Write the tests**

Tests focus on the pure-logic parts (conversion, buffer management) since `AudioRecord` requires mocking:

```kotlin
package com.keysink.inputmethod.latin.voice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AudioRecorderTest {

    @Test
    fun `convertToFloat normalizes short samples to -1 to 1 range`() {
        val shorts = shortArrayOf(0, Short.MAX_VALUE, Short.MIN_VALUE, 16384)
        val floats = AudioRecorder.convertToFloat(shorts, shorts.size)
        assertEquals(0f, floats[0], 0.001f)
        assertEquals(1f, floats[1], 0.001f)
        assertEquals(-1f, floats[2], 0.001f)
        assertEquals(0.5f, floats[3], 0.01f)
    }

    @Test
    fun `convertToFloat respects sampleCount parameter`() {
        val shorts = shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE)
        val floats = AudioRecorder.convertToFloat(shorts, 2)
        assertEquals(2, floats.size)
    }

    @Test
    fun `convertToFloat handles empty input`() {
        val floats = AudioRecorder.convertToFloat(shortArrayOf(), 0)
        assertEquals(0, floats.size)
    }

    @Test
    fun `SAMPLE_RATE is 16000`() {
        assertEquals(16000, AudioRecorder.SAMPLE_RATE)
    }

    @Test
    fun `MAX_DURATION_SECONDS is 120`() {
        assertEquals(120, AudioRecorder.MAX_DURATION_SECONDS)
    }

    @Test
    fun `maxSamples equals SAMPLE_RATE times MAX_DURATION_SECONDS`() {
        assertEquals(
            AudioRecorder.SAMPLE_RATE * AudioRecorder.MAX_DURATION_SECONDS,
            AudioRecorder.MAX_SAMPLES
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "com.keysink.inputmethod.latin.voice.AudioRecorderTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Implement `AudioRecorder.kt`**

```kotlin
package com.keysink.inputmethod.latin.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorder {

    interface Callback {
        fun onRecordingComplete(audioData: FloatArray)
        fun onRecordingError(message: String)
        fun onMaxDurationReached()
    }

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun startRecording(callback: Callback) {
        if (isRecording.get()) return

        executor.execute {
            try {
                val bufferSize = maxOf(
                    AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ),
                    SAMPLE_RATE * 2 // at least 1 second buffer
                )

                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    callback.onRecordingError("Mic unavailable")
                    return@execute
                }

                audioRecord = record
                record.startRecording()
                isRecording.set(true)

                val allSamples = ShortArray(MAX_SAMPLES)
                var totalSamples = 0
                val readBuffer = ShortArray(bufferSize / 2)

                while (isRecording.get() && totalSamples < MAX_SAMPLES) {
                    val read = record.read(readBuffer, 0, readBuffer.size)
                    if (read > 0) {
                        val remaining = MAX_SAMPLES - totalSamples
                        val toCopy = minOf(read, remaining)
                        System.arraycopy(readBuffer, 0, allSamples, totalSamples, toCopy)
                        totalSamples += toCopy
                    } else if (read < 0) {
                        isRecording.set(false)
                        record.stop()
                        record.release()
                        audioRecord = null
                        callback.onRecordingError("Recording failed")
                        return@execute
                    }
                }

                val hitMax = totalSamples >= MAX_SAMPLES
                record.stop()
                record.release()
                audioRecord = null
                isRecording.set(false)

                if (hitMax) {
                    callback.onMaxDurationReached()
                }

                val floatData = convertToFloat(allSamples, totalSamples)
                callback.onRecordingComplete(floatData)

            } catch (e: SecurityException) {
                isRecording.set(false)
                callback.onRecordingError("Mic unavailable")
            } catch (e: Exception) {
                isRecording.set(false)
                callback.onRecordingError("Recording failed")
            }
        }
    }

    fun stopRecording() {
        isRecording.set(false)
    }

    fun release() {
        stopRecording()
        audioRecord?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        audioRecord = null
        executor.shutdown()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_DURATION_SECONDS = 120
        const val MAX_SAMPLES = SAMPLE_RATE * MAX_DURATION_SECONDS

        fun convertToFloat(shorts: ShortArray, sampleCount: Int): FloatArray {
            val floats = FloatArray(sampleCount)
            for (i in 0 until sampleCount) {
                floats[i] = shorts[i].toFloat() / Short.MAX_VALUE.toFloat()
            }
            return floats
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "com.keysink.inputmethod.latin.voice.AudioRecorderTest" --info`
Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/voice/AudioRecorder.kt \
       app/src/test/java/com/keysink/inputmethod/latin/voice/AudioRecorderTest.kt
git commit -m "feat: add AudioRecorder with 16kHz PCM capture and 120s max"
```

---

### Task 5: Vendor whisper.cpp and set up native build

**Files:**
- Create: `app/src/main/jni/whisper/` directory with whisper.cpp sources
- Create: `app/src/main/jni/whisper/Android.mk`
- Create: `app/src/main/jni/whisper/whisper_jni.cpp`
- Create: `app/src/main/jni/whisper/whisper_jni.h`
- Modify: `app/src/main/jni/Application.mk` (update ABI targets)

- [ ] **Step 1: Clone whisper.cpp and copy required sources**

```bash
cd /tmp
git clone --depth 1 https://github.com/ggerganov/whisper.cpp.git
mkdir -p app/src/main/jni/whisper/src
cp /tmp/whisper.cpp/src/whisper.cpp app/src/main/jni/whisper/src/
cp /tmp/whisper.cpp/include/whisper.h app/src/main/jni/whisper/
cp /tmp/whisper.cpp/ggml/src/*.c app/src/main/jni/whisper/src/
cp /tmp/whisper.cpp/ggml/src/*.cpp app/src/main/jni/whisper/src/
cp -r /tmp/whisper.cpp/ggml/include/ app/src/main/jni/whisper/ggml-include/
```

Note: The exact files needed depend on the whisper.cpp version. Check the whisper.cpp Android example (`examples/whisper.android/`) for the canonical list of required source files and includes. Copy exactly what's needed — no more.

- [ ] **Step 2: Create `whisper/Android.mk`**

```makefile
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := whisper_jni

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/ggml-include

# List exact source files needed (check whisper.cpp Android example)
LOCAL_SRC_FILES := \
    src/whisper.cpp \
    src/ggml.c \
    src/ggml-alloc.c \
    src/ggml-backend.c \
    src/ggml-cpu.c \
    whisper_jni.cpp

LOCAL_CFLAGS := -O3 -DNDEBUG -D_XOPEN_SOURCE=600
LOCAL_CPPFLAGS := -std=c++17
LOCAL_LDLIBS := -llog -landroid

include $(BUILD_SHARED_LIBRARY)
```

Note: The exact ggml source files required will vary by whisper.cpp version. Consult the whisper.cpp `CMakeLists.txt` or their Android sample to get the definitive list.

- [ ] **Step 3: Create `whisper_jni.cpp`**

```cpp
#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_keysink_inputmethod_latin_voice_WhisperEngine_loadModelNative(
        JNIEnv *env, jclass /*clazz*/, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);

    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (ctx == nullptr) {
        LOGE("Failed to load model");
        return 0;
    }

    LOGI("Model loaded successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_keysink_inputmethod_latin_voice_WhisperEngine_transcribeNative(
        JNIEnv *env, jclass /*clazz*/, jlong contextPtr, jfloatArray audioData) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }

    jfloat *audio = env->GetFloatArrayElements(audioData, nullptr);
    jsize audioLength = env->GetArrayLength(audioData);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.language = "en";
    params.n_threads = 4;
    params.no_timestamps = true;
    params.single_segment = true;

    LOGI("Transcribing %d samples", audioLength);
    int result = whisper_full(ctx, params, audio, audioLength);

    env->ReleaseFloatArrayElements(audioData, audio, JNI_ABORT);

    if (result != 0) {
        LOGE("Transcription failed with code: %d", result);
        return env->NewStringUTF("");
    }

    const int n_segments = whisper_full_n_segments(ctx);
    std::string text;
    for (int i = 0; i < n_segments; i++) {
        const char *segment = whisper_full_get_segment_text(ctx, i);
        text += segment;
    }

    // Trim leading/trailing whitespace
    size_t start = text.find_first_not_of(" \t\n\r");
    size_t end = text.find_last_not_of(" \t\n\r");
    if (start != std::string::npos) {
        text = text.substr(start, end - start + 1);
    } else {
        text = "";
    }

    LOGI("Transcription result: %s", text.c_str());
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_keysink_inputmethod_latin_voice_WhisperEngine_releaseModelNative(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong contextPtr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    if (ctx != nullptr) {
        whisper_free(ctx);
        LOGI("Model released");
    }
}

} // extern "C"
```

- [ ] **Step 4: Create `whisper_jni.h`**

```cpp
#ifndef WHISPER_JNI_H
#define WHISPER_JNI_H

#include <jni.h>

extern "C" {
JNIEXPORT jlong JNICALL Java_com_keysink_inputmethod_latin_voice_WhisperEngine_loadModelNative(JNIEnv *, jclass, jstring);
JNIEXPORT jstring JNICALL Java_com_keysink_inputmethod_latin_voice_WhisperEngine_transcribeNative(JNIEnv *, jclass, jlong, jfloatArray);
JNIEXPORT void JNICALL Java_com_keysink_inputmethod_latin_voice_WhisperEngine_releaseModelNative(JNIEnv *, jclass, jlong);
}

#endif
```

- [ ] **Step 5: Update `Application.mk`**

Change `APP_ABI := all` to:

```makefile
APP_STL := c++_static
APP_ABI := arm64-v8a armeabi-v7a
```

- [ ] **Step 6: Update top-level `Android.mk` to include whisper module**

At the end of the existing `app/src/main/jni/Android.mk`, add:

```makefile
include $(LOCAL_PATH)/whisper/Android.mk
```

- [ ] **Step 7: Build to verify native compilation succeeds**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (both `libjni_latinime.so` and `libwhisper_jni.so` built)

If build fails, check whisper.cpp source file list — the exact files needed depend on the version cloned. Consult `examples/whisper.android/` in the whisper.cpp repo.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/jni/whisper/ app/src/main/jni/Application.mk app/src/main/jni/Android.mk
git commit -m "feat: vendor whisper.cpp and set up native build for libwhisper_jni"
```

---

### Task 6: Create `WhisperEngine` JNI wrapper

**Files:**
- Create: `app/src/main/java/com/keysink/inputmethod/latin/voice/WhisperEngine.kt`
- Test: `app/src/test/java/com/keysink/inputmethod/latin/voice/WhisperEngineTest.kt`

- [ ] **Step 1: Write tests for the wrapper logic**

The JNI calls themselves can't be unit tested without a device, but we test the wrapper's state management:

```kotlin
package com.keysink.inputmethod.latin.voice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WhisperEngineTest {

    @Test
    fun `isAvailable returns false before library load`() {
        // WhisperEngine.isAvailable is false until loadLibrary succeeds
        // In unit tests, native library won't be present
        assertFalse(WhisperEngine.isAvailable)
    }

    @Test
    fun `isModelLoaded returns false initially`() {
        assertFalse(WhisperEngine.isModelLoaded())
    }

    @Test
    fun `MODEL_FILE_NAME is correct`() {
        assertEquals("ggml-base.en.bin", WhisperEngine.MODEL_FILE_NAME)
    }

    @Test
    fun `WHISPER_DIR is correct`() {
        assertEquals("whisper", WhisperEngine.WHISPER_DIR)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "com.keysink.inputmethod.latin.voice.WhisperEngineTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Implement `WhisperEngine.kt`**

```kotlin
package com.keysink.inputmethod.latin.voice

import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

object WhisperEngine {

    const val MODEL_FILE_NAME = "ggml-base.en.bin"
    const val WHISPER_DIR = "whisper"

    var isAvailable: Boolean = false
        private set

    private val modelHandle = AtomicLong(0)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        try {
            System.loadLibrary("whisper_jni")
            isAvailable = true
        } catch (_: UnsatisfiedLinkError) {
            isAvailable = false
        }
    }

    fun isModelLoaded(): Boolean = modelHandle.get() != 0L

    fun getModelFile(filesDir: File): File {
        return File(filesDir, "$WHISPER_DIR/$MODEL_FILE_NAME")
    }

    fun loadModel(modelPath: String, callback: (Boolean) -> Unit) {
        if (!isAvailable) {
            callback(false)
            return
        }
        executor.execute {
            val handle = loadModelNative(modelPath)
            modelHandle.set(handle)
            callback(handle != 0L)
        }
    }

    fun transcribe(audioData: FloatArray, callback: (String?) -> Unit) {
        val handle = modelHandle.get()
        if (!isAvailable || handle == 0L) {
            callback(null)
            return
        }
        executor.execute {
            val result = transcribeNative(handle, audioData)
            callback(result.ifEmpty { null })
        }
    }

    fun releaseModel() {
        val handle = modelHandle.getAndSet(0)
        if (handle != 0L && isAvailable) {
            executor.execute {
                releaseModelNative(handle)
            }
        }
    }

    @JvmStatic
    private external fun loadModelNative(modelPath: String): Long

    @JvmStatic
    private external fun transcribeNative(handle: Long, audioData: FloatArray): String

    @JvmStatic
    private external fun releaseModelNative(handle: Long)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "com.keysink.inputmethod.latin.voice.WhisperEngineTest" --info`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/voice/WhisperEngine.kt \
       app/src/test/java/com/keysink/inputmethod/latin/voice/WhisperEngineTest.kt
git commit -m "feat: add WhisperEngine JNI wrapper with lazy loading"
```

---

## Chunk 3: Permissions, Model Download & Settings UI

### Task 7: Add permissions to manifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add permission declarations**

After the existing `<uses-permission android:name="android.permission.VIBRATE" />` line, add:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

- [ ] **Step 2: Build to verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add INTERNET and RECORD_AUDIO permissions"
```

---

### Task 8: Implement `ModelDownloadManager`

**Files:**
- Create: `app/src/main/java/com/keysink/inputmethod/latin/voice/ModelDownloadManager.kt`
- Test: `app/src/test/java/com/keysink/inputmethod/latin/voice/ModelDownloadManagerTest.kt`

- [ ] **Step 1: Write tests**

```kotlin
package com.keysink.inputmethod.latin.voice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class ModelDownloadManagerTest {

    @Test
    fun `DOWNLOAD_URL points to huggingface`() {
        assertTrue(ModelDownloadManager.DOWNLOAD_URL.contains("huggingface.co"))
        assertTrue(ModelDownloadManager.DOWNLOAD_URL.contains("ggml-base.en.bin"))
    }

    @Test
    fun `EXPECTED_SHA256 is 64 hex characters`() {
        assertEquals(64, ModelDownloadManager.EXPECTED_SHA256.length)
        assertTrue(ModelDownloadManager.EXPECTED_SHA256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `getModelFile returns correct path`() {
        val filesDir = File("/data/data/com.keysink.inputmethod/files")
        val modelFile = ModelDownloadManager.getModelFile(filesDir)
        assertEquals(
            "/data/data/com.keysink.inputmethod/files/whisper/ggml-base.en.bin",
            modelFile.path
        )
    }

    @Test
    fun `isModelDownloaded returns false when file does not exist`() {
        val fakeDir = File("/nonexistent")
        assertFalse(ModelDownloadManager.isModelDownloaded(fakeDir))
    }

    @Test
    fun `DownloadState has correct variants`() {
        // Verify all states exist
        assertNotNull(ModelDownloadManager.DownloadState.NotDownloaded)
        assertNotNull(ModelDownloadManager.DownloadState.Downloading(50))
        assertNotNull(ModelDownloadManager.DownloadState.Complete)
        assertNotNull(ModelDownloadManager.DownloadState.Failed("error"))
    }

    @Test
    fun `Downloading state holds progress value`() {
        val state = ModelDownloadManager.DownloadState.Downloading(75)
        assertEquals(75, state.progress)
    }

    @Test
    fun `Failed state holds error message`() {
        val state = ModelDownloadManager.DownloadState.Failed("Network error")
        assertEquals("Network error", state.message)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "com.keysink.inputmethod.latin.voice.ModelDownloadManagerTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Implement `ModelDownloadManager.kt`**

```kotlin
package com.keysink.inputmethod.latin.voice

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ModelDownloadManager {

    sealed class DownloadState {
        object NotDownloaded : DownloadState()
        data class Downloading(val progress: Int) : DownloadState()
        object Complete : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    interface Callback {
        fun onStateChanged(state: DownloadState)
    }

    private val isCancelled = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun download(filesDir: File, callback: Callback) {
        isCancelled.set(false)
        callback.onStateChanged(DownloadState.Downloading(0))

        executor.execute {
            try {
                val modelDir = File(filesDir, WhisperEngine.WHISPER_DIR)
                modelDir.mkdirs()
                val modelFile = File(modelDir, WhisperEngine.MODEL_FILE_NAME)
                val tempFile = File(modelDir, "${WhisperEngine.MODEL_FILE_NAME}.tmp")

                val url = URL(DOWNLOAD_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true

                try {
                    connection.connect()
                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        callback.onStateChanged(DownloadState.Failed("Download failed (HTTP $responseCode)"))
                        return@execute
                    }

                    val totalBytes = connection.contentLengthLong
                    var downloadedBytes = 0L

                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (isCancelled.get()) {
                                    tempFile.delete()
                                    callback.onStateChanged(DownloadState.NotDownloaded)
                                    return@execute
                                }
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                if (totalBytes > 0) {
                                    val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                    callback.onStateChanged(DownloadState.Downloading(progress))
                                }
                            }
                        }
                    }

                    // Verify checksum
                    val actualHash = sha256(tempFile)
                    if (actualHash != EXPECTED_SHA256) {
                        tempFile.delete()
                        callback.onStateChanged(DownloadState.Failed("Model corrupted"))
                        return@execute
                    }

                    // Atomic rename
                    if (tempFile.renameTo(modelFile)) {
                        callback.onStateChanged(DownloadState.Complete)
                    } else {
                        tempFile.delete()
                        callback.onStateChanged(DownloadState.Failed("Failed to save model"))
                    }

                } finally {
                    connection.disconnect()
                }

            } catch (e: IOException) {
                callback.onStateChanged(DownloadState.Failed("Download failed. Check your connection."))
            } catch (e: Exception) {
                callback.onStateChanged(DownloadState.Failed("Download failed"))
            }
        }
    }

    fun cancel() {
        isCancelled.set(true)
    }

    fun shutdown() {
        cancel()
        executor.shutdown()
    }

    companion object {
        const val DOWNLOAD_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"

        // TODO: Compute and set the actual SHA256 hash of ggml-base.en.bin
        // Download the file manually, run `sha256sum ggml-base.en.bin`, and paste the hash here.
        const val EXPECTED_SHA256 =
            "0000000000000000000000000000000000000000000000000000000000000000"

        fun getModelFile(filesDir: File): File {
            return File(filesDir, "${WhisperEngine.WHISPER_DIR}/${WhisperEngine.MODEL_FILE_NAME}")
        }

        fun isModelDownloaded(filesDir: File): Boolean {
            return getModelFile(filesDir).exists()
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "com.keysink.inputmethod.latin.voice.ModelDownloadManagerTest" --info`
Expected: All 7 tests PASS

Note: The `EXPECTED_SHA256` test will fail until the real hash is set. Temporarily set the test expectation to match the placeholder, or compute the real hash now by downloading the model:
```bash
curl -L "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin" -o /tmp/ggml-base.en.bin
sha256sum /tmp/ggml-base.en.bin
```
Then update `EXPECTED_SHA256` in both the source and test.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/voice/ModelDownloadManager.kt \
       app/src/test/java/com/keysink/inputmethod/latin/voice/ModelDownloadManagerTest.kt
git commit -m "feat: add ModelDownloadManager with progress and SHA256 verification"
```

---

### Task 9: Add Voice Input settings screen

**Files:**
- Create: `app/src/main/java/com/keysink/inputmethod/latin/settings/VoiceInputSettingsFragment.kt`
- Create: `app/src/main/res/xml/prefs_screen_voice.xml`
- Modify: `app/src/main/res/xml/prefs.xml` (add voice sub-screen)
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/utils/FragmentUtils.java` (register fragment)
- Modify: `app/proguard-rules.pro` (add keep rule)

- [ ] **Step 1: Create `prefs_screen_voice.xml`**

Create `app/src/main/res/xml/prefs_screen_voice.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen
    xmlns:android="http://schemas.android.com/apk/res/android">

    <Preference
        android:key="voice_permission_status"
        android:title="Microphone Permission"
        android:summary="Not granted" />

    <Preference
        android:key="voice_model_status"
        android:title="Voice Model"
        android:summary="Not downloaded" />

</PreferenceScreen>
```

- [ ] **Step 2: Add voice sub-screen to `prefs.xml`**

In `app/src/main/res/xml/prefs.xml`, add before the privacy/license preferences:

```xml
<PreferenceScreen
    android:fragment="com.keysink.inputmethod.latin.settings.VoiceInputSettingsFragment"
    android:title="Voice Input"
    android:key="screen_voice" />
```

- [ ] **Step 3: Create `VoiceInputSettingsFragment.kt`**

```kotlin
package com.keysink.inputmethod.latin.settings

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.Preference
import com.keysink.inputmethod.R
import com.keysink.inputmethod.latin.voice.ModelDownloadManager
import com.keysink.inputmethod.latin.voice.WhisperEngine

class VoiceInputSettingsFragment : SubScreenFragment() {

    private var downloadManager: ModelDownloadManager? = null
    private var permissionPref: Preference? = null
    private var modelPref: Preference? = null

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_voice)

        permissionPref = findPreference("voice_permission_status")
        modelPref = findPreference("voice_model_status")

        updatePermissionStatus()
        updateModelStatus()

        permissionPref?.setOnPreferenceClickListener {
            requestMicPermission()
            true
        }

        modelPref?.setOnPreferenceClickListener {
            startModelDownload()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateModelStatus()
    }

    private fun updatePermissionStatus() {
        val granted = activity?.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        permissionPref?.summary = if (granted) "Granted" else "Tap to grant"
    }

    private fun updateModelStatus() {
        val filesDir = activity?.filesDir ?: return
        val downloaded = ModelDownloadManager.isModelDownloaded(filesDir)
        modelPref?.summary = if (downloaded) "Ready" else "Tap to download (~142 MB)"
    }

    private fun requestMicPermission() {
        activity?.requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_MIC_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_MIC_PERMISSION) {
            updatePermissionStatus()
        }
    }

    private fun startModelDownload() {
        val filesDir = activity?.filesDir ?: return
        if (ModelDownloadManager.isModelDownloaded(filesDir)) return

        downloadManager = ModelDownloadManager()
        modelPref?.summary = "Downloading... 0%"

        downloadManager?.download(filesDir, object : ModelDownloadManager.Callback {
            override fun onStateChanged(state: ModelDownloadManager.DownloadState) {
                activity?.runOnUiThread {
                    when (state) {
                        is ModelDownloadManager.DownloadState.Downloading -> {
                            modelPref?.summary = "Downloading... ${state.progress}%"
                        }
                        is ModelDownloadManager.DownloadState.Complete -> {
                            modelPref?.summary = "Ready"
                        }
                        is ModelDownloadManager.DownloadState.Failed -> {
                            modelPref?.summary = "${state.message}. Tap to retry."
                        }
                        is ModelDownloadManager.DownloadState.NotDownloaded -> {
                            modelPref?.summary = "Tap to download (~142 MB)"
                        }
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        downloadManager?.shutdown()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        // No shared preferences to listen to in this fragment
    }

    companion object {
        private const val REQUEST_MIC_PERMISSION = 1001
    }
}
```

- [ ] **Step 4: Register fragment in `FragmentUtils.java`**

Add import:
```java
import com.keysink.inputmethod.latin.settings.VoiceInputSettingsFragment;
```

Add to `sLatinImeFragments` static block:
```java
sLatinImeFragments.add(VoiceInputSettingsFragment.class.getName());
```

- [ ] **Step 5: Add ProGuard keep rule**

In `app/proguard-rules.pro`, add:
```
-keep class com.keysink.inputmethod.latin.settings.VoiceInputSettingsFragment
```

- [ ] **Step 6: Build to verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/settings/VoiceInputSettingsFragment.kt \
       app/src/main/res/xml/prefs_screen_voice.xml \
       app/src/main/res/xml/prefs.xml \
       app/src/main/java/com/keysink/inputmethod/latin/utils/FragmentUtils.java \
       app/proguard-rules.pro
git commit -m "feat: add Voice Input settings screen with permission and download UI"
```

---

## Chunk 4: Controller, Suggestion Strip, & Integration Wiring

### Task 10: Add voice status drawing and icon swap to `MainKeyboardView`

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/keyboard/MainKeyboardView.java`

- [ ] **Step 1: Add voice state fields and imports**

Add import at top:
```java
import com.keysink.inputmethod.latin.voice.VoiceInputState;
```

Add fields alongside existing suggestion strip fields (around line 86):
```java
private VoiceInputState mVoiceInputState = VoiceInputState.IDLE;
private String mVoiceErrorMessage = null;
private int mDotAnimationCounter = 0;
private final Handler mDotAnimationHandler = new Handler(Looper.getMainLooper());
private final Runnable mDotAnimationRunnable = new Runnable() {
    @Override
    public void run() {
        if (mVoiceInputState.getShowsAnimatedDots()) {
            mDotAnimationCounter = (mDotAnimationCounter + 1) % 3;
            invalidate(0, 0, getWidth(), mSuggestionStripHeight);
            mDotAnimationHandler.postDelayed(this, 1000);
        }
    }
};
```

- [ ] **Step 2: Add setter methods**

```java
public void setVoiceInputState(final VoiceInputState state) {
    setVoiceInputState(state, null);
}

public void setVoiceInputState(final VoiceInputState state, final String errorMessage) {
    mVoiceInputState = state;
    mVoiceErrorMessage = errorMessage;
    mDotAnimationCounter = 0;
    mDotAnimationHandler.removeCallbacks(mDotAnimationRunnable);

    if (state.getShowsAnimatedDots()) {
        mDotAnimationHandler.postDelayed(mDotAnimationRunnable, 1000);
    }

    if (state == VoiceInputState.ERROR && errorMessage != null) {
        // Auto-clear error after 3 seconds
        mDotAnimationHandler.postDelayed(() -> {
            if (mVoiceInputState == VoiceInputState.ERROR) {
                mVoiceInputState = VoiceInputState.IDLE;
                mVoiceErrorMessage = null;
                invalidate(0, 0, getWidth(), mSuggestionStripHeight);
            }
        }, 3000);
    }

    invalidate(0, 0, getWidth(), mSuggestionStripHeight);
}
```

- [ ] **Step 3: Add `drawVoiceStatus()` method**

```java
private void drawVoiceStatus(final Canvas canvas, final int width, final int stripHeight) {
    final float padding = mSuggestionHorizontalPadding;
    final float textY = stripHeight / 2f + mSuggestionPaint.getTextSize() / 3f;

    if (mVoiceInputState == VoiceInputState.ERROR && mVoiceErrorMessage != null) {
        // Error: centered text
        final float textWidth = mSuggestionPaint.measureText(mVoiceErrorMessage);
        canvas.drawText(mVoiceErrorMessage, (width - textWidth) / 2f, textY, mSuggestionPaint);
    } else {
        // Recording/Transcribing: left-aligned with animated dots
        final String statusText = mVoiceInputState.getStatusText();
        if (statusText != null) {
            final String dots;
            switch (mDotAnimationCounter) {
                case 0: dots = "."; break;
                case 1: dots = ".."; break;
                default: dots = "..."; break;
            }
            canvas.drawText(statusText + dots, padding, textY, mSuggestionPaint);
        }
    }
}
```

- [ ] **Step 4: Modify `drawSuggestionStrip()` to check voice state**

In `drawSuggestionStrip()` (MainKeyboardView.java around line 274), insert the voice state check between `canvas.drawColor(Color.WHITE)` and the existing `final SuggestedWords words = ...` block. The full method should read:

```java
private void drawSuggestionStrip(final Canvas canvas) {
    if (!mSuggestionStripEnabled) return;
    final int stripHeight = mSuggestionStripHeight;
    if (stripHeight <= 0) return;

    final int width = getWidth();
    if (width <= 0) return;

    // White background for the strip area
    canvas.save();
    canvas.clipRect(0, 0, width, stripHeight);
    canvas.drawColor(Color.WHITE);

    // === NEW: Voice status takes over the strip when active ===
    if (mVoiceInputState.getHidesSuggestions()) {
        drawVoiceStatus(canvas, width, stripHeight);
        // Bottom separator line
        canvas.drawLine(0, stripHeight - 1, width, stripHeight - 1, mSeparatorPaint);
        canvas.restore();
        return;
    }
    // === END NEW ===

    final SuggestedWords words = mSuggestedWords;
    if (words != null && !words.isEmpty() && words.size() > 0) {
        drawSuggestionWords(canvas, words, width, stripHeight);
    }

    // Bottom separator line
    canvas.drawLine(0, stripHeight - 1, width, stripHeight - 1, mSeparatorPaint);
    canvas.restore();
}
```

Note: The `canvas.save()` at the top is matched by the `canvas.restore()` in both the early-return path and the normal path — the save/restore stack stays balanced.

- [ ] **Step 5: Add mic-to-stop icon swap support**

The mic key icon must change to the stop icon during RECORDING state. Add a method to swap the icon on the Key object and a getter for the current voice state:

```java
public VoiceInputState getVoiceInputState() {
    return mVoiceInputState;
}
```

Then, in the key drawing code, the icon swap is handled by overriding `getKeyIcon()` or by checking the voice state in `KeyboardView.onDrawKey()`. The simplest approach: in `KeyboardView.java`'s `onDrawKey()` method (which draws each key), check if the key's code is `Constants.CODE_VOICE_INPUT` and the current voice state is RECORDING. If so, substitute the stop icon:

In `KeyboardView.java`, find the `onDrawKey()` method where key icons are drawn. Add before the icon draw:

```java
Drawable icon = key.getIcon(mKeyboard.mIconsSet, Constants.NOT_A_CODE);
// Swap mic icon to stop icon during recording
if (key.getCode() == Constants.CODE_VOICE_INPUT) {
    final MainKeyboardView mainView = (this instanceof MainKeyboardView) ? (MainKeyboardView) this : null;
    if (mainView != null && mainView.getVoiceInputState() == VoiceInputState.RECORDING) {
        icon = mKeyboard.mIconsSet.getIconDrawable(KeyboardIconsSet.NAME_STOP_KEY);
    }
}
```

Alternatively, if `KeyboardView` doesn't have easy access to `MainKeyboardView`, handle it in `MainKeyboardView` by overriding the draw method for this specific key. Choose whichever approach is cleanest after reading the existing `onDrawKey` code. The key requirement: during RECORDING, the mic key shows the stop icon; in all other states it shows the mic icon.

- [ ] **Step 6: Clean up handler in `onDetachedFromWindow`**

Override `onDetachedFromWindow` (or add to existing if present):

```java
@Override
protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    mDotAnimationHandler.removeCallbacks(mDotAnimationRunnable);
}
```

- [ ] **Step 7: Build to verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/keyboard/MainKeyboardView.java
git commit -m "feat: add voice status drawing to suggestion strip area"
```

---

### Task 11: Implement `VoiceInputController`

**Files:**
- Create: `app/src/main/java/com/keysink/inputmethod/latin/voice/VoiceInputController.kt`
- Test: `app/src/test/java/com/keysink/inputmethod/latin/voice/VoiceInputControllerTest.kt`

- [ ] **Step 1: Write tests**

```kotlin
package com.keysink.inputmethod.latin.voice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VoiceInputControllerTest {

    @Test
    fun `canTranscribe returns false when native library not loaded`() {
        // WhisperEngine.isAvailable is false in tests (no native lib)
        assertFalse(VoiceInputController.canTranscribe())
    }

    @Test
    fun `prerequisites check identifies missing native library`() {
        val result = VoiceInputController.checkPrerequisites(
            hasPermission = true,
            modelExists = true,
            nativeAvailable = false
        )
        assertEquals(VoiceInputController.PrerequisiteResult.NATIVE_UNAVAILABLE, result)
    }

    @Test
    fun `prerequisites check identifies missing permission`() {
        val result = VoiceInputController.checkPrerequisites(
            hasPermission = false,
            modelExists = true,
            nativeAvailable = true
        )
        assertEquals(VoiceInputController.PrerequisiteResult.NEEDS_PERMISSION, result)
    }

    @Test
    fun `prerequisites check identifies missing model`() {
        val result = VoiceInputController.checkPrerequisites(
            hasPermission = true,
            modelExists = false,
            nativeAvailable = true
        )
        assertEquals(VoiceInputController.PrerequisiteResult.NEEDS_MODEL, result)
    }

    @Test
    fun `prerequisites check returns READY when all satisfied`() {
        val result = VoiceInputController.checkPrerequisites(
            hasPermission = true,
            modelExists = true,
            nativeAvailable = true
        )
        assertEquals(VoiceInputController.PrerequisiteResult.READY, result)
    }

    @Test
    fun `prerequisites check prioritizes permission over model`() {
        val result = VoiceInputController.checkPrerequisites(
            hasPermission = false,
            modelExists = false,
            nativeAvailable = true
        )
        assertEquals(VoiceInputController.PrerequisiteResult.NEEDS_PERMISSION, result)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "com.keysink.inputmethod.latin.voice.VoiceInputControllerTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Implement `VoiceInputController.kt`**

```kotlin
package com.keysink.inputmethod.latin.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.keysink.inputmethod.keyboard.MainKeyboardView
import com.keysink.inputmethod.latin.RichInputConnection
import com.keysink.inputmethod.latin.settings.SettingsActivity
import java.io.File

class VoiceInputController(
    private val context: Context,
    private val filesDir: File
) {

    enum class PrerequisiteResult {
        READY,
        NEEDS_PERMISSION,
        NEEDS_MODEL,
        NATIVE_UNAVAILABLE
    }

    enum class State {
        IDLE, RECORDING, TRANSCRIBING, ERROR;

        fun toVoiceInputState(): VoiceInputState = when (this) {
            IDLE -> VoiceInputState.IDLE
            RECORDING -> VoiceInputState.RECORDING
            TRANSCRIBING -> VoiceInputState.TRANSCRIBING
            ERROR -> VoiceInputState.ERROR
        }
    }

    interface Callback {
        fun onStateChanged(state: VoiceInputState, errorMessage: String?)
        fun onTranscriptionResult(text: String)
        fun launchVoiceSettings()
    }

    @Volatile private var state = State.IDLE
    private var callback: Callback? = null
    private val audioRecorder = AudioRecorder()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    fun onMicKeyPressed() {
        when (state) {
            State.IDLE -> handleIdleTap()
            State.RECORDING -> stopRecording()
            State.TRANSCRIBING -> { /* no-op */ }
            State.ERROR -> handleIdleTap()
        }
    }

    private fun handleIdleTap() {
        val prereq = checkPrerequisites(
            hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
            modelExists = ModelDownloadManager.isModelDownloaded(filesDir),
            nativeAvailable = WhisperEngine.isAvailable
        )

        when (prereq) {
            PrerequisiteResult.READY -> startRecording()
            PrerequisiteResult.NATIVE_UNAVAILABLE -> {
                transitionTo(State.ERROR, "Voice unavailable")
            }
            PrerequisiteResult.NEEDS_PERMISSION,
            PrerequisiteResult.NEEDS_MODEL -> {
                callback?.launchVoiceSettings()
            }
        }
    }

    private fun startRecording() {
        // Load model if not already loaded
        if (!WhisperEngine.isModelLoaded()) {
            val modelFile = WhisperEngine.getModelFile(filesDir)
            WhisperEngine.loadModel(modelFile.absolutePath) { success ->
                mainHandler.post {
                    if (success) {
                        beginRecording()
                    } else {
                        transitionTo(State.ERROR, "Model corrupted")
                    }
                }
            }
        } else {
            beginRecording()
        }
    }

    private fun beginRecording() {
        transitionTo(State.RECORDING)
        audioRecorder.startRecording(object : AudioRecorder.Callback {
            override fun onRecordingComplete(audioData: FloatArray) {
                mainHandler.post {
                    transitionTo(State.TRANSCRIBING)
                    transcribe(audioData)
                }
            }

            override fun onRecordingError(message: String) {
                mainHandler.post {
                    transitionTo(State.ERROR, message)
                }
            }

            override fun onMaxDurationReached() {
                // Recording will auto-complete via onRecordingComplete
            }
        })
    }

    private fun stopRecording() {
        audioRecorder.stopRecording()
    }

    private fun transcribe(audioData: FloatArray) {
        WhisperEngine.transcribe(audioData) { result ->
            mainHandler.post {
                if (result != null && result.isNotEmpty()) {
                    callback?.onTranscriptionResult(result)
                    transitionTo(State.IDLE)
                } else {
                    transitionTo(State.ERROR, "Transcription failed")
                }
            }
        }
    }

    private fun transitionTo(newState: State, errorMessage: String? = null) {
        state = newState
        callback?.onStateChanged(newState.toVoiceInputState(), errorMessage)
    }

    fun onStartInputView() {
        if (state == State.RECORDING || state == State.TRANSCRIBING) {
            cancelAndReset()
        }
    }

    fun destroy() {
        cancelAndReset()
        audioRecorder.release()
        WhisperEngine.releaseModel()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun cancelAndReset() {
        audioRecorder.stopRecording()
        state = State.IDLE
        callback?.onStateChanged(VoiceInputState.IDLE, null)
    }

    companion object {
        fun canTranscribe(): Boolean {
            return WhisperEngine.isAvailable && WhisperEngine.isModelLoaded()
        }

        fun checkPrerequisites(
            hasPermission: Boolean,
            modelExists: Boolean,
            nativeAvailable: Boolean
        ): PrerequisiteResult {
            if (!nativeAvailable) return PrerequisiteResult.NATIVE_UNAVAILABLE
            if (!hasPermission) return PrerequisiteResult.NEEDS_PERMISSION
            if (!modelExists) return PrerequisiteResult.NEEDS_MODEL
            return PrerequisiteResult.READY
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --tests "com.keysink.inputmethod.latin.voice.VoiceInputControllerTest" --info`
Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/voice/VoiceInputController.kt \
       app/src/test/java/com/keysink/inputmethod/latin/voice/VoiceInputControllerTest.kt
git commit -m "feat: add VoiceInputController with state machine and prerequisites"
```

---

### Task 12: Wire voice input into `InputLogic` and `LatinIME`

**Files:**
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java`
- Modify: `app/src/main/java/com/keysink/inputmethod/latin/LatinIME.java`

- [ ] **Step 1: Add `CODE_VOICE_INPUT` handler in `InputLogic.handleFunctionalEvent()`**

In the switch statement (around line 240), before the `default:` case, add:

```java
case Constants.CODE_VOICE_INPUT:
    mLatinIME.onVoiceInputKeyPressed();
    break;
```

- [ ] **Step 2: Add `onVoiceInputKeyPressed()` method to `LatinIME`**

Add a public method that the `InputLogic` can call:

```java
public void onVoiceInputKeyPressed() {
    if (mVoiceInputController != null) {
        mVoiceInputController.onMicKeyPressed();
    }
}
```

- [ ] **Step 3: Initialize `VoiceInputController` in `LatinIME.onCreate()`**

Add field:
```java
private VoiceInputController mVoiceInputController;
```

In `onCreate()`, after existing initialization:
```java
mVoiceInputController = new VoiceInputController(this, getFilesDir());
mVoiceInputController.setCallback(new VoiceInputController.Callback() {
    @Override
    public void onStateChanged(VoiceInputState state, String errorMessage) {
        final MainKeyboardView keyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (keyboardView != null) {
            keyboardView.setVoiceInputState(state, errorMessage);
        }
    }

    @Override
    public void onTranscriptionResult(String text) {
        mInputLogic.mConnection.commitText(text + " ", 1);
    }

    @Override
    public void launchVoiceSettings() {
        // Mirror the existing launchSettings() pattern
        requestHideSelf(0);
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
        final Intent intent = new Intent();
        intent.setClass(LatinIME.this, SettingsActivity.class);
        intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
            "com.keysink.inputmethod.latin.settings.VoiceInputSettingsFragment");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
});
```

- [ ] **Step 4: Add lifecycle hooks**

In `onStartInputViewInternal()` (or equivalent), add:
```java
if (mVoiceInputController != null) {
    mVoiceInputController.onStartInputView();
}
```

In `onDestroy()`, add:
```java
if (mVoiceInputController != null) {
    mVoiceInputController.destroy();
}
```

- [ ] **Step 5: Add imports to `LatinIME.java`**

```java
import com.keysink.inputmethod.latin.voice.VoiceInputController;
import com.keysink.inputmethod.latin.voice.VoiceInputState;
```

- [ ] **Step 6: Build to verify**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run all tests**

Run: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew test --info`
Expected: All tests PASS (existing 53 + new voice tests)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/keysink/inputmethod/latin/inputlogic/InputLogic.java \
       app/src/main/java/com/keysink/inputmethod/latin/LatinIME.java
git commit -m "feat: wire VoiceInputController into LatinIME and InputLogic"
```

---

### Task 13: Integration testing on device

This task cannot be TDD'd — it requires the physical Boox NoteAir 5C.

- [ ] **Step 1: Build and install**

```bash
JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Verify mic key appears in bottom row**

Open any text field. Confirm the mic icon appears between period and enter. Confirm it does NOT appear in symbols keyboard.

- [ ] **Step 3: Test first-tap flow (no permission, no model)**

Tap mic key. Expected: Settings opens to Voice Input screen showing "Tap to grant" for permission and "Tap to download" for model.

- [ ] **Step 4: Grant RECORD_AUDIO permission**

Tap "Microphone Permission". Grant the system permission dialog. Confirm status updates to "Granted".

- [ ] **Step 5: Download model**

Tap "Voice Model". Confirm download progress updates. Confirm "Ready" when complete. (~142MB, may take a few minutes on WiFi.)

- [ ] **Step 6: Test recording**

Go back to text field. Tap mic key. Confirm:
- Mic icon swaps to stop icon
- Suggestion strip shows "Recording." → "Recording.." → "Recording..."
- Dot animation cycles every ~1 second without excessive ghosting

- [ ] **Step 7: Test transcription**

Say a short phrase, tap stop. Confirm:
- Stop icon reverts to mic
- Strip shows "Transcribing." → "Transcribing.." → "Transcribing..."
- After a few seconds, text appears in the editor
- Trailing space added after transcribed text
- Suggestion strip returns to normal

- [ ] **Step 8: Test error cases**

- Revoke RECORD_AUDIO permission in system settings, tap mic → should open Voice Input settings
- Test with no model file (delete from internal storage via adb) → should open Voice Input settings

- [ ] **Step 9: Test lifecycle**

- Start recording, switch to another app, come back → recording should have been cancelled
- Start recording, change text field (tap another input) → recording should cancel

- [ ] **Step 10: Check logcat for issues**

```bash
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -E "keysink|WhisperJNI|FATAL|SIGABRT"
```

- [ ] **Step 11: Commit any fixes from device testing**

```bash
git add -A
git commit -m "fix: address issues found during device testing"
```
