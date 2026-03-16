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
- JDK: `JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"`
- No AndroidX (`android.useAndroidX=false`)
- `v02-suggestions-wip` branch preserves suggestion/dictionary code (Kotlin + NDK) for future work

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

## Boox E-Ink Device Constraints (CRITICAL)

These are non-negotiable hardware/firmware limitations of the Boox NoteAir 5C:

1. **No extra views in `input_view.xml`.** The IME must have exactly ONE child view (`MainKeyboardView`). Any additional view — even `visibility="gone"` — crashes the host app. The Android `setCandidatesView` API also crashes. Draw everything on the `MainKeyboardView` canvas.

2. **No `setComposingText()` or `finishComposingText()`.** Boox editors crash on composing text spans. Commit characters directly and track words in memory.

3. **Dictionary JNI calls must be off the main thread.** Use a background `ExecutorService` and post results back via `Handler`.

## E-Ink Optimizations (Current)

- **Default theme**: `KeyboardTheme.EInk` (ID 7) — pure white background, black text, gray key borders
- **Key press feedback**: Border thickens from 1dp gray to 2.5dp black (no color fill change)
- **Key preview popups**: Disabled (height/width/offset all 0dp) — prevents ghosting
- **No animations**: No key preview dismiss animation in e-ink theme

## Roadmap

### v1.0 — Ship
- Settings: toggle voice, key height, haptic feedback
- GitHub Actions CI (reuse re:ink pattern)
- Play Store listing

### v2.0 — Autocorrect + Suggestions (Future)
- Suggestion code preserved on `v02-suggestions-wip` branch
- Re-add AOSP suggestion strip from LatinIME
- Integrate AOSP dictionaries (Apache 2.0) — English first
- E-ink styled suggestion bar: plain text, bold center candidate, no animations

### v3.0 — Swipe Typing (Future)
- Geometric DTW for v1, neural encoder-decoder for v2
- No ML dependencies for v1

## Tech Decisions

- **Existing code stays Java**, new features written in Kotlin
- **whisper.cpp** via JNI (not TFLite) — better perf, MIT license
- All dependencies must be Apache 2.0 or MIT (monetizable, no GPL)

## ProGuard

Release builds use ProGuard with explicit `-keep` rules for settings fragments that are loaded by name via reflection (see `app/proguard-rules.pro`). Any new fragment loaded via `FragmentUtils` must be added there.
