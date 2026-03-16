package com.keysink.inputmethod.latin.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.keysink.inputmethod.R
import java.io.File

class VoiceInputController(
    private val context: Context,
    private val filesDir: File
) {

    enum class PrerequisiteResult {
        READY,
        NEEDS_PERMISSION,
        NEEDS_MODEL,
        NATIVE_UNAVAILABLE
    }

    interface Callback {
        fun onStateChanged(state: VoiceInputState, errorMessage: String?)
        fun onTranscriptionResult(text: String)
        fun launchVoiceSettings()
    }

    @Volatile private var state = State.IDLE
    private var callback: Callback? = null
    private val audioRecorder = AudioRecorder()
    private val mainHandler = Handler(Looper.getMainLooper())

    private enum class State {
        IDLE, RECORDING, TRANSCRIBING, ERROR
    }

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    fun onMicKeyPressed() {
        when (state) {
            State.IDLE -> handleIdleTap()
            State.RECORDING -> stopRecording()
            State.TRANSCRIBING -> { /* no-op */ }
            State.ERROR -> handleIdleTap()
        }
    }

    private fun handleIdleTap() {
        val prereq = checkPrerequisites(
            hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
            modelExists = ModelDownloadManager.isModelDownloaded(filesDir),
            nativeAvailable = WhisperEngine.isAvailable
        )

        when (prereq) {
            PrerequisiteResult.READY -> startRecording()
            PrerequisiteResult.NATIVE_UNAVAILABLE -> {
                transitionTo(State.ERROR, context.getString(R.string.voice_error_unavailable))
            }
            PrerequisiteResult.NEEDS_PERMISSION,
            PrerequisiteResult.NEEDS_MODEL -> {
                callback?.launchVoiceSettings()
            }
        }
    }

    private fun startRecording() {
        if (!WhisperEngine.isModelLoaded()) {
            val modelFile = WhisperEngine.getModelFile(filesDir)
            WhisperEngine.loadModel(modelFile.absolutePath) { success ->
                mainHandler.post {
                    if (success) {
                        beginRecording()
                    } else {
                        transitionTo(State.ERROR, context.getString(R.string.voice_error_model_corrupted))
                    }
                }
            }
        } else {
            beginRecording()
        }
    }

    private fun beginRecording() {
        transitionTo(State.RECORDING)
        audioRecorder.startRecording(object : AudioRecorder.Callback {
            override fun onRecordingComplete(audioData: FloatArray) {
                mainHandler.post {
                    transitionTo(State.TRANSCRIBING)
                    transcribe(audioData)
                }
            }

            override fun onRecordingError(error: AudioRecorder.RecordingError) {
                val message = when (error) {
                    AudioRecorder.RecordingError.MIC_UNAVAILABLE ->
                        context.getString(R.string.voice_error_mic_unavailable)
                    AudioRecorder.RecordingError.RECORDING_FAILED ->
                        context.getString(R.string.voice_error_recording_failed)
                }
                mainHandler.post {
                    transitionTo(State.ERROR, message)
                }
            }

            override fun onMaxDurationReached() {
                // Recording will auto-complete via onRecordingComplete
            }
        })
    }

    private fun stopRecording() {
        audioRecorder.stopRecording()
    }

    private fun transcribe(audioData: FloatArray) {
        WhisperEngine.transcribe(audioData) { result ->
            mainHandler.post {
                if (result != null && result.isNotEmpty()) {
                    callback?.onTranscriptionResult(result)
                    transitionTo(State.IDLE)
                } else {
                    transitionTo(State.ERROR, context.getString(R.string.voice_error_transcription_failed))
                }
            }
        }
    }

    private fun transitionTo(newState: State, errorMessage: String? = null) {
        state = newState
        val voiceState = when (newState) {
            State.IDLE -> VoiceInputState.IDLE
            State.RECORDING -> VoiceInputState.RECORDING
            State.TRANSCRIBING -> VoiceInputState.TRANSCRIBING
            State.ERROR -> VoiceInputState.ERROR
        }
        callback?.onStateChanged(voiceState, errorMessage)
    }

    fun onStartInputView() {
        if (state == State.RECORDING || state == State.TRANSCRIBING) {
            cancelAndReset()
        }
    }

    fun destroy() {
        cancelAndReset()
        audioRecorder.release()
        WhisperEngine.shutdown()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun cancelAndReset() {
        audioRecorder.stopRecording()
        state = State.IDLE
        callback?.onStateChanged(VoiceInputState.IDLE, null)
    }

    companion object {
        fun canTranscribe(): Boolean {
            return WhisperEngine.isAvailable && WhisperEngine.isModelLoaded()
        }

        fun checkPrerequisites(
            hasPermission: Boolean,
            modelExists: Boolean,
            nativeAvailable: Boolean
        ): PrerequisiteResult {
            if (!nativeAvailable) return PrerequisiteResult.NATIVE_UNAVAILABLE
            if (!hasPermission) return PrerequisiteResult.NEEDS_PERMISSION
            if (!modelExists) return PrerequisiteResult.NEEDS_MODEL
            return PrerequisiteResult.READY
        }
    }
}
