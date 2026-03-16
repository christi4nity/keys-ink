package com.keysink.inputmethod.latin.voice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AudioRecorderTest {

    @Test
    fun `convertToFloat normalizes short samples to -1 to 1 range`() {
        val shorts = shortArrayOf(0, Short.MAX_VALUE, Short.MIN_VALUE, 16384)
        val floats = AudioRecorder.convertToFloat(shorts, shorts.size)
        assertEquals(0f, floats[0], 0.001f)
        assertEquals(1f, floats[1], 0.001f)
        assertEquals(-1f, floats[2], 0.001f)
        assertEquals(0.5f, floats[3], 0.01f)
    }

    @Test
    fun `convertToFloat respects sampleCount parameter`() {
        val shorts = shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE)
        val floats = AudioRecorder.convertToFloat(shorts, 2)
        assertEquals(2, floats.size)
    }

    @Test
    fun `convertToFloat handles empty input`() {
        val floats = AudioRecorder.convertToFloat(shortArrayOf(), 0)
        assertEquals(0, floats.size)
    }

    @Test
    fun `SAMPLE_RATE is 16000`() {
        assertEquals(16000, AudioRecorder.SAMPLE_RATE)
    }

    @Test
    fun `MAX_DURATION_SECONDS is 120`() {
        assertEquals(120, AudioRecorder.MAX_DURATION_SECONDS)
    }

    @Test
    fun `maxSamples equals SAMPLE_RATE times MAX_DURATION_SECONDS`() {
        assertEquals(
            AudioRecorder.SAMPLE_RATE * AudioRecorder.MAX_DURATION_SECONDS,
            AudioRecorder.MAX_SAMPLES
        )
    }
}
