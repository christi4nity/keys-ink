package com.keysink.inputmethod.latin.voice

import com.keysink.inputmethod.R
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VoiceInputStateTest {

    @Test
    fun `IDLE is the initial default state`() {
        assertEquals(VoiceInputState.IDLE, VoiceInputState.values()[0])
    }

    @Test
    fun `all four states exist`() {
        val states = VoiceInputState.values()
        assertEquals(4, states.size)
        assertTrue(states.contains(VoiceInputState.IDLE))
        assertTrue(states.contains(VoiceInputState.RECORDING))
        assertTrue(states.contains(VoiceInputState.TRANSCRIBING))
        assertTrue(states.contains(VoiceInputState.ERROR))
    }

    @Test
    fun `showsMicIcon returns true for IDLE, TRANSCRIBING, ERROR`() {
        assertTrue(VoiceInputState.IDLE.showsMicIcon)
        assertTrue(VoiceInputState.TRANSCRIBING.showsMicIcon)
        assertTrue(VoiceInputState.ERROR.showsMicIcon)
    }

    @Test
    fun `showsMicIcon returns false for RECORDING`() {
        assertFalse(VoiceInputState.RECORDING.showsMicIcon)
    }

    @Test
    fun `statusTextResId returns correct resource IDs`() {
        assertEquals(0, VoiceInputState.IDLE.statusTextResId)
        assertEquals(R.string.voice_status_recording, VoiceInputState.RECORDING.statusTextResId)
        assertEquals(R.string.voice_status_transcribing, VoiceInputState.TRANSCRIBING.statusTextResId)
        assertEquals(0, VoiceInputState.ERROR.statusTextResId)
    }

    @Test
    fun `showsAnimatedDots returns true only for RECORDING and TRANSCRIBING`() {
        assertFalse(VoiceInputState.IDLE.showsAnimatedDots)
        assertTrue(VoiceInputState.RECORDING.showsAnimatedDots)
        assertTrue(VoiceInputState.TRANSCRIBING.showsAnimatedDots)
        assertFalse(VoiceInputState.ERROR.showsAnimatedDots)
    }

    @Test
    fun `hidesSuggestions returns true for RECORDING, TRANSCRIBING, ERROR`() {
        assertFalse(VoiceInputState.IDLE.hidesSuggestions)
        assertTrue(VoiceInputState.RECORDING.hidesSuggestions)
        assertTrue(VoiceInputState.TRANSCRIBING.hidesSuggestions)
        assertTrue(VoiceInputState.ERROR.hidesSuggestions)
    }
}
