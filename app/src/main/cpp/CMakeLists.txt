cmake_minimum_required(VERSION 3.22.1)
project("vox_whisper")

# Whisper is unusably slow without optimizations, so always build native code
# as Release, even for the debug variant of the app.
set(CMAKE_BUILD_TYPE Release)

# whisper.cpp (vendored in ./whisper, upstream v1.9.4)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

# The CPU backend is built as loadable modules so ggml can pick, at runtime,
# the best variant the phone's CPU supports (dotprod, fp16, i8mm, SVE...).
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
endif()

# Backend modules must land next to the other .so files of the APK.
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY})

add_subdirectory(whisper)

add_library(vox_whisper SHARED whisper-jni.cpp)

find_library(log-lib log)

target_link_libraries(vox_whisper whisper ggml ${log-lib})
