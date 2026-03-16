package com.keysink.inputmethod.latin.voice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class ModelDownloadManagerTest {

    @Test
    fun `getModelFile returns correct path for each variant`() {
        val filesDir = File("/data/data/com.keysink.inputmethod/files")
        WhisperModel.entries.forEach { model ->
            val modelFile = ModelDownloadManager.getModelFile(filesDir, model)
            assertEquals(
                "/data/data/com.keysink.inputmethod/files/whisper/${model.fileName}",
                modelFile.path
            )
        }
    }

    @Test
    fun `isModelDownloaded returns false when file does not exist`() {
        val fakeDir = File("/nonexistent")
        WhisperModel.entries.forEach { model ->
            assertFalse(ModelDownloadManager.isModelDownloaded(fakeDir, model))
        }
    }

    @Test
    fun `DownloadState has correct variants`() {
        assertNotNull(ModelDownloadManager.DownloadState.NotDownloaded)
        assertNotNull(ModelDownloadManager.DownloadState.Downloading(50))
        assertNotNull(ModelDownloadManager.DownloadState.Complete)
        assertNotNull(ModelDownloadManager.DownloadState.Failed("error"))
    }

    @Test
    fun `Downloading state holds progress value`() {
        val state = ModelDownloadManager.DownloadState.Downloading(75)
        assertEquals(75, state.progress)
    }

    @Test
    fun `Failed state holds error message`() {
        val state = ModelDownloadManager.DownloadState.Failed("Network error")
        assertEquals("Network error", state.message)
    }
}
