#ifndef WHISPER_H
#define WHISPER_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define WHISPER_SAMPLE_RATE 16000
#define WHISPER_N_FFT       400
#define WHISPER_HOP_LENGTH  160
#define WHISPER_CHUNK_SIZE  30

struct whisper_context;
struct whisper_state;

enum whisper_sampling_strategy {
    WHISPER_SAMPLING_GREEDY,
    WHISPER_SAMPLING_BEAM_SEARCH,
};

typedef bool (*whisper_progress_callback)(struct whisper_context * ctx, struct whisper_state * state, int progress, void * user_data);
typedef void (*whisper_new_segment_callback)(struct whisper_context * ctx, struct whisper_state * state, int n_new, void * user_data);
typedef bool (*whisper_abort_callback)(void * user_data);

struct whisper_context_params {
    bool use_gpu;
    bool flash_attn;
    int  gpu_device;
};

struct whisper_full_params {
    enum whisper_sampling_strategy strategy;

    int n_threads;
    int n_max_text_ctx;
    int offset_ms;
    int duration_ms;

    bool translate;
    bool no_context;
    bool no_timestamps;
    bool single_segment;
    bool print_special;
    bool print_progress;
    bool print_realtime;
    bool print_timestamps;

    const char * language;
    bool detect_language;

    bool suppress_blank;
    bool suppress_non_speech_tokens;

    whisper_progress_callback    progress_callback;
    void *                       progress_callback_user_data;

    whisper_new_segment_callback new_segment_callback;
    void *                       new_segment_callback_user_data;

    whisper_abort_callback       abort_callback;
    void *                       abort_callback_user_data;
};

struct whisper_context_params whisper_context_default_params(void);
struct whisper_context * whisper_init_from_file_with_params(const char * path_model, struct whisper_context_params params);
struct whisper_context * whisper_init_from_file(const char * path_model);

void whisper_free(struct whisper_context * ctx);

struct whisper_full_params whisper_full_default_params(enum whisper_sampling_strategy strategy);

int whisper_full(
        struct whisper_context * ctx,
        struct whisper_full_params   params,
        const float * samples,
        int   n_samples);

int whisper_full_n_segments(struct whisper_context * ctx);
int64_t whisper_full_get_segment_t0(struct whisper_context * ctx, int i_segment);
int64_t whisper_full_get_segment_t1(struct whisper_context * ctx, int i_segment);
const char * whisper_full_get_segment_text(struct whisper_context * ctx, int i_segment);

int whisper_lang_id(const char * lang);

#ifdef __cplusplus
}
#endif

#endif // WHISPER_H
