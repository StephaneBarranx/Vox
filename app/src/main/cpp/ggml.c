#include "ggml.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <math.h>

struct ggml_context {
    size_t mem_size;
    void * mem_buffer;
    bool   mem_owner;
    size_t objects_offset;
};

struct ggml_context * ggml_init(struct ggml_init_params params) {
    struct ggml_context * ctx = (struct ggml_context *)malloc(sizeof(struct ggml_context));
    if (!ctx) return NULL;

    ctx->mem_size = params.mem_size;
    if (params.mem_buffer) {
        ctx->mem_buffer = params.mem_buffer;
        ctx->mem_owner = false;
    } else {
        ctx->mem_buffer = malloc(params.mem_size > 0 ? params.mem_size : 1024);
        ctx->mem_owner = true;
    }
    ctx->objects_offset = 0;
    return ctx;
}

void ggml_free(struct ggml_context * ctx) {
    if (!ctx) return;
    if (ctx->mem_owner && ctx->mem_buffer) {
        free(ctx->mem_buffer);
    }
    free(ctx);
}

size_t ggml_used_mem(const struct ggml_context * ctx) {
    return ctx ? ctx->objects_offset : 0;
}

size_t ggml_type_size(enum ggml_type type) {
    switch (type) {
        case GGML_TYPE_F32:  return sizeof(float);
        case GGML_TYPE_F16:  return sizeof(uint16_t);
        case GGML_TYPE_Q4_0: return 18; // 32 blocks
        case GGML_TYPE_Q4_1: return 20;
        case GGML_TYPE_Q5_0: return 22;
        case GGML_TYPE_Q5_1: return 24;
        case GGML_TYPE_I8:   return sizeof(int8_t);
        case GGML_TYPE_I16:  return sizeof(int16_t);
        case GGML_TYPE_I32:  return sizeof(int32_t);
        default: return sizeof(float);
    }
}

size_t ggml_blck_size(enum ggml_type type) {
    switch (type) {
        case GGML_TYPE_Q4_0:
        case GGML_TYPE_Q4_1:
        case GGML_TYPE_Q5_0:
        case GGML_TYPE_Q5_1: return 32;
        default: return 1;
    }
}

size_t ggml_row_size(enum ggml_type type, int64_t ne) {
    size_t bs = ggml_blck_size(type);
    size_t ts = ggml_type_size(type);
    return (ne / bs) * ts;
}

struct ggml_tensor * ggml_new_tensor(
        struct ggml_context * ctx,
        enum   ggml_type type,
        int    n_dims,
        const int64_t * ne) {
    struct ggml_tensor * t = (struct ggml_tensor *)calloc(1, sizeof(struct ggml_tensor));
    if (!t) return NULL;

    t->type = type;
    t->n_dims = n_dims;
    for (int i = 0; i < n_dims; ++i) {
        t->ne[i] = ne[i];
    }
    for (int i = n_dims; i < GGML_MAX_DIMS; ++i) {
        t->ne[i] = 1;
    }

    t->nb[0] = ggml_type_size(type);
    t->nb[1] = t->nb[0] * (t->ne[0] / ggml_blck_size(type));
    for (int i = 2; i < GGML_MAX_DIMS; ++i) {
        t->nb[i] = t->nb[i-1] * t->ne[i-1];
    }

    return t;
}

struct ggml_tensor * ggml_new_tensor_1d(
        struct ggml_context * ctx,
        enum   ggml_type type,
        int64_t ne0) {
    int64_t ne[1] = { ne0 };
    return ggml_new_tensor(ctx, type, 1, ne);
}

struct ggml_tensor * ggml_new_tensor_2d(
        struct ggml_context * ctx,
        enum   ggml_type type,
        int64_t ne0,
        int64_t ne1) {
    int64_t ne[2] = { ne0, ne1 };
    return ggml_new_tensor(ctx, type, 2, ne);
}

struct ggml_tensor * ggml_new_tensor_3d(
        struct ggml_context * ctx,
        enum   ggml_type type,
        int64_t ne0,
        int64_t ne1,
        int64_t ne2) {
    int64_t ne[3] = { ne0, ne1, ne2 };
    return ggml_new_tensor(ctx, type, 3, ne);
}
