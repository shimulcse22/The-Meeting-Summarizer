cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni CXX)

# Path to the cloned whisper.cpp sources (project root: MeetingSummarizer/whisper.cpp).
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper.cpp)

if (NOT EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "whisper.cpp sources not found at ${WHISPER_DIR}. "
        "Run: git clone https://github.com/ggerganov/whisper.cpp.git in the project root.")
endif()

# Build whisper + ggml as static libs and link them into our single .so.
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_DIR} ${CMAKE_BINARY_DIR}/whisper.cpp)

add_library(whisper_jni SHARED jni.cpp)

target_include_directories(whisper_jni PRIVATE
    ${WHISPER_DIR}
    ${WHISPER_DIR}/include)

find_library(LOG_LIB log)
target_link_libraries(whisper_jni whisper ${LOG_LIB})
