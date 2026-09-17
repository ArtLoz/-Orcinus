# JNI library of :render:gcode. Added by engine/CMakeLists.txt, next to the
# engine, so OrcaSlicer's libvgcode is built with the same toolchain; Gradle
# packages the resulting liborcinus_toolpaths.so. It runs in the app process
# and links neither libslic3r nor its dependencies.

# The toolpaths file format, shared with the engine, which writes it.
add_library(orcinus_toolpaths_file INTERFACE)
target_include_directories(orcinus_toolpaths_file INTERFACE "${CMAKE_CURRENT_LIST_DIR}" "${LIBDIR}")

add_library(orcinus_toolpaths SHARED toolpaths_bridge.cpp)
find_library(android_log_library log REQUIRED)
target_link_libraries(orcinus_toolpaths PRIVATE orcinus_toolpaths_file libvgcode ${android_log_library})
target_link_options(orcinus_toolpaths PRIVATE "-Wl,--exclude-libs,ALL")
set_target_properties(
    orcinus_toolpaths
    PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/jniLibs/${ANDROID_ABI}"
)
