package com.keysink.inputmethod.latin.voice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WhisperEngineTest {

    @Test
    fun `isAvailable returns false before library load`() {
        // In unit tests, native library won't be present
        assertFalse(WhisperEngine.isAvailable)
    }

    @Test
    fun `isModelLoaded returns false initially`() {
        assertFalse(WhisperEngine.isModelLoaded())
    }

    @Test
    fun `MODEL_FILE_NAME is correct`() {
        assertEquals("ggml-base.en-q5_1.bin", WhisperEngine.MODEL_FILE_NAME)
    }

    @Test
    fun `WHISPER_DIR is correct`() {
        assertEquals("whisper", WhisperEngine.WHISPER_DIR)
    }
}
