cmake_minimum_required(VERSION 3.24)

# OrcaSlicer's libslic3r engine and its upstream Catch2 test suites for Android.
#
# This file is the Android counterpart of upstream's top-level CMakeLists.txt:
# global compile settings and package discovery are mirrored from it, while the
# libslic3r source list is read from upstream at configure time. Dependencies
# come from the prefix produced by engine/deps.

project(OrcinusEngine LANGUAGES C CXX)

if(NOT ANDROID)
    message(FATAL_ERROR "Configure with engine/cmake/android-arm64.toolchain.cmake")
endif()

get_filename_component(ORCA_UPSTREAM_DIR "${CMAKE_CURRENT_LIST_DIR}/../upstream/OrcaSlicer" ABSOLUTE)
set(LIBDIR "${ORCA_UPSTREAM_DIR}/src")
set(LIBDIR_BIN "${CMAKE_CURRENT_BINARY_DIR}/src")

option(ENGINE_BUILD_TESTS "Build OrcaSlicer's libslic3r and fff_print test suites" ON)
set(ENGINE_DEVICE_TEST_DIR "/data/local/tmp/orca-engine-tests" CACHE STRING "Device directory that receives the test executables and data")

list(APPEND CMAKE_MODULE_PATH "${ORCA_UPSTREAM_DIR}/cmake/modules" "${CMAKE_CURRENT_LIST_DIR}/cmake")
include("${ORCA_UPSTREAM_DIR}/version.inc")
include(OrcaSourceLists)

# ---------------------------------------------------------------------------
# Compile settings mirrored from upstream CMakeLists.txt (GCC/Clang branches).
# ---------------------------------------------------------------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(SLIC3R_STATIC ON)
set(IS_CROSS_COMPILE TRUE)

add_compile_definitions("BBL_RELEASE_TO_PUBLIC=$<CONFIG:Release>")
add_compile_options(-fsigned-char)
add_compile_definitions("$<IF:$<CONFIG:Debug>,DEBUG,NDEBUG>")
string(APPEND CMAKE_CXX_FLAGS " -Wall -Wno-reorder")
add_compile_options(
    -Werror=return-type
    -Wno-unused-function
    -Wno-unused-variable
    -Wno-unused-but-set-variable
    -Wno-unused-label
    -Wno-unused-local-typedefs
    -Wno-sign-compare
    -Wno-misleading-indentation
    -Wno-switch
    -Wno-ignored-attributes
    -Wno-deprecated-declarations
    -Wno-error=enum-constexpr-conversion
)
include_directories(SYSTEM "${LIBDIR}")
add_definitions(-DwxUSE_UNICODE -D_UNICODE -DUNICODE -DWXINTL_NO_GETTEXT_MACRO -DwxNO_UNSAFE_WXSTRING_CONV)

# Upstream reads the short hash from git; the pinned commit is recorded in the lock file.
file(READ "${CMAKE_CURRENT_LIST_DIR}/../upstream/orca.lock.json" orca_lock)
string(JSON orca_commit GET "${orca_lock}" commit)
string(SUBSTRING "${orca_commit}" 0 7 GIT_COMMIT_HASH)
add_definitions("-DGIT_COMMIT_HASH=\"${GIT_COMMIT_HASH}\"")

function(slic3r_remap_configs targets from_Cfg to_Cfg)
    # Only needed by multi-configuration MSVC builds upstream.
endfunction()

# ---------------------------------------------------------------------------
# Packages, as found by upstream CMakeLists.txt.
# ---------------------------------------------------------------------------
if(POLICY CMP0167)
    cmake_policy(SET CMP0167 NEW)
endif()
set(Boost_USE_STATIC_LIBS ON)
find_package(Boost 1.83.0 REQUIRED COMPONENTS system filesystem thread log log_setup locale regex chrono atomic date_time iostreams program_options nowide)
find_package(Eigen3 5.0.1 REQUIRED)

add_library(boost_libs INTERFACE)
add_library(boost_headeronly INTERFACE)
target_include_directories(boost_headeronly SYSTEM INTERFACE ${Boost_INCLUDE_DIRS})
target_link_libraries(boost_libs INTERFACE boost_headeronly ${Boost_LIBRARIES})

