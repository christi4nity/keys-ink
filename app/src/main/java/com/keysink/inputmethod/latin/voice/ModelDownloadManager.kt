package com.keysink.inputmethod.latin.voice

import android.content.Context
import com.keysink.inputmethod.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ModelDownloadManager(private val model: WhisperModel) {

    sealed class DownloadState {
        object NotDownloaded : DownloadState()
        data class Downloading(val progress: Int) : DownloadState()
        object Complete : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    interface Callback {
        fun onStateChanged(state: DownloadState)
    }

    private val isCancelled = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun download(context: Context, filesDir: File, callback: Callback) {
        isCancelled.set(false)
        callback.onStateChanged(DownloadState.Downloading(0))

        executor.execute {
            try {
                val modelDir = File(filesDir, WhisperEngine.WHISPER_DIR)
                modelDir.mkdirs()
                val modelFile = File(modelDir, model.fileName)
                val tempFile = File(modelDir, "${model.fileName}.tmp")

                val url = URL(model.downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true

                try {
                    connection.connect()
                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        callback.onStateChanged(DownloadState.Failed(
                            context.getString(R.string.voice_error_download_http, responseCode)))
                        return@execute
                    }

                    val totalBytes = connection.contentLengthLong
                    var downloadedBytes = 0L

                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (isCancelled.get()) {
                                    tempFile.delete()
                                    callback.onStateChanged(DownloadState.NotDownloaded)
                                    return@execute
                                }
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                if (totalBytes > 0) {
                                    val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                    callback.onStateChanged(DownloadState.Downloading(progress))
                                }
                            }
                        }
                    }

                    // Verify checksum
                    val actualHash = sha256(tempFile)
                    if (actualHash != model.sha256) {
                        tempFile.delete()
                        callback.onStateChanged(DownloadState.Failed(
                            context.getString(R.string.voice_error_model_corrupted)))
                        return@execute
                    }

                    // Atomic rename
                    if (tempFile.renameTo(modelFile)) {
                        callback.onStateChanged(DownloadState.Complete)
                    } else {
                        tempFile.delete()
                        callback.onStateChanged(DownloadState.Failed(
                            context.getString(R.string.voice_error_download_save)))
                    }

                } finally {
                    connection.disconnect()
                }

            } catch (e: IOException) {
                callback.onStateChanged(DownloadState.Failed(
                    context.getString(R.string.voice_error_download_network)))
            } catch (e: Exception) {
                callback.onStateChanged(DownloadState.Failed(
                    context.getString(R.string.voice_error_download_generic)))
            }
        }
    }

    fun cancel() {
        isCancelled.set(true)
    }

    fun shutdown() {
        cancel()
        executor.shutdown()
    }

    companion object {
        fun getModelFile(filesDir: File, model: WhisperModel): File {
            return File(filesDir, "${WhisperEngine.WHISPER_DIR}/${model.fileName}")
        }

        fun isModelDownloaded(filesDir: File, model: WhisperModel): Boolean {
            return getModelFile(filesDir, model).exists()
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
