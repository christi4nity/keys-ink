package com.keysink.inputmethod.latin.voice

import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

object WhisperEngine {

    const val WHISPER_DIR = "whisper"

    var isAvailable: Boolean = false
        private set

    @Volatile
    private var loadedModel: WhisperModel? = null

    private val modelHandle = AtomicLong(0)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        try {
            System.loadLibrary("whisper_jni")
            isAvailable = true
        } catch (_: UnsatisfiedLinkError) {
            isAvailable = false
        }
    }

    fun isModelLoaded(): Boolean = modelHandle.get() != 0L

    fun getLoadedModel(): WhisperModel? = loadedModel

    fun getModelFile(filesDir: File, model: WhisperModel): File {
        return File(filesDir, "$WHISPER_DIR/${model.fileName}")
    }

    fun loadModel(modelPath: String, model: WhisperModel, callback: (Boolean) -> Unit) {
        if (!isAvailable) {
            callback(false)
            return
        }
        executor.execute {
            val handle = loadModelNative(modelPath)
            modelHandle.set(handle)
            loadedModel = if (handle != 0L) model else null
            callback(handle != 0L)
        }
    }

    fun reloadIfNeeded(filesDir: File, model: WhisperModel, callback: (Boolean) -> Unit) {
        if (isModelLoaded() && loadedModel == model) {
            callback(true)
            return
        }
        releaseModel()
        val modelFile = getModelFile(filesDir, model)
        loadModel(modelFile.absolutePath, model, callback)
    }

    fun transcribe(audioData: FloatArray, callback: (String?) -> Unit) {
        val handle = modelHandle.get()
        if (!isAvailable || handle == 0L) {
            callback(null)
            return
        }
        executor.execute {
            val result = transcribeNative(handle, audioData)
            callback(result.ifEmpty { null })
        }
    }

    fun releaseModel() {
        val handle = modelHandle.getAndSet(0)
        loadedModel = null
        if (handle != 0L && isAvailable) {
            executor.execute {
                releaseModelNative(handle)
            }
        }
    }

    fun shutdown() {
        releaseModel()
        executor.shutdown()
    }

    @JvmStatic
    private external fun loadModelNative(modelPath: String): Long

    @JvmStatic
    private external fun transcribeNative(handle: Long, audioData: FloatArray): String

    @JvmStatic
    private external fun releaseModelNative(handle: Long)
}
