package com.keysink.inputmethod.latin.voice

import org.junit.jupiter.api.Assertions.*
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
