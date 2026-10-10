#ifndef GGML_H
#define GGML_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define GGML_MAX_DIMS 4
#define GGML_MAX_NODES 4096
#define GGML_MAX_PARAMS 256
#define GGML_MAX_CONTEXTS 64
#define GGML_MAX_OPT 4

enum ggml_type {
    GGML_TYPE_F32  = 0,
    GGML_TYPE_F16  = 1,
    GGML_TYPE_Q4_0 = 2,
    GGML_TYPE_Q4_1 = 3,
    GGML_TYPE_Q5_0 = 6,
    GGML_TYPE_Q5_1 = 7,
    GGML_TYPE_Q8_0 = 8,
    GGML_TYPE_I8   = 9,
    GGML_TYPE_I16  = 10,
    GGML_TYPE_I32  = 11,
    GGML_TYPE_COUNT,
};

enum ggml_op {
    GGML_OP_NONE = 0,
    GGML_OP_DUP,
    GGML_OP_ADD,
    GGML_OP_SUB,
    GGML_OP_MUL,
    GGML_OP_DIV,
    GGML_OP_SQR,
    GGML_OP_SQRT,
    GGML_OP_LOG,
    GGML_OP_SUM,
    GGML_OP_MEAN,
    GGML_OP_ARGMAX,
    GGML_OP_REPEAT,
    GGML_OP_ABS,
    GGML_OP_SIGN,
    GGML_OP_NEG,
    GGML_OP_STEP,
    GGML_OP_RELU,
    GGML_OP_GELU,
    GGML_OP_GELU_QUICK,
    GGML_OP_SILU,
    GGML_OP_NORM,
    GGML_OP_RMS_NORM,
    GGML_OP_MUL_MAT,
    GGML_OP_SCALE,
    GGML_OP_CPY,
    GGML_OP_CONT,
    GGML_OP_RESHAPE,
    GGML_OP_VIEW,
    GGML_OP_PERMUTE,
    GGML_OP_TRANSPOSE,
    GGML_OP_GET_ROWS,
    GGML_OP_DIAG_MASK_INF,
    GGML_OP_SOFT_MAX,
    GGML_OP_ROPE,
    GGML_OP_ALIBI,
    GGML_OP_CLAMP,
    GGML_OP_CONV_1D,
    GGML_OP_CONV_2D,
    GGML_OP_FLASH_ATTN,
    GGML_OP_FLASH_FFN,
    GGML_OP_MAP_UNARY,
    GGML_OP_MAP_BINARY,
    GGML_OP_COUNT,
};

struct ggml_object {
    size_t offset;
    size_t size;
    struct ggml_object * next;
    char padding[8];
};

struct ggml_tensor {
    enum ggml_type type;
    int n_dims;
    int64_t ne[GGML_MAX_DIMS]; // number of elements
    size_t  nb[GGML_MAX_DIMS]; // stride in bytes
    enum ggml_op op;
    int32_t op_params[GGML_MAX_PARAMS / sizeof(int32_t)];
    bool flags;
    struct ggml_tensor * src[GGML_MAX_PARAMS];
    struct ggml_tensor * view_src;
    size_t view_offs;
    void * data;
    char name[64];
    char padding[16];
};

struct ggml_cgraph {
    int n_nodes;
    int n_leafs;
    struct ggml_tensor * nodes[GGML_MAX_NODES];
    struct ggml_tensor * leafs[GGML_MAX_NODES];
};

struct ggml_init_params {
    size_t mem_size;
    void * mem_buffer;
    bool   no_alloc;
};

struct ggml_context;

struct ggml_context * ggml_init(struct ggml_init_params params);
void ggml_free(struct ggml_context * ctx);

size_t ggml_used_mem(const struct ggml_context * ctx);

struct ggml_tensor * ggml_new_tensor(
        struct ggml_context * ctx,
        enum   ggml_type type,
        int    n_dims,
        const int64_t * ne);

struct ggml_tensor * ggml_new_tensor_1d(
        struct ggml_context * ctx,
        enum   ggml_type type,
        int64_t ne0);

struct ggml_tensor * ggml_new_tensor_2d(
        struct ggml_context * ctx,
        enum   ggml_type type,
        int64_t ne0,
        int64_t ne1);

struct ggml_tensor * ggml_new_tensor_3d(
        struct ggml_context * ctx,
        enum   ggml_type type,
        int64_t ne0,
        int64_t ne1,
        int64_t ne2);

size_t ggml_type_size(enum ggml_type type);
size_t ggml_blck_size(enum ggml_type type);
size_t ggml_row_size(enum ggml_type type, int64_t ne);

#ifdef __cplusplus
}
#endif

#endif // GGML_H
