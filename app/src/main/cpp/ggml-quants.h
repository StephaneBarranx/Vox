#ifndef GGML_QUANTS_H
#define GGML_QUANTS_H

#include "ggml.h"

#ifdef __cplusplus
extern "C" {
#endif

// Quantization functions declarations
void quantize_row_q4_0(const float * x, void * y, int k);
void quantize_row_q4_1(const float * x, void * y, int k);
void quantize_row_q5_0(const float * x, void * y, int k);
void quantize_row_q5_1(const float * x, void * y, int k);

void dequantize_row_q4_0(const void * x, float * y, int k);
void dequantize_row_q4_1(const void * x, float * y, int k);
void dequantize_row_q5_0(const void * x, float * y, int k);
void dequantize_row_q5_1(const void * x, float * y, int k);

#ifdef __cplusplus
}
#endif

#endif // GGML_QUANTS_H
