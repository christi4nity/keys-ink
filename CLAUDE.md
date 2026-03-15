# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Keys, Ink is a fork of Simple Keyboard — a minimal Android soft keyboard (IME) based on AOSP LatinIME. Pure Java, no Kotlin, zero external dependencies. Package: `com.keysink.inputmethod`.

Key traits: <1MB size, no emoji/GIF/spellcheck/swipe-typing, only VIBRATE permission, 80+ language layouts.

## Build Commands

```bash
./gradlew assembleDebug          # Debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease        # Release APK (requires signing config)
./gradlew clean                  # Clean build artifacts
```

- Gradle 9.1.0, AGP 9.0.0, compileSdk/targetSdk 36, minSdk 24
- Android SDK expected at `~/Library/Android/sdk`
- No Kotlin (`android.builtInKotlin=false`), no AndroidX (`android.useAndroidX=false`)

## Architecture

The codebase follows AOSP LatinIME structure with three main layers:

### IME Service Layer
- **`LatinIME`** — The `InputMethodService` subclass. Entry point for the keyboard. Handles lifecycle, editor connections, and delegates to `InputLogic` for key processing and `KeyboardSwitcher` for view management.
- **`InputLogic`** — All text manipulation logic: character insertion, deletion, cursor movement, space-swipe pointer, delete-swipe, recapitalization. Communicates with the editor via `RichInputConnection`.
- **`RichInputConnection`** — Wrapper around `InputConnection` that handles text composition, cursor position tracking, and editor communication.

### Keyboard Layout Layer
- **`KeyboardSwitcher`** — Singleton managing which keyboard is displayed. Switches between alphabet, symbols, symbols-shifted based on `KeyboardState`.
- **`KeyboardLayoutSet`** — Loads and caches keyboard layouts per `EditorInfo`. A layout set contains all keyboards for a given context (alpha, symbols, phone, number).
- **`KeyboardBuilder`** — Parses XML layout files (`res/xml/kbd_*.xml`) into `Keyboard` objects. XML hierarchy: `kbd_*.xml` → `rows_*.xml` → `rowkeys_*.xml` for key definitions.
- **`Key`** / **`Keyboard`** — Data models for individual keys and full keyboards.

### View/Rendering Layer
- **`MainKeyboardView`** — Custom `View` that renders the keyboard and handles touch input via `PointerTracker`.
- **`PointerTracker`** — Translates touch events into key presses, handling multi-touch, long-press, swipe gestures, and key repeat.
- **`KeyboardView`** — Base view class that draws keys using `Canvas`.

### Settings
- **`Settings`** — Singleton `SharedPreferences` manager. Preference keys defined as `PREF_*` constants.
- **`SettingsValues`** — Immutable snapshot of current settings, recreated on preference changes.
- Settings UI fragments in `latin/settings/` correspond to XML in `res/xml/prefs_screen_*.xml`.

## Keyboard Layout XML Structure

Adding or modifying a keyboard layout involves a 3-level XML hierarchy:

1. **`keyboard_layout_set_*.xml`** — Maps element types (alphabet, symbols, phone) to keyboard XML files
2. **`kbd_*.xml`** — Defines a keyboard: includes `key_styles_*.xml` and `rows_*.xml`
3. **`rows_*.xml`** → **`rowkeys_*.xml`** — Row definitions referencing individual key specs

Shared key styles live in `key_styles_common.xml`, `key_styles_enter.xml`, `key_styles_actions.xml`, etc.

Locale-specific strings and more-keys are in `KeyboardTextsTable.java` (programmatic, not XML).

## E-Ink Optimizations (Current)

- **Default theme**: `KeyboardTheme.EInk` (ID 7) — pure white background, black text, gray key borders
- **Key press feedback**: Border thickens from 1dp gray to 2.5dp black (no color fill change)
- **Key preview popups**: Disabled (height/width/offset all 0dp) — prevents ghosting
- **No animations**: No key preview dismiss animation in e-ink theme

## Roadmap

### v0.2 — Autocorrect + Suggestions
- Re-add AOSP suggestion strip from LatinIME
- Integrate AOSP dictionaries (Apache 2.0) — English first
- E-ink styled suggestion bar: plain text, bold center candidate, no animations
- Auto-capitalize after sentence-ending punctuation
- Auto-space after picking suggestion, undo on immediate backspace

### v0.3 — Voice Input (Whisper)
- whisper.cpp via JNI (MIT license), base.en model (~40MB)
- Mic button in keyboard toolbar
- Record → transcribe locally → insert text
- Reference WhisperInput (MIT) for Android integration pattern

### v0.4 — Swipe Typing v1 (Geometric)
- Detect swipe vs tap by movement threshold
- Dynamic time warping (DTW) against dictionary ideal paths
- Filter by first/last character, rank by DTW distance × word frequency
- Top 3 candidates in suggestion strip

### v1.0 — Ship
- Settings: toggle autocorrect/swipe/voice, key height, haptic feedback
- GitHub Actions CI (reuse re:ink pattern)
- Play Store listing

### v2.0 — Neural Swipe (Future)
- LSTM encoder-decoder trained on synthetic swipe data
- ONNX Runtime on-device inference
- Language model re-ranking

## Tech Decisions

- **Existing code stays Java**, new features written in Kotlin
- **whisper.cpp** via JNI (not TFLite) — better perf, MIT license
- **AOSP dictionaries** for autocorrect — Apache 2.0, commercially usable
- **Geometric DTW** for swipe v1 — no ML dependencies
- All dependencies must be Apache 2.0 or MIT (monetizable, no GPL)

## ProGuard

Release builds use ProGuard with explicit `-keep` rules for settings fragments that are loaded by name via reflection (see `app/proguard-rules.pro`). Any new fragment loaded via `FragmentUtils` must be added there.
