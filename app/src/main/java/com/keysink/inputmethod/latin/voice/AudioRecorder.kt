package com.keysink.inputmethod.latin.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorder {

    enum class RecordingError {
        MIC_UNAVAILABLE,
        RECORDING_FAILED
    }

    interface Callback {
        fun onRecordingComplete(audioData: FloatArray)
        fun onRecordingError(error: RecordingError)
        fun onMaxDurationReached()
    }

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun startRecording(callback: Callback) {
        if (isRecording.get()) return

        executor.execute {
            try {
                val bufferSize = maxOf(
                    AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ),
                    SAMPLE_RATE * 2
                )

                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    callback.onRecordingError(RecordingError.MIC_UNAVAILABLE)
                    return@execute
                }

                audioRecord = record
                record.startRecording()
                isRecording.set(true)

                val allSamples = ShortArray(MAX_SAMPLES)
                var totalSamples = 0
                val readBuffer = ShortArray(bufferSize / 2)

                while (isRecording.get() && totalSamples < MAX_SAMPLES) {
                    val read = record.read(readBuffer, 0, readBuffer.size)
                    if (read > 0) {
                        val remaining = MAX_SAMPLES - totalSamples
                        val toCopy = minOf(read, remaining)
                        System.arraycopy(readBuffer, 0, allSamples, totalSamples, toCopy)
                        totalSamples += toCopy
                    } else if (read < 0) {
                        isRecording.set(false)
                        record.stop()
                        record.release()
                        audioRecord = null
                        callback.onRecordingError(RecordingError.RECORDING_FAILED)
                        return@execute
                    }
                }

                val hitMax = totalSamples >= MAX_SAMPLES
                record.stop()
                record.release()
                audioRecord = null
                isRecording.set(false)

                if (hitMax) {
                    callback.onMaxDurationReached()
                }

                val floatData = convertToFloat(allSamples, totalSamples)
                callback.onRecordingComplete(floatData)

            } catch (e: SecurityException) {
                isRecording.set(false)
                callback.onRecordingError(RecordingError.MIC_UNAVAILABLE)
            } catch (e: Exception) {
                isRecording.set(false)
                callback.onRecordingError(RecordingError.RECORDING_FAILED)
            }
        }
    }

    fun stopRecording() {
        isRecording.set(false)
    }

    fun release() {
        stopRecording()
        audioRecord?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        audioRecord = null
        executor.shutdown()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_DURATION_SECONDS = 120
        const val MAX_SAMPLES = SAMPLE_RATE * MAX_DURATION_SECONDS

        fun convertToFloat(shorts: ShortArray, sampleCount: Int): FloatArray {
            val floats = FloatArray(sampleCount)
            for (i in 0 until sampleCount) {
                floats[i] = shorts[i].toFloat() / Short.MAX_VALUE.toFloat()
            }
            return floats
        }
    }
}
