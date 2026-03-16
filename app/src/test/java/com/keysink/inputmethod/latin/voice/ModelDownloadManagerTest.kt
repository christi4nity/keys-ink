package com.keysink.inputmethod.latin.voice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class ModelDownloadManagerTest {

    @Test
    fun `DOWNLOAD_URL points to huggingface`() {
        assertTrue(ModelDownloadManager.DOWNLOAD_URL.contains("huggingface.co"))
        assertTrue(ModelDownloadManager.DOWNLOAD_URL.contains("ggml-base.en.bin"))
    }

    @Test
    fun `EXPECTED_SHA256 is 64 hex characters`() {
        assertEquals(64, ModelDownloadManager.EXPECTED_SHA256.length)
        assertTrue(ModelDownloadManager.EXPECTED_SHA256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `getModelFile returns correct path`() {
        val filesDir = File("/data/data/com.keysink.inputmethod/files")
        val modelFile = ModelDownloadManager.getModelFile(filesDir)
        assertEquals(
            "/data/data/com.keysink.inputmethod/files/whisper/ggml-base.en.bin",
            modelFile.path
        )
    }

    @Test
    fun `isModelDownloaded returns false when file does not exist`() {
        val fakeDir = File("/nonexistent")
        assertFalse(ModelDownloadManager.isModelDownloaded(fakeDir))
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
