#ifndef WHISPER_JNI_H
#define WHISPER_JNI_H

#include <jni.h>

extern "C" {
JNIEXPORT jlong JNICALL Java_com_keysink_inputmethod_latin_voice_WhisperEngine_loadModelNative(JNIEnv *, jclass, jstring);
JNIEXPORT jstring JNICALL Java_com_keysink_inputmethod_latin_voice_WhisperEngine_transcribeNative(JNIEnv *, jclass, jlong, jfloatArray);
JNIEXPORT void JNICALL Java_com_keysink_inputmethod_latin_voice_WhisperEngine_releaseModelNative(JNIEnv *, jclass, jlong);
}

#endif
