# Voice Input (v0.3) — Design Spec

## Overview

Add voice-to-text input to Keys, Ink using whisper.cpp (base.en model, ~142MB full-precision). Tap-to-toggle mic key in the bottom keyboard row. Model downloaded on first use. English-only for v0.3.

## Mic Key & Keyboard Layout

A new key is added to the bottom row of `row_qwerty4.xml`, between the period key and the enter key:

```
[123 15%] [, 10%] [space 50%] [. 10%] [mic 10%] [enter fillRight]
```

- Key code: new custom constant `Constants.CODE_VOICE_INPUT` (negative value, e.g. `-12`, following the pattern of other custom codes in `Constants.java`)
- Icon: microphone vector drawable, swaps to stop icon during recording
- Background type: `functional` (matches comma/period styling)
- Width: 10%p (same as comma and period)

The key is always visible but functionally gated — if the model isn't downloaded or RECORD_AUDIO isn't granted, tapping it opens Settings. Otherwise it toggles recording.

**Scope:** Mic key added only to `row_qwerty4.xml` (alphabet bottom row). Not added to `row_symbols4.xml`, `row_symbols_shift4.xml`, or other layout variants. Tablet variant (`xml-sw600dp/row_qwerty4.xml`) gets the mic key too if it exists.

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
- `RECORDING` → input field changes (`onStartInputView`) → cancel recording → `IDLE`
- `RECORDING` → IME destroyed → release resources → `IDLE`
- `TRANSCRIBING` → success → insert text → `IDLE`
- `TRANSCRIBING` → failure → `ERROR`
- `TRANSCRIBING` → IME destroyed → cancel, release resources → `IDLE`
- `ERROR` → 3s timeout → `IDLE`

**Prerequisites check on mic tap** (in order):
1. RECORD_AUDIO permission granted? If no → open Settings
2. Model file exists in internal storage? If no → attempt to open Settings for download (if download fails due to no internet, Settings screen shows error with retry)
3. All clear → `RECORDING`

**Error messages:**
- "Mic unavailable" — AudioRecord initialization failed
- "Recording failed" — AudioRecord read error
- "Transcription failed" — whisper.cpp returned error
- "Model corrupted" — checksum mismatch on load

### Lifecycle

`VoiceInputController` is owned by `LatinIME` (the service). It receives references to `InputLogic` and `MainKeyboardView` during initialization.

- **`onStartInputView`**: If recording or transcribing, cancel and return to IDLE
- **`onDestroy`**: Release `AudioRecord`, free whisper model handle via `WhisperEngine.releaseModel()`, cancel any running transcription, remove all `Handler` callbacks

## Native Layer (whisper.cpp JNI)

Separate native library: `libwhisper_jni.so`, built via its own `Android.mk` module in `app/src/main/jni/whisper/`.

### Kotlin JNI Wrapper: `WhisperEngine.kt`

- `loadModel(modelPath: String): Long` — loads model file, returns opaque handle
- `transcribe(handle: Long, audioBuffer: FloatArray): String` — runs inference, returns text
- `releaseModel(handle: Long)` — frees native resources

If `System.loadLibrary("whisper_jni")` fails (`UnsatisfiedLinkError`), voice input is permanently disabled for the session. The mic key tap shows "Voice unavailable" in the strip.

### C++ Wrapper: `whisper_jni.cpp`

- Thin bridge between JNI and whisper.cpp's C API (`whisper_init_from_file`, `whisper_full`, `whisper_free`)
- Single-threaded inference — called from background `ExecutorService`, never on main thread

### Build

- whisper.cpp sources vendored into `app/src/main/jni/whisper/` (MIT license)
- Own `Android.mk` module producing `libwhisper_jni.so`
- ABI targets: `arm64-v8a`, `armeabi-v7a` (whisper module gets its own `Application.mk` or the shared `Application.mk` is updated from `APP_ABI := all` to `APP_ABI := arm64-v8a armeabi-v7a`)
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

**Max duration:** 120 seconds soft cap. Recording auto-stops and begins transcription at the limit. At 120s, audio buffer is ~3.8MB and transcription time on ARM is manageable.

## Model Download & Settings Flow

### First-Tap Flow

Mic tap → `VoiceInputController` detects missing model or permission → launches `SettingsActivity` with intent extra targeting the Voice Input settings screen.

### Voice Input Settings Screen: `VoiceInputSettingsFragment.kt`

- Shows RECORD_AUDIO permission status with a "Grant" button (calls `getActivity().requestPermissions()`)
- Shows model download status: Not downloaded / Downloading (progress %) / Ready (142MB)
- "Download" button triggers the download
- Both must be green before voice works
- Must be registered in `FragmentUtils.java` `sLatinImeFragments` set

### Model Download: `ModelDownloadManager.kt`

- Uses `HttpURLConnection` (no external dependencies)
- Download URL: `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin`
- Writes to `context.getFilesDir()/whisper/ggml-base.en.bin`
- Streams to disk with progress callback
- SHA256 checksum verification after download — expected hash hardcoded as a constant in `ModelDownloadManager.kt` (update the constant when changing model versions)
- Runs on background `ExecutorService`
- If interrupted or fails: deletes partial file, shows retry button
- Connectivity check: attempt the download directly, handle `IOException` — no `ACCESS_NETWORK_STATE` permission needed

### Permissions Note

No AndroidX required. `SettingsActivity` extends `PreferenceActivity`. Permission requests use framework `Activity.requestPermissions()` API (available since API 23, min SDK is 24).

New fragment added to `proguard-rules.pro` keep rules and `FragmentUtils.java` whitelist.

## Suggestion Strip Integration

The suggestion strip area on `MainKeyboardView` canvas gets new drawing modes:

### Recording Mode
"Recording" text drawn left-aligned in the strip, followed by animated dots. Dots cycle through ".", "..", "..." on a 1-second `Handler` timer that invalidates only the strip rect. Suggestion words hidden.

**E-ink note:** 1Hz partial refresh for 3 characters of dots is acceptable — it's a small region update, not a full-screen animation. The existing suggestion strip already does targeted `invalidate()` calls on the strip rect when suggestions change. If 1s causes visible ghosting on the Boox, we can slow to 2s during device testing.

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
| `VoiceInputSettingsFragment.kt` | Kotlin | Settings UI for permission + download |
| `prefs_screen_voice.xml` | XML | Settings screen layout |
| `whisper_jni.cpp` | C++ | JNI bridge to whisper.cpp C API |
| `whisper/Android.mk` | Makefile | Native build for libwhisper_jni.so |
| `key_mic.xml` | XML | Mic key definition |
| Mic/stop drawables | XML/vector | Key icons |

## Modified Files Summary

| File | Change |
|------|--------|
| `Constants.java` | Add `CODE_VOICE_INPUT` constant |
| `row_qwerty4.xml` | Add mic key between period and enter |
| `MainKeyboardView.java` | Add `drawVoiceStatus()`, voice state field, dot animation handler |
| `LatinIME.java` | Own `VoiceInputController`, wire lifecycle callbacks |
| `InputLogic.java` | Handle `CODE_VOICE_INPUT` key press |
| `AndroidManifest.xml` | Add INTERNET + RECORD_AUDIO permissions |
| `FragmentUtils.java` | Register `VoiceInputSettingsFragment` |
| `proguard-rules.pro` | Keep rule for new fragment |
| `app/build.gradle` | Add whisper native build reference if needed |
| `Application.mk` or new whisper `Application.mk` | Set ABI targets for whisper module |
