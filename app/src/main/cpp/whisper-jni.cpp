#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include "whisper.h"
#include "ggml-backend.h"

#define TAG "VoxWhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct JniWhisperContext {
    whisper_context * ctx;
    std::atomic<bool> aborted{false};
};

struct CallbackUserData {
    JNIEnv * env;
    jobject listener;
    jmethodID onProgressMethod;
    jmethodID onNewSegmentMethod;
};

static void whisper_android_log(enum ggml_log_level level, const char * text, void * /*user_data*/) {
    int prio = ANDROID_LOG_DEBUG;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: prio = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN:  prio = ANDROID_LOG_WARN;  break;
        case GGML_LOG_LEVEL_INFO:  prio = ANDROID_LOG_INFO;  break;
        default: break;
    }
    __android_log_write(prio, "whisper.cpp", text);
}

static void progress_callback(struct whisper_context * /*ctx*/, struct whisper_state * /*state*/, int progress, void * user_data) {
    auto * data = static_cast<CallbackUserData *>(user_data);
    if (!data || !data->listener || !data->onProgressMethod) return;
    data->env->CallVoidMethod(data->listener, data->onProgressMethod, static_cast<jint>(progress));
}

static void new_segment_callback(struct whisper_context * ctx, struct whisper_state * /*state*/, int n_new, void * user_data) {
    auto * data = static_cast<CallbackUserData *>(user_data);
    if (!data || !data->listener || !data->onNewSegmentMethod) return;

    const int n_segments = whisper_full_n_segments(ctx);
    int start_idx = n_segments - n_new;
    if (start_idx < 0) start_idx = 0;

    for (int i = start_idx; i < n_segments; ++i) {
        // whisper timestamps are in centiseconds
        jlong t0 = static_cast<jlong>(whisper_full_get_segment_t0(ctx, i)) * 10;
        jlong t1 = static_cast<jlong>(whisper_full_get_segment_t1(ctx, i)) * 10;
        const char * text = whisper_full_get_segment_text(ctx, i);

        jstring jtext = data->env->NewStringUTF(text ? text : "");
        data->env->CallVoidMethod(data->listener, data->onNewSegmentMethod, t0, t1, jtext);
        data->env->DeleteLocalRef(jtext);
    }
}

static bool abort_callback(void * user_data) {
    auto * jniCtx = static_cast<JniWhisperContext *>(user_data);
    return jniCtx && jniCtx->aborted.load();
}

extern "C" JNIEXPORT jlong JNICALL
Java_fr_schwanx_vox_core_whisper_WhisperEngine_initContext(
        JNIEnv * env,
        jobject /*thiz*/,
        jstring model_path,
        jstring native_lib_dir) {
    if (!model_path || !native_lib_dir) return 0;

    // Load the CPU backend variant best suited to this device, once per process.
    static std::once_flag backends_loaded;
    const char * lib_dir = env->GetStringUTFChars(native_lib_dir, nullptr);
    std::call_once(backends_loaded, [lib_dir]() {
        whisper_log_set(whisper_android_log, nullptr);
        ggml_backend_load_all_from_path(lib_dir);
        LOGI("Loaded %zu ggml backend(s) from %s", ggml_backend_reg_count(), lib_dir);
    });
    env->ReleaseStringUTFChars(native_lib_dir, lib_dir);

    if (ggml_backend_reg_count() == 0) {
        LOGE("No ggml backend could be loaded");
        return 0;
    }

    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (!path) return 0;

    LOGI("Initializing whisper context from model path: %s", path);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context * ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (!ctx) {
        LOGE("Failed to load whisper model from file");
        return 0;
    }

    auto * jniCtx = new JniWhisperContext();
    jniCtx->ctx = ctx;
    return reinterpret_cast<jlong>(jniCtx);
}

