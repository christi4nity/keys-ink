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
