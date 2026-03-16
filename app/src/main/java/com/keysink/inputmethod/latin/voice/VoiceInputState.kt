package com.keysink.inputmethod.latin.voice

enum class VoiceInputState(
    val showsMicIcon: Boolean,
    val statusText: String?,
    val showsAnimatedDots: Boolean,
    val hidesSuggestions: Boolean
) {
    IDLE(showsMicIcon = true, statusText = null, showsAnimatedDots = false, hidesSuggestions = false),
    NOT_READY(showsMicIcon = true, statusText = null, showsAnimatedDots = false, hidesSuggestions = false),
    RECORDING(showsMicIcon = false, statusText = "Recording", showsAnimatedDots = true, hidesSuggestions = true),
    TRANSCRIBING(showsMicIcon = true, statusText = "Transcribing", showsAnimatedDots = true, hidesSuggestions = true),
    ERROR(showsMicIcon = true, statusText = null, showsAnimatedDots = false, hidesSuggestions = true)
}
