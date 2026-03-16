package com.keysink.inputmethod.latin.voice

enum class WhisperModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val displaySize: String
) {
    TINY_EN(
        id = "tiny_en",
        displayName = "Tiny (English)",
        fileName = "ggml-tiny.en-q5_1.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q5_1.bin",
        sha256 = "c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b",
        displaySize = "~31 MB"
    ),
    BASE_EN(
        id = "base_en",
        displayName = "Base (English)",
        fileName = "ggml-base.en-q5_1.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q5_1.bin",
        sha256 = "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f",
        displaySize = "~57 MB"
    ),
    SMALL_EN(
        id = "small_en",
        displayName = "Small (English)",
        fileName = "ggml-small.en-q5_1.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en-q5_1.bin",
        sha256 = "bfdff4894dcb76bbf647d56263ea2a96645423f1669176f4844a1bf8e478ad30",
        displaySize = "~181 MB"
    );

    companion object {
        val DEFAULT = BASE_EN
        fun fromId(id: String): WhisperModel = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
