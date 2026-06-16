#include <jni.h>
#include <string>
#include "whisper.h"

// JNI bridge between Kotlin (com.whispercpp.whisper.WhisperLib) and whisper.cpp.

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_initContextFromFile(
        JNIEnv *env, jobject /*thiz*/, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_whispercpp_whisper_WhisperLib_freeContext(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong contextPtr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    if (ctx != nullptr) whisper_free(ctx);
}

JNIEXPORT void JNICALL
Java_com_whispercpp_whisper_WhisperLib_fullTranscribe(
        JNIEnv *env, jobject /*thiz*/, jlong contextPtr, jint numThreads,
        jfloatArray audioData, jstring language) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    if (ctx == nullptr) return;

    jsize n = env->GetArrayLength(audioData);
    jfloat *samples = env->GetFloatArrayElements(audioData, nullptr);

    // "auto" lets whisper detect the language; "en"/"bn"/... pin it.
    const char *lang = (language != nullptr)
                       ? env->GetStringUTFChars(language, nullptr)
                       : "auto";

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads        = numThreads;
    params.translate        = false;
    params.language         = lang;
    params.no_context       = true;
    params.single_segment   = false;
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;

    whisper_reset_timings(ctx);
    whisper_full(ctx, params, samples, n);

    if (language != nullptr) env->ReleaseStringUTFChars(language, lang);
    env->ReleaseFloatArrayElements(audioData, samples, JNI_ABORT);
}

JNIEXPORT jint JNICALL
Java_com_whispercpp_whisper_WhisperLib_getTextSegmentCount(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong contextPtr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    if (ctx == nullptr) return 0;
    return whisper_full_n_segments(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_getTextSegment(
        JNIEnv *env, jobject /*thiz*/, jlong contextPtr, jint index) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    if (ctx == nullptr) return env->NewStringUTF("");
    const char *text = whisper_full_get_segment_text(ctx, index);
    return env->NewStringUTF(text == nullptr ? "" : text);
}

} // extern "C"
