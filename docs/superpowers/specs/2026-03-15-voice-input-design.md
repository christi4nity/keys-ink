# Voice Input (v0.3) — Design Spec

## Overview

Add voice-to-text input to Keys, Ink using whisper.cpp (base.en model, ~142MB). Tap-to-toggle mic key in the bottom keyboard row. Model downloaded on first use. English-only for v0.3.

## Mic Key & Keyboard Layout

A new key is added to the bottom row of `row_qwerty4.xml`, between the period key and the enter key:

```
[123 15%] [, 10%] [space 50%] [. 10%] [mic 10%] [enter fillRight]
```

- Key code: `Constants.CODE_SHORTCUT` (AOSP voice/shortcut key code) or new custom code
- Icon: microphone drawable, swaps to stop icon during recording
- Background type: `functional` (matches comma/period styling)
- Width: 10%p (same as comma and period)

The key is always visible but functionally gated — if the model isn't downloaded or RECORD_AUDIO isn't granted, tapping it opens Settings. Otherwise it toggles recording.

## State Machine

Five states managed by `VoiceInputState` enum in `VoiceInputController`:

| State | Mic Icon | Suggestion Strip | Tap Action |
|-------|----------|-----------------|------------|
| **IDLE** | Mic | Normal suggestions | Check prerequisites → start recording |
| **NOT_READY** | Mic | Normal suggestions | Open Settings (download + permission flow) |
| **RECORDING** | Stop | "Recording..." + animated dots | Stop recording → begin transcription |
| **TRANSCRIBING** | Mic | "Transcribing..." + animated dots | Ignored (no-op) |
| **ERROR** | Mic | Error message (3s) → revert to IDLE | Start recording |

**Transitions:**
- `IDLE` → tap mic → `RECORDING`
- `IDLE` → tap mic, no model/permission → opens Settings (stays IDLE)
- `IDLE` → tap mic, no internet, no model → shows error in strip (stays IDLE)
- `RECORDING` → tap stop → `TRANSCRIBING`
- `TRANSCRIBING` → success → insert text → `IDLE`
- `TRANSCRIBING` → failure → `ERROR`
- `ERROR` → 3s timeout → `IDLE`

**Prerequisites check on mic tap** (in order):
1. RECORD_AUDIO permission granted? If no → open Settings
2. Model file exists in internal storage? If no + internet → open Settings for download. If no + no internet → show error in strip.
3. All clear → `RECORDING`

## Native Layer (whisper.cpp JNI)

Separate native library: `libwhisper_jni.so`, built via its own `Android.mk` module in `app/src/main/jni/whisper/`.

### Kotlin JNI Wrapper: `WhisperEngine.kt`

- `loadModel(modelPath: String): Long` — loads model file, returns opaque handle
- `transcribe(handle: Long, audioBuffer: FloatArray): String` — runs inference, returns text
- `releaseModel(handle: Long)` — frees native resources

### C++ Wrapper: `whisper_jni.cpp`

- Thin bridge between JNI and whisper.cpp's C API (`whisper_init_from_file`, `whisper_full`, `whisper_free`)
- Single-threaded inference — called from background `ExecutorService`, never on main thread

### Build

- whisper.cpp sources vendored into `app/src/main/jni/whisper/` (MIT license)
- Own `Android.mk` module producing `libwhisper_jni.so`
- Same ABI targets: `arm64-v8a`, `armeabi-v7a`
- Loaded lazily via `System.loadLibrary("whisper_jni")` only when voice is first used

### Audio Format

whisper.cpp expects 16kHz mono float PCM. Android's `AudioRecord` captures at 16kHz/16-bit PCM, converted to float before passing to native.

## Audio Recording

### `AudioRecorder.kt`

Wraps Android's `AudioRecord` API.

**Configuration:**
- Source: `MediaRecorder.AudioSource.MIC`
- Sample rate: 16,000 Hz
- Channel: `AudioFormat.CHANNEL_IN_MONO`
- Encoding: `AudioFormat.ENCODING_PCM_16BIT`
- Buffer: `AudioRecord.getMinBufferSize()` with a reasonable minimum

