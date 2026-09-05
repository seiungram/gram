cmake_minimum_required(VERSION 3.22)
project(tmessages C CXX ASM)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

add_compile_options(
    -ffunction-sections
    -fdata-sections
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -Wno-error=implicit-function-declaration
)

add_link_options(
    -Wl,--gc-sections
    -Wl,--icf=safe
    -Wl,-Bsymbolic
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

enable_language(ASM)
if(ANDROID_ABI STREQUAL "x86" OR ANDROID_ABI STREQUAL "x86_64")
    enable_language(ASM_NASM)
endif()
set(CMAKE_ASM_FLAGS "${CMAKE_C_FLAGS} -x assembler-with-cpp")

set(FFMPEG_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg/${ANDROID_ABI}")
set(BORINGSSL_DIR "${CMAKE_CURRENT_SOURCE_DIR}/boringssl/lib/${ANDROID_ABI}")
set(TDE2E_DIR "${CMAKE_CURRENT_SOURCE_DIR}/tde2e/${ANDROID_ABI}")
set(PREBUILD_DIR "${CMAKE_CURRENT_SOURCE_DIR}/prebuild/${ANDROID_ABI}")

add_library(avutil STATIC IMPORTED)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/libavutil.a")

add_library(avformat STATIC IMPORTED)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/libavformat.a")

add_library(avcodec STATIC IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/libavcodec.a")

add_library(swresample STATIC IMPORTED)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/libswresample.a")

add_library(swscale STATIC IMPORTED)
set_target_properties(swscale PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/libswscale.a")

add_library(libvpx STATIC IMPORTED)
set_target_properties(libvpx PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/libvpx.a")

add_library(libdav1d STATIC IMPORTED)
set_target_properties(libdav1d PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/libdav1d.a")

add_library(libopus STATIC IMPORTED)
set_target_properties(libopus PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/libopus.a")

# BoringSSL
add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION "${BORINGSSL_DIR}/libcrypto.a")

add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION "${BORINGSSL_DIR}/libssl.a")

# tlottie
add_library(tlottie STATIC IMPORTED)
set_target_properties(tlottie PROPERTIES IMPORTED_LOCATION "${PREBUILD_DIR}/libtlottie.a")

# TDLib E2E
add_library(tde2e STATIC IMPORTED)
set_target_properties(tde2e PROPERTIES IMPORTED_LOCATION "${TDE2E_DIR}/libtde2e.a")

add_library(tdutils STATIC IMPORTED)
set_target_properties(tdutils PROPERTIES IMPORTED_LOCATION "${TDE2E_DIR}/libtdutils.a")

set(CAN_COMPILE_SME FALSE CACHE INTERNAL "Disable libyuv SME" FORCE)
add_subdirectory(third_party/libyuv ${CMAKE_CURRENT_BINARY_DIR}/libyuv EXCLUDE_FROM_ALL)

# SQLite
add_library(sqlite STATIC sqlite/sqlite3.c)
target_compile_options(sqlite PRIVATE -fno-strict-aliasing -Oz)
target_compile_definitions(sqlite PRIVATE
    NULL=0
    SOCKLEN_T=socklen_t
    LOCALE_NOT_USED
    ANDROID_NDK
    HAVE_STRCHRNUL=0
)

# Breakpad (Crash handler)
add_library(breakpad STATIC
    third_party/breakpad/src/client/linux/crash_generation/crash_generation_client.cc
    third_party/breakpad/src/client/linux/handler/exception_handler.cc
    third_party/breakpad/src/client/linux/handler/minidump_descriptor.cc
    third_party/breakpad/src/client/linux/log/log.cc
    third_party/breakpad/src/client/linux/dump_writer_common/thread_info.cc
    third_party/breakpad/src/client/linux/dump_writer_common/seccomp_unwinder.cc
    third_party/breakpad/src/client/linux/dump_writer_common/ucontext_reader.cc
    third_party/breakpad/src/client/linux/microdump_writer/microdump_writer.cc
    third_party/breakpad/src/client/linux/minidump_writer/linux_dumper.cc
    third_party/breakpad/src/client/linux/minidump_writer/linux_ptrace_dumper.cc
    third_party/breakpad/src/client/linux/minidump_writer/minidump_writer.cc
    third_party/breakpad/src/client/minidump_file_writer.cc
    third_party/breakpad/src/common/android/breakpad_getcontext.S
    third_party/breakpad/src/common/convert_UTF.c
    third_party/breakpad/src/common/md5.cc
    third_party/breakpad/src/common/string_conversion.cc
    third_party/breakpad/src/common/linux/elfutils.cc
    third_party/breakpad/src/common/linux/file_id.cc
    third_party/breakpad/src/common/linux/guid_creator.cc
    third_party/breakpad/src/common/linux/linux_libc_support.cc
    third_party/breakpad/src/common/linux/memory_mapped_file.cc
    third_party/breakpad/src/common/linux/safe_readlink.cc
)
set_property(SOURCE third_party/breakpad/src/common/android/breakpad_getcontext.S PROPERTY LANGUAGE ASM)
target_include_directories(breakpad PUBLIC
    third_party/breakpad/src/common/android/include
    third_party/breakpad/src
)

# tgnet (MTProto layer)
add_library(tgnet STATIC
    tgnet/ApiScheme.cpp
    tgnet/BuffersStorage.cpp
    tgnet/ByteArray.cpp
    tgnet/ByteStream.cpp
    tgnet/Connection.cpp
    tgnet/ConnectionSession.cpp
    tgnet/ConnectionsManager.cpp
    tgnet/ConnectionSocket.cpp
    tgnet/Datacenter.cpp
    tgnet/EventObject.cpp
    tgnet/FileLog.cpp
    tgnet/MTProtoScheme.cpp
    tgnet/NativeByteBuffer.cpp
    tgnet/Request.cpp
    tgnet/Timer.cpp
    tgnet/TLObject.cpp
    tgnet/ProxyCheckInfo.cpp
    tgnet/Handshake.cpp
    tgnet/Config.cpp
)
target_compile_options(tgnet PRIVATE -Wall -frtti -finline-functions -Oz)
target_compile_definitions(tgnet PRIVATE HAVE_PTHREAD)
target_include_directories(tgnet PUBLIC boringssl/include)
target_link_libraries(tgnet PRIVATE crypto)

# VoIP Core
include(${CMAKE_CURRENT_SOURCE_DIR}/voip/CMakeLists.txt)

set(NATIVE_LIB "tmessages.49")

add_library(${NATIVE_LIB} SHARED
    jni.c
    audio.c
    webm_encoder.c
    image.cpp
    intro/IntroRenderer.c
    SqliteWrapper.cpp
    utilities.cpp
    gifvideo.cpp
    lottie.cpp
    TgNetWrapper.cpp
    exoplayer/ffmpeg_jni.cc
    fast-edge.cpp
    genann.c
    secureid_ocr.cpp
    tde2e/bridge.cpp

    # Opus & Ogg sources
    third_party/xiph/opusfile/src/info.c
    third_party/xiph/opusfile/src/internal.c
    third_party/xiph/opusfile/src/opusfile.c
    third_party/xiph/opusfile/src/stream.c
    third_party/xiph/ogg/src/bitwise.c
    third_party/xiph/ogg/src/framing.c
)

target_compile_options(${NATIVE_LIB} PRIVATE
    -Oz
    -funroll-loops
    -fno-strict-aliasing
    -fno-math-errno
)

target_compile_definitions(${NATIVE_LIB} PUBLIC
    BSD=1
    NULL=0
    SOCKLEN_T=socklen_t
    ANDROID_NDK
    __STDC_CONSTANT_MACROS
    OPUS_BUILD
    FIXED_POINT
    USE_ALLOCA
    restrict=
    LOCALE_NOT_USED
    HAVE_LRINT
    HAVE_LRINTF
)

target_include_directories(${NATIVE_LIB} PUBLIC
    third_party/xiph/opus/include
    third_party/xiph/opus/silk
    third_party/xiph/opus/silk/fixed
    third_party/xiph/opus/celt
    third_party/xiph/opus
    third_party/xiph/opusfile/include
    third_party/xiph/ogg/include
    third_party/libyuv/include
    boringssl/include
    ffmpeg/include
    emoji
    exoplayer/include
    intro
    tlottie/include
    tgcalls/
    webrtc/
    tde2e/include
)

target_link_options(${NATIVE_LIB} PRIVATE
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map"
)

target_link_libraries(${NATIVE_LIB} PRIVATE
    -Wl,--whole-archive rnnoise openh264 voipandroid -Wl,--no-whole-archive
    tgvoip
    tgcalls
    tgcalls_tp
    tgnet
    tlottie
    sqlite
    swscale
    avformat
    avcodec
    swresample
    libvpx
    libdav1d
    libopus
    tde2e
    tdutils
    yuv
    avutil
    ssl
    crypto
    jnigraphics
    log
    z
    GLESv2
    EGL
    android
    OpenSLES
    breakpad
)
