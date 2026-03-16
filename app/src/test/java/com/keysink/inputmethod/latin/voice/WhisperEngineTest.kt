package com.keysink.inputmethod.latin.voice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class WhisperEngineTest {

    @Test
    fun `isAvailable returns false before library load`() {
        assertFalse(WhisperEngine.isAvailable)
    }

    @Test
    fun `isModelLoaded returns false initially`() {
        assertFalse(WhisperEngine.isModelLoaded())
    }

    @Test
    fun `getLoadedModel returns null initially`() {
        assertNull(WhisperEngine.getLoadedModel())
    }

    @Test
    fun `WHISPER_DIR is correct`() {
        assertEquals("whisper", WhisperEngine.WHISPER_DIR)
    }

    @Test
    fun `getModelFile returns correct path for each variant`() {
        val filesDir = File("/data/data/com.keysink.inputmethod/files")
        WhisperModel.entries.forEach { model ->
            val file = WhisperEngine.getModelFile(filesDir, model)
            assertEquals(
                "/data/data/com.keysink.inputmethod/files/whisper/${model.fileName}",
                file.path
            )
        }
    }
}
