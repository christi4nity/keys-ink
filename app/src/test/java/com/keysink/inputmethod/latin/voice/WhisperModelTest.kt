package com.keysink.inputmethod.latin.voice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WhisperModelTest {

    @Test
    fun `fromId returns correct model for each id`() {
        assertEquals(WhisperModel.TINY_EN, WhisperModel.fromId("tiny_en"))
        assertEquals(WhisperModel.BASE_EN, WhisperModel.fromId("base_en"))
        assertEquals(WhisperModel.SMALL_EN, WhisperModel.fromId("small_en"))
    }

    @Test
    fun `fromId returns DEFAULT for unknown id`() {
        assertEquals(WhisperModel.DEFAULT, WhisperModel.fromId("unknown"))
        assertEquals(WhisperModel.DEFAULT, WhisperModel.fromId(""))
    }

    @Test
    fun `DEFAULT is BASE_EN`() {
        assertEquals(WhisperModel.BASE_EN, WhisperModel.DEFAULT)
    }

    @Test
    fun `all ids are unique`() {
        val ids = WhisperModel.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `all filenames are unique`() {
        val filenames = WhisperModel.entries.map { it.fileName }
        assertEquals(filenames.size, filenames.toSet().size)
    }

    @Test
    fun `all download URLs point to huggingface`() {
        WhisperModel.entries.forEach { model ->
            assertTrue(model.downloadUrl.contains("huggingface.co"), "${model.id} URL missing huggingface.co")
            assertTrue(model.downloadUrl.contains(model.fileName), "${model.id} URL missing filename")
        }
    }

    @Test
    fun `all SHA256 hashes are 64 hex characters`() {
        WhisperModel.entries.forEach { model ->
            assertEquals(64, model.sha256.length, "${model.id} SHA256 wrong length")
            assertTrue(model.sha256.matches(Regex("[0-9a-f]{64}")), "${model.id} SHA256 not hex")
        }
    }
}