extern "C" JNIEXPORT jint JNICALL
Java_fr_schwanx_vox_core_whisper_WhisperEngine_fullTranscribe(
        JNIEnv * env,
        jobject /*thiz*/,
        jlong context_ptr,
        jfloatArray pcm_data,
        jint num_threads,
        jstring language,
        jboolean translate,
        jobject listener) {
    if (context_ptr == 0) return -1;

    auto * jniCtx = reinterpret_cast<JniWhisperContext *>(context_ptr);
    if (!jniCtx->ctx) return -1;

    jniCtx->aborted.store(false);

    const jsize n_samples = env->GetArrayLength(pcm_data);
    jfloat * samples = env->GetFloatArrayElements(pcm_data, nullptr);
    if (!samples) return -1;

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = num_threads > 0 ? num_threads : 4;
    params.translate = translate;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    // Like the official whisper.android example: don't feed the previous text
    // back as a prompt. It makes decoding much faster on long audio and avoids
    // repetition loops, at the cost of a little consistency between windows.
    params.no_context = true;

    // "auto" lets whisper detect the spoken language before transcribing.
    // Note: params.detect_language must stay false, otherwise whisper only
    // detects the language and returns without transcribing anything.
    const char * lang_str = language ? env->GetStringUTFChars(language, nullptr) : nullptr;
    params.language = (lang_str && lang_str[0] != '\0') ? lang_str : "auto";
    params.detect_language = false;

    CallbackUserData cbData{};
    cbData.env = env;
    cbData.listener = listener;
    if (listener) {
        jclass listenerClass = env->GetObjectClass(listener);
        cbData.onProgressMethod = env->GetMethodID(listenerClass, "onProgress", "(I)V");
        cbData.onNewSegmentMethod = env->GetMethodID(listenerClass, "onNewSegment", "(JJLjava/lang/String;)V");
        env->DeleteLocalRef(listenerClass);
    }

    params.progress_callback = progress_callback;
    params.progress_callback_user_data = &cbData;
    params.new_segment_callback = new_segment_callback;
    params.new_segment_callback_user_data = &cbData;
    params.abort_callback = abort_callback;
    params.abort_callback_user_data = jniCtx;

    LOGI("Starting whisper transcription: %d samples, %d threads, language=%s",
         n_samples, params.n_threads, params.language);
    int res = whisper_full(jniCtx->ctx, params, samples, n_samples);
    if (jniCtx->aborted.load()) {
        res = -2;
    }
    LOGI("Whisper transcription finished with code %d", res);
    whisper_print_timings(jniCtx->ctx);

    if (lang_str) {
        env->ReleaseStringUTFChars(language, lang_str);
    }
    env->ReleaseFloatArrayElements(pcm_data, samples, JNI_ABORT);

    return res;
}

extern "C" JNIEXPORT jstring JNICALL
Java_fr_schwanx_vox_core_whisper_WhisperEngine_detectedLanguage(
        JNIEnv * env,
        jobject /*thiz*/,
        jlong context_ptr) {
    if (context_ptr == 0) return nullptr;
    auto * jniCtx = reinterpret_cast<JniWhisperContext *>(context_ptr);
    const int lang_id = whisper_full_lang_id(jniCtx->ctx);
    const char * lang = lang_id >= 0 ? whisper_lang_str(lang_id) : nullptr;
    return lang ? env->NewStringUTF(lang) : nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_fr_schwanx_vox_core_whisper_WhisperEngine_abort(
        JNIEnv * /*env*/,
        jobject /*thiz*/,
        jlong context_ptr) {
    if (context_ptr == 0) return;
    auto * jniCtx = reinterpret_cast<JniWhisperContext *>(context_ptr);
    LOGI("Aborting whisper transcription");
    jniCtx->aborted.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_fr_schwanx_vox_core_whisper_WhisperEngine_freeContext(
        JNIEnv * /*env*/,
        jobject /*thiz*/,
        jlong context_ptr) {
    if (context_ptr == 0) return;
    auto * jniCtx = reinterpret_cast<JniWhisperContext *>(context_ptr);
    if (jniCtx->ctx) {
        whisper_free(jniCtx->ctx);
    }
    delete jniCtx;
    LOGI("Whisper context freed successfully");
}
