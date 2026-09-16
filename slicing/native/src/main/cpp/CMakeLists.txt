# JNI library of :slicing:native. Added by engine/CMakeLists.txt so it is built
# with the same toolchain and flags as OrcaSlicer's libslic3r; Gradle packages
# the resulting liborcinus_engine.so.

add_library(orca_engine_adapter STATIC
    android_log_sink.cpp
    orca_engine_adapter.cpp
)
target_include_directories(orca_engine_adapter PUBLIC "${CMAKE_CURRENT_LIST_DIR}")
find_library(android_log_library log REQUIRED)
target_link_libraries(orca_engine_adapter PUBLIC libslic3r PRIVATE boost_libs ${android_log_library})

add_library(orcinus_engine SHARED native_bridge.cpp)
target_link_libraries(orcinus_engine PRIVATE orca_engine_adapter)
# Export only the JNI entry points. Symbols from OrcaSlicer and its static
# dependencies stay local, so code unreachable from JNI is dropped the same way
# it is in executables (for example SVG import, whose nanosvg implementation is
# compiled by Orca's desktop GUI).
target_link_options(orcinus_engine PRIVATE "-Wl,--exclude-libs,ALL")
set_target_properties(
    orcinus_engine
    PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/jniLibs/${ANDROID_ABI}"
)