set(TBB_STATIC 1)
find_package(TBB REQUIRED)
find_package(OpenSSL REQUIRED)
find_package(Freetype REQUIRED)
find_package(ZLIB REQUIRED)
find_package(EXPAT REQUIRED)
find_package(PNG REQUIRED)
find_package(cereal REQUIRED)
if(NOT TARGET cereal::cereal)
    set_target_properties(cereal PROPERTIES IMPORTED_GLOBAL TRUE)
    add_library(cereal::cereal ALIAS cereal)
else()
    set_target_properties(cereal::cereal PROPERTIES IMPORTED_GLOBAL TRUE)
endif()
find_package(NLopt 1.4 REQUIRED)
find_package(libnoise REQUIRED)

set(CGAL_DO_NOT_WARN_ABOUT_CMAKE_BUILD_TYPE ON CACHE BOOL "" FORCE)
find_package(CGAL REQUIRED)
find_package(JPEG REQUIRED)
# Upstream's lowercase request resolves draco's own config on case-sensitive hosts.
find_package(draco REQUIRED CONFIG)
find_package(OpenCASCADE REQUIRED)

# Upstream's own bundled libraries. Only targets that libslic3r and the tests
# link are built; GUI-only entries such as imgui and hidapi stay unused.
add_subdirectory("${ORCA_UPSTREAM_DIR}/deps_src" "${CMAKE_CURRENT_BINARY_DIR}/deps_src" EXCLUDE_FROM_ALL)

# ---------------------------------------------------------------------------
# libslic3r: upstream source list minus explicit Android exclusions.
# ---------------------------------------------------------------------------
set(LIBSLIC3R_DIR "${LIBDIR}/libslic3r")
set(LIBSLIC3R_CMAKE "${LIBSLIC3R_DIR}/CMakeLists.txt")

orca_read_cmake_list("${LIBSLIC3R_CMAKE}" "set\\(lisbslic3r_sources" libslic3r_sources)
orca_exclude_sources(
    libslic3r_sources
    # OpenCV colour quantisation used only by the desktop OBJ colour dialog.
    ObjColorUtils.cpp
    ObjColorUtils.hpp
    # SLA hollowing requires OpenVDB; SLA printing is outside the Android scope.
    SLA/Hollowing.cpp
)
list(TRANSFORM libslic3r_sources PREPEND "${LIBSLIC3R_DIR}/")

orca_read_cmake_list("${LIBSLIC3R_CMAKE}" "add_library\\(libslic3r_cgal STATIC" libslic3r_cgal_sources)
list(TRANSFORM libslic3r_cgal_sources PREPEND "${LIBSLIC3R_DIR}/")

orca_read_cmake_list("${LIBSLIC3R_CMAKE}" "set\\(OCCT_LIBS" OCCT_LIBS)

if(NOT DEFINED ORCA_CHECK_GCODE_PLACEHOLDERS)
    set(ORCA_CHECK_GCODE_PLACEHOLDERS "0")
