package com.keysink.inputmethod.latin.voice

import com.keysink.inputmethod.R

enum class VoiceInputState(
    val showsMicIcon: Boolean,
    val statusTextResId: Int,
    val showsAnimatedDots: Boolean,
    val hidesSuggestions: Boolean
) {
    IDLE(showsMicIcon = true, statusTextResId = 0, showsAnimatedDots = false, hidesSuggestions = false),
    RECORDING(showsMicIcon = false, statusTextResId = R.string.voice_status_recording, showsAnimatedDots = true, hidesSuggestions = true),
    TRANSCRIBING(showsMicIcon = true, statusTextResId = R.string.voice_status_transcribing, showsAnimatedDots = true, hidesSuggestions = true),
    ERROR(showsMicIcon = true, statusTextResId = 0, showsAnimatedDots = false, hidesSuggestions = true)
}
