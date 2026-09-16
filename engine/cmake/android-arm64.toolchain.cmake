# Android target shared by the OrcaSlicer engine and every dependency sub-build.
# ExternalProject forwards only CMAKE_TOOLCHAIN_FILE to dependencies, so the ABI,
# API level, C++ runtime, and NDK location are all resolved here.

set(ORCINUS_NDK_VERSION 28.2.13676358)

if(DEFINED ENV{ANDROID_NDK_ROOT})
    file(TO_CMAKE_PATH "$ENV{ANDROID_NDK_ROOT}" _orcinus_ndk)
else()
    get_filename_component(_orcinus_repo "${CMAKE_CURRENT_LIST_DIR}/../.." ABSOLUTE)
    file(STRINGS "${_orcinus_repo}/local.properties" _orcinus_sdk REGEX "^sdk\\.dir=")
    if(NOT _orcinus_sdk)
        message(FATAL_ERROR "Set ANDROID_NDK_ROOT or sdk.dir in local.properties")
    endif()
    string(REGEX REPLACE "^sdk\\.dir=" "" _orcinus_sdk "${_orcinus_sdk}")
    string(REPLACE "\\:" ":" _orcinus_sdk "${_orcinus_sdk}")
    string(REPLACE "\\\\" "/" _orcinus_sdk "${_orcinus_sdk}")
    set(_orcinus_ndk "${_orcinus_sdk}/ndk/${ORCINUS_NDK_VERSION}")
endif()

set(ANDROID_ABI arm64-v8a)
set(ANDROID_PLATFORM android-29)
set(ANDROID_STL c++_static)

# AArch64 Clang fuses a*b+c into FMA instructions by default, which changes the
# last bits of Orca's geometry compared with desktop x86-64 builds.
set(CMAKE_C_FLAGS "-ffp-contract=off" CACHE STRING "")
set(CMAKE_CXX_FLAGS "-ffp-contract=off" CACHE STRING "")

include("${_orcinus_ndk}/build/cmake/android.toolchain.cmake")

# Orca's autotools recipes derive their --host triple from this variable.
set(TOOLCHAIN_PREFIX aarch64-linux-android)
