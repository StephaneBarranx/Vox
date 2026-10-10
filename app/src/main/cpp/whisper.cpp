#include "whisper.h"
#include "ggml.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <string>
#include <algorithm>
#include <cmath>

struct whisper_segment {
    int64_t t0;
    int64_t t1;
    std::string text;
};

struct whisper_context {
    std::string path_model;
    whisper_context_params params;
    std::vector<whisper_segment> result_segments;
    bool is_loaded;
};

struct whisper_context_params whisper_context_default_params(void) {
    struct whisper_context_params params;
    params.use_gpu = false;
    params.flash_attn = false;
    params.gpu_device = 0;
    return params;
}

struct whisper_context * whisper_init_from_file_with_params(const char * path_model, struct whisper_context_params params) {
    if (!path_model) return nullptr;

    FILE * f = fopen(path_model, "rb");
    if (!f) return nullptr;

    // Check GGML magic header
    uint32_t magic = 0;
    if (fread(&magic, sizeof(magic), 1, f) != 1) {
        fclose(f);
        return nullptr;
    }
    fclose(f);

    whisper_context * ctx = new whisper_context();
    ctx->path_model = path_model;
    ctx->params = params;
    ctx->is_loaded = true;

    return ctx;
}

struct whisper_context * whisper_init_from_file(const char * path_model) {
    return whisper_init_from_file_with_params(path_model, whisper_context_default_params());
}

void whisper_free(struct whisper_context * ctx) {
    if (ctx) {
        delete ctx;
    }
}

struct whisper_full_params whisper_full_default_params(enum whisper_sampling_strategy strategy) {
    struct whisper_full_params params;
    memset(&params, 0, sizeof(params));

    params.strategy = strategy;
    params.n_threads = 4;
    params.n_max_text_ctx = 16384;
    params.offset_ms = 0;
    params.duration_ms = 0;

    params.translate = false;
    params.no_context = true;
    params.no_timestamps = false;
    params.single_segment = false;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = true;

    params.language = "auto";
    params.detect_language = true;

    params.suppress_blank = true;
    params.suppress_non_speech_tokens = false;

    params.progress_callback = nullptr;
    params.progress_callback_user_data = nullptr;

    params.new_segment_callback = nullptr;
    params.new_segment_callback_user_data = nullptr;

    params.abort_callback = nullptr;
    params.abort_callback_user_data = nullptr;

    return params;
}

int whisper_full(
        struct whisper_context * ctx,
        struct whisper_full_params params,
        const float * samples,
        int n_samples) {
    if (!ctx || !ctx->is_loaded || !samples || n_samples <= 0) {
        return -1;
    }

    ctx->result_segments.clear();

    // Calculate duration in seconds
    double total_sec = (double)n_samples / WHISPER_SAMPLE_RATE;
    int chunk_samples = WHISPER_SAMPLE_RATE * 10; // 10 second chunks

    int n_chunks = (n_samples + chunk_samples - 1) / chunk_samples;

    for (int chunk = 0; chunk < n_chunks; ++chunk) {
        if (params.abort_callback && params.abort_callback(params.abort_callback_user_data)) {
            return -2; // Aborted
        }

        int progress_pct = (int)(((double)(chunk + 1) / n_chunks) * 100.0);
        if (params.progress_callback) {
            params.progress_callback(ctx, nullptr, progress_pct, params.progress_callback_user_data);
        }

        int start_sample = chunk * chunk_samples;
        int end_sample = std::min(start_sample + chunk_samples, n_samples);

        int64_t t0 = (int64_t)((double)start_sample / WHISPER_SAMPLE_RATE * 1000.0);
        int64_t t1 = (int64_t)((double)end_sample / WHISPER_SAMPLE_RATE * 1000.0);

        // Dummy text segment generator placeholder for JNI testing
        whisper_segment seg;
        seg.t0 = t0;
        seg.t1 = t1;
        char buf[256];
        snprintf(buf, sizeof(buf), "[Transcription segment %d: %dms - %dms]", chunk + 1, (int)t0, (int)t1);
        seg.text = buf;

        ctx->result_segments.push_back(seg);

        if (params.new_segment_callback) {
            params.new_segment_callback(ctx, nullptr, 1, params.new_segment_callback_user_data);
        }
    }

    return 0;
}

int whisper_full_n_segments(struct whisper_context * ctx) {
    if (!ctx) return 0;
    return (int)ctx->result_segments.size();
}

int64_t whisper_full_get_segment_t0(struct whisper_context * ctx, int i_segment) {
    if (!ctx || i_segment < 0 || i_segment >= (int)ctx->result_segments.size()) return 0;
    return ctx->result_segments[i_segment].t0;
}

int64_t whisper_full_get_segment_t1(struct whisper_context * ctx, int i_segment) {
    if (!ctx || i_segment < 0 || i_segment >= (int)ctx->result_segments.size()) return 0;
    return ctx->result_segments[i_segment].t1;
}

const char * whisper_full_get_segment_text(struct whisper_context * ctx, int i_segment) {
    if (!ctx || i_segment < 0 || i_segment >= (int)ctx->result_segments.size()) return "";
    return ctx->result_segments[i_segment].text.c_str();
}

int whisper_lang_id(const char * lang) {
    if (!lang) return 0;
    if (strcmp(lang, "fr") == 0) return 1;
    if (strcmp(lang, "en") == 0) return 2;
    if (strcmp(lang, "es") == 0) return 3;
    return 0;
}
