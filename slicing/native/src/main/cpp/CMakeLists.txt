# JNI library of :slicing:native. Added by engine/CMakeLists.txt so it is built
# with the same toolchain and flags as OrcaSlicer's libslic3r; Gradle packages
# the resulting liborcinus_engine.so.

add_library(orca_engine_adapter STATIC
    android_log_sink.cpp
    calibration.cpp
    color_space.cpp
    config_manipulation.cpp
    create_presets.cpp
    create_printer.cpp
    cut.cpp
    engine_context.cpp
    flush_volumes.cpp
    nanosvg.cpp
    orca_engine_adapter.cpp
    painting.cpp
    preset_export.cpp
    presets.cpp
    project_3mf.cpp
    settings_dialogs.cpp
    settings_fields.cpp
    settings_tab.cpp
    settings_tabs.cpp
    setup_catalog.cpp
    tab_filament.cpp
    tab_print.cpp
    tab_print_model.cpp
    tab_printer.cpp
    wipe_tower.cpp
    # libvgcode::convert() from GCodeProcessorResult, as the desktop preview loads G-code.
    "${LIBDIR}/slic3r/GUI/LibVGCode/LibVGCodeWrapper.cpp"
)
target_include_directories(orca_engine_adapter PUBLIC "${CMAKE_CURRENT_LIST_DIR}")
# LibVGCodeWrapper.hpp includes libvgcode relative to src/slic3r, and
# slic3r/GUI/GUI_Preview.hpp, a wxWidgets panel it does not use: its include
# guard is defined so the header is skipped.
target_include_directories(orca_engine_adapter PRIVATE "${LIBDIR}/slic3r")
target_compile_definitions(orca_engine_adapter PRIVATE slic3r_GUI_Preview_hpp_)
find_library(android_log_library log REQUIRED)
target_link_libraries(
    orca_engine_adapter
    PUBLIC libslic3r
    PRIVATE boost_libs CGAL::CGAL nanosvg PNG::PNG libvgcode orcinus_toolpaths_file ${android_log_library}
)

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
