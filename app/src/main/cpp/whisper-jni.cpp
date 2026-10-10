#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <string>
#include "whisper.h"

#define TAG "VoxWhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
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
    JniWhisperContext * jniCtx;
};

static bool progress_callback(struct whisper_context * ctx, struct whisper_state * state, int progress, void * user_data) {
    auto * data = static_cast<CallbackUserData *>(user_data);
    if (!data) return true;

    if (data->jniCtx && data->jniCtx->aborted.load()) {
        return false; // Abort
    }

    if (data->env && data->listener && data->onProgressMethod) {
        data->env->CallVoidMethod(data->listener, data->onProgressMethod, static_cast<jint>(progress));
    }
    return true;
}

static void new_segment_callback(struct whisper_context * ctx, struct whisper_state * state, int n_new, void * user_data) {
    auto * data = static_cast<CallbackUserData *>(user_data);
    if (!data || !data->env || !data->listener || !data->onNewSegmentMethod) return;

    int n_segments = whisper_full_n_segments(ctx);
    int start_idx = n_segments - n_new;
    if (start_idx < 0) start_idx = 0;

    for (int i = start_idx; i < n_segments; ++i) {
        jlong t0 = whisper_full_get_segment_t0(ctx, i);
        jlong t1 = whisper_full_get_segment_t1(ctx, i);
        const char * text = whisper_full_get_segment_text(ctx, i);

        jstring jtext = data->env->NewStringUTF(text ? text : "");
        data->env->CallVoidMethod(data->listener, data->onNewSegmentMethod, t0, t1, jtext);
        data->env->DeleteLocalRef(jtext);
    }
}

static bool abort_callback(void * user_data) {
    auto * jniCtx = static_cast<JniWhisperContext *>(user_data);
    return jniCtx ? jniCtx->aborted.load() : false;
}

extern "C" JNIEXPORT jlong JNICALL
Java_fr_schwanx_vox_core_native_WhisperEngine_initContext(
        JNIEnv * env,
        jobject thiz,
        jstring model_path) {
    if (!model_path) return 0;

    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (!path) return 0;

    LOGI("Initializing whisper context from model path: %s", path);
    whisper_context * ctx = whisper_init_from_file(path);
    env->ReleaseStringUTFChars(model_path, path);

    if (!ctx) {
        LOGE("Failed to load whisper model from file");
        return 0;
    }

    auto * jniCtx = new JniWhisperContext();
    jniCtx->ctx = ctx;
    jniCtx->aborted.store(false);

    return reinterpret_cast<jlong>(jniCtx);
}

extern "C" JNIEXPORT jint JNICALL
Java_fr_schwanx_vox_core_native_WhisperEngine_fullTranscribe(
        JNIEnv * env,
        jobject thiz,
        jlong context_ptr,
        jfloatArray pcm_data,
        jint num_threads,
        jstring language,
        jboolean translate,
        jobject listener) {
    if (context_ptr == 0) return -1;

    auto * jniCtx = reinterpret_cast<JniWhisperContext *>(context_ptr);
    if (!jniCtx || !jniCtx->ctx) return -1;

    jniCtx->aborted.store(false);

    jsize n_samples = env->GetArrayLength(pcm_data);
    jfloat * samples = env->GetFloatArrayElements(pcm_data, nullptr);
    if (!samples) return -1;

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = num_threads > 0 ? num_threads : 4;
    params.translate = translate;

    const char * lang_str = language ? env->GetStringUTFChars(language, nullptr) : "auto";
    params.language = lang_str;
    params.detect_language = (language == nullptr || strcmp(lang_str, "auto") == 0);

    CallbackUserData cbData;
    cbData.env = env;
    cbData.listener = listener;
    cbData.jniCtx = jniCtx;

    if (listener) {
        jclass listenerClass = env->GetObjectClass(listener);
        cbData.onProgressMethod = env->GetMethodID(listenerClass, "onProgress", "(I)V");
        cbData.onNewSegmentMethod = env->GetMethodID(listenerClass, "onNewSegment", "(JJLjava/lang/String;)V");
    } else {
        cbData.onProgressMethod = nullptr;
        cbData.onNewSegmentMethod = nullptr;
    }

    params.progress_callback = progress_callback;
    params.progress_callback_user_data = &cbData;

    params.new_segment_callback = new_segment_callback;
    params.new_segment_callback_user_data = &cbData;

    params.abort_callback = abort_callback;
    params.abort_callback_user_data = jniCtx;

    LOGI("Starting whisper transcription with %d samples on %d threads", n_samples, params.n_threads);
    int res = whisper_full(jniCtx->ctx, params, samples, n_samples);

    if (language && lang_str) {
        env->ReleaseStringUTFChars(language, lang_str);
    }
    env->ReleaseFloatArrayElements(pcm_data, samples, JNI_ABORT);

    return res;
}

extern "C" JNIEXPORT void JNICALL
Java_fr_schwanx_vox_core_native_WhisperEngine_abort(
        JNIEnv * env,
        jobject thiz,
        jlong context_ptr) {
    if (context_ptr == 0) return;
    auto * jniCtx = reinterpret_cast<JniWhisperContext *>(context_ptr);
    if (jniCtx) {
        LOGI("Aborting whisper transcription");
        jniCtx->aborted.store(true);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_fr_schwanx_vox_core_native_WhisperEngine_freeContext(
        JNIEnv * env,
        jobject thiz,
        jlong context_ptr) {
    if (context_ptr == 0) return;
    auto * jniCtx = reinterpret_cast<JniWhisperContext *>(context_ptr);
    if (jniCtx) {
        if (jniCtx->ctx) {
            whisper_free(jniCtx->ctx);
        }
        delete jniCtx;
        LOGI("Whisper context freed successfully");
    }
}
