#include "ggml-alloc.h"
#include <stdlib.h>

struct ggml_allocr {
    void * data;
    size_t size;
    size_t offset;
    size_t alignment;
};

ggml_allocr_t ggml_allocr_new(void * data, size_t size, size_t alignment) {
    struct ggml_allocr * alloc = (struct ggml_allocr *)malloc(sizeof(struct ggml_allocr));
    if (!alloc) return NULL;
    alloc->data = data;
    alloc->size = size;
    alloc->offset = 0;
    alloc->alignment = alignment > 0 ? alignment : 16;
    return alloc;
}

void ggml_allocr_free(ggml_allocr_t alloc) {
    if (alloc) free(alloc);
}

void ggml_allocr_alloc(ggml_allocr_t alloc, struct ggml_tensor * tensor) {
    if (!alloc || !tensor) return;
    size_t sz = tensor->nb[3] * tensor->ne[3];
    size_t align = alloc->alignment;
    size_t off = (alloc->offset + align - 1) & ~(align - 1);
    if (off + sz <= alloc->size && alloc->data) {
        tensor->data = (char*)alloc->data + off;
        alloc->offset = off + sz;
    }
}
