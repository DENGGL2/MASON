cmake_minimum_required(VERSION 3.22.1)

project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

if(DEFINED ANDROID_ABI)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI ON)
        set(GGML_OPENMP ON)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
set(LLAMA_ANDROID_WRAPPER ${LLAMA_SRC}/examples/llama.android/lib/src/main/cpp)
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS ${LLAMA_ANDROID_WRAPPER}/ai_chat.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

file(READ ${LLAMA_ANDROID_WRAPPER}/ai_chat.cpp AI_CHAT_SOURCE)
string(REPLACE
        "/* use_jinja */ false"
        "/* use_jinja */ true"
        AI_CHAT_SOURCE
        "${AI_CHAT_SOURCE}")
string(REPLACE
        "common_chat_templates_was_explicit(g_chat_templates.get())"
        "g_chat_templates != nullptr"
        AI_CHAT_SOURCE
        "${AI_CHAT_SOURCE}")
set(PATCHED_AI_CHAT ${CMAKE_CURRENT_BINARY_DIR}/ai_chat_mason.cpp)
file(WRITE ${PATCHED_AI_CHAT} "${AI_CHAT_SOURCE}")

add_library(${CMAKE_PROJECT_NAME} SHARED
        ${PATCHED_AI_CHAT}
        android_log_compat.cpp)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        -include ${CMAKE_CURRENT_LIST_DIR}/android_log_compat.h)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${LLAMA_ANDROID_WRAPPER}
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        llama-common
        android
        log)
