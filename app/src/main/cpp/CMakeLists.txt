cmake_minimum_required(VERSION 3.22.1)
project("vox_whisper")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -funroll-loops")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -funroll-loops -std=c++17")

if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_definitions(-D__ARM_NEON)
endif()

add_library(
    vox_whisper
    SHARED
    ggml.c
    ggml-alloc.c
    ggml-backend.c
    ggml-quants.c
    whisper.cpp
    whisper-jni.cpp
)

find_library(log-lib log)

target_link_libraries(
    vox_whisper
    ${log-lib}
    m
)