endif()
set(LIBSLIC3R_GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/libslic3r")
configure_file("${LIBSLIC3R_DIR}/libslic3r_version.h.in" "${LIBSLIC3R_GENERATED_DIR}/libslic3r_version.h" @ONLY)

add_library(libslic3r STATIC ${libslic3r_sources} "${LIBSLIC3R_GENERATED_DIR}/libslic3r_version.h")

add_library(libslic3r_cgal STATIC ${libslic3r_cgal_sources})
target_include_directories(libslic3r_cgal PRIVATE "${LIBSLIC3R_GENERATED_DIR}")
target_link_libraries(libslic3r_cgal PRIVATE CGAL::CGAL admesh libigl mcut boost_libs)

target_compile_definitions(libslic3r PUBLIC -DUSE_TBB -DTBB_USE_CAPTURED_EXCEPTION=0)
target_include_directories(libslic3r PRIVATE "${LIBSLIC3R_DIR}" PUBLIC "${LIBSLIC3R_GENERATED_DIR}")
target_include_directories(libslic3r SYSTEM PUBLIC ${EXPAT_INCLUDE_DIRS} ${OpenCASCADE_INCLUDE_DIR})

# Same link interface as upstream, except for desktop-only libraries: OpenCV
# (excluded above) and fontconfig (Android fonts are located by OpenCASCADE).
target_link_libraries(
    libslic3r
    PUBLIC
        Eigen3::Eigen
        admesh
        libigl
        libnest2d
        miniz
    PRIVATE
        ${CMAKE_DL_LIBS}
        ${EXPAT_LIBRARIES}
        ${OCCT_LIBS}
        boost_libs
        cereal::cereal
        clipper
        Clipper2
        draco::draco
        glu-libtess
        JPEG::JPEG
        libslic3r_cgal
        mcut
        noise::noise
        PNG::PNG
        qhull
        qoi
        semver
        TBB::tbb
        TBB::tbbmalloc
        ZLIB::ZLIB
        OpenSSL::Crypto
        ${FREETYPE_LIBRARIES}
)

# Upstream force-includes pchheader.hpp into every libslic3r translation unit.
target_precompile_headers(libslic3r PRIVATE "${LIBSLIC3R_DIR}/pchheader.hpp")

# ---------------------------------------------------------------------------
# JNI library of the Android app (:slicing:native).
# ---------------------------------------------------------------------------
add_subdirectory("${CMAKE_CURRENT_LIST_DIR}/../slicing/native/src/main/cpp" "${CMAKE_CURRENT_BINARY_DIR}/jni")

# ---------------------------------------------------------------------------
# Upstream Catch2 suites, run on the device through scripts/engine-test.ps1.
# ---------------------------------------------------------------------------
if(ENGINE_BUILD_TESTS)
    set(ORCA_TESTS_DIR "${ORCA_UPSTREAM_DIR}/tests")
    add_subdirectory("${ORCA_TESTS_DIR}/catch2" "${CMAKE_CURRENT_BINARY_DIR}/tests/catch2")

    # Replacements for the helpers defined in upstream tests/CMakeLists.txt:
    # data lives on the device, and tests are discovered by the device runner.
    add_library(test_common INTERFACE)
    target_compile_definitions(test_common INTERFACE TEST_DATA_DIR=R"\(${ENGINE_DEVICE_TEST_DIR}/data\)" CATCH_CONFIG_FAST_COMPILE)
    target_include_directories(test_common INTERFACE "${ORCA_TESTS_DIR}" "${LIBDIR}")
    function(orcaslicer_copy_test_dlls)
    endfunction()
    function(orcaslicer_discover_tests TARGET)
    endfunction()

    add_subdirectory("${ORCA_TESTS_DIR}/libslic3r" "${CMAKE_CURRENT_BINARY_DIR}/tests/libslic3r")
    add_subdirectory("${ORCA_TESTS_DIR}/fff_print" "${CMAKE_CURRENT_BINARY_DIR}/tests/fff_print")

    # The app's facade over the engine: bundled profiles, slicing, cancellation.
    add_executable(orca_engine_adapter_tests "${CMAKE_CURRENT_LIST_DIR}/../slicing/native/src/test/cpp/orca_engine_adapter_tests.cpp")
    target_link_libraries(orca_engine_adapter_tests PRIVATE orca_engine_adapter boost_libs Catch2::Catch2WithMain)
    target_compile_definitions(orca_engine_adapter_tests PRIVATE ENGINE_DEVICE_TEST_DIR="${ENGINE_DEVICE_TEST_DIR}")
    set_target_properties(orca_engine_adapter_tests PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/tests/orca_engine_adapter")

    # Slices one model through the facade for the comparison with desktop OrcaSlicer.
    add_executable(orca_engine_slice "${CMAKE_CURRENT_LIST_DIR}/../slicing/native/src/test/cpp/orca_engine_slice.cpp")
    target_link_libraries(orca_engine_slice PRIVATE orca_engine_adapter)
    set_target_properties(orca_engine_slice PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/tests/golden")
endif()
