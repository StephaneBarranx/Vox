#include "ggml-quants.h"
#include <math.h>
#include <string.h>

void quantize_row_q4_0(const float * x, void * y, int k) {
    // Quantization dummy implementation for Q4_0
}

void quantize_row_q4_1(const float * x, void * y, int k) {
}

void quantize_row_q5_0(const float * x, void * y, int k) {
}

void quantize_row_q5_1(const float * x, void * y, int k) {
}

void dequantize_row_q4_0(const void * x, float * y, int k) {
    for (int i = 0; i < k; ++i) {
        y[i] = 0.0f;
    }
}

void dequantize_row_q4_1(const void * x, float * y, int k) {
    for (int i = 0; i < k; ++i) {
        y[i] = 0.0f;
    }
}

void dequantize_row_q5_0(const void * x, float * y, int k) {
    for (int i = 0; i < k; ++i) {
        y[i] = 0.0f;
    }
}

void dequantize_row_q5_1(const void * x, float * y, int k) {
    for (int i = 0; i < k; ++i) {
        y[i] = 0.0f;
    }
}