**Recording loop:**
- Runs on a background thread (dedicated `HandlerThread` or `ExecutorService`)
- Reads 16-bit PCM chunks into a growing `ShortArray` buffer
- On stop: converts `ShortArray` → `FloatArray` (divide by `Short.MAX_VALUE` to normalize to [-1.0, 1.0])
- Passes `FloatArray` to `WhisperEngine.transcribe()` on the same background executor

**Memory:** At 16kHz mono 16-bit, audio is ~32KB/s. A 60-second recording is ~1.9MB. Buffer the full recording in memory and transcribe in one shot.

No max duration enforced for v0.3.

## Model Download & Settings Flow

### First-Tap Flow

Mic tap → `VoiceInputController` detects missing model or permission → launches `SettingsActivity` with intent extra targeting the Voice Input settings screen.

### Voice Input Settings Screen: `VoiceInputSettingsFragment`

- Shows RECORD_AUDIO permission status with a "Grant" button (`Activity.requestPermissions()`)
- Shows model download status: Not downloaded / Downloading (progress %) / Ready (142MB)
- "Download" button triggers the download
- Both must be green before voice works

### Model Download: `ModelDownloadManager.kt`

- Uses `HttpURLConnection` (no external dependencies)
- Downloads `ggml-base.en.bin` from Hugging Face model repo
- Writes to `context.getFilesDir()/whisper/ggml-base.en.bin`
- Streams to disk with progress callback
- SHA256 checksum verification after download
- Runs on background `ExecutorService`
- If interrupted or fails: deletes partial file, shows retry button

### Permissions Note

No AndroidX required. `SettingsActivity` extends `PreferenceActivity`. Permission requests use framework `Activity.requestPermissions()` API (available since API 23, min SDK is 24).

New fragment added to `proguard-rules.pro` keep rules.

## Suggestion Strip Integration

The suggestion strip area on `MainKeyboardView` canvas gets new drawing modes:

### Recording Mode
"Recording" text drawn left-aligned in the strip, followed by animated dots. Dots cycle through ".", "..", "..." on a 1-second `Handler` timer that invalidates only the strip rect. Suggestion words hidden.

### Transcribing Mode
Same layout as recording: "Transcribing" + animated dots. Mic icon reverts to mic. Suggestions hidden.

### Error Mode
Error message drawn centered in strip. Reverts to normal after 3 seconds via `Handler.postDelayed`.

### Implementation in MainKeyboardView

- New field: `mVoiceInputState` (enum) set by `VoiceInputController` via setter
- `drawSuggestionStrip()` checks state — if not IDLE, delegates to `drawVoiceStatus()` instead of `drawSuggestionWords()`
- Dot animation: `Handler` posts strip rect invalidation every 1s, counter cycles 0→1→2
- On transcription complete, state returns to IDLE, strip resumes normal behavior

### Text Insertion

`VoiceInputController` calls `InputLogic` to commit transcribed text via `RichInputConnection.commitText()`. No composing spans (Boox constraint).

## New Permissions

Added to `AndroidManifest.xml`:
- `android.permission.INTERNET` — model download
- `android.permission.RECORD_AUDIO` — microphone access (runtime permission)

## New Files Summary

| File | Language | Purpose |
|------|----------|---------|
| `VoiceInputController.kt` | Kotlin | State machine, orchestrates recording/transcription |
| `VoiceInputState.kt` | Kotlin | Enum for the five states |
| `WhisperEngine.kt` | Kotlin | JNI wrapper for whisper.cpp |
| `AudioRecorder.kt` | Kotlin | Android AudioRecord wrapper |
| `ModelDownloadManager.kt` | Kotlin | HTTP download with progress + checksum |
| `VoiceInputSettingsFragment.java` | Java | Settings UI for permission + download |
| `prefs_screen_voice.xml` | XML | Settings screen layout |
| `whisper_jni.cpp` | C++ | JNI bridge to whisper.cpp C API |
| `whisper/Android.mk` | Makefile | Native build for libwhisper_jni.so |
| `key_mic.xml` | XML | Mic key definition |
| Mic/stop drawables | XML/vector | Key icons |
