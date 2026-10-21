# JNI library of :render:scene. Added by engine/CMakeLists.txt, next to the
# engine, so it is built with the same toolchain; Gradle packages the
# resulting liborcinus_scene.so. It runs in the app process and links none of
# the engine's libraries, only qhull, which finds convex hulls for OrcaSlicer.

# The tree, apart from the bridge, for the engine's device tests too.
add_library(orcinus_mesh_tree STATIC mesh_tree.cpp)
target_include_directories(orcinus_mesh_tree PUBLIC "${CMAKE_CURRENT_LIST_DIR}")
# The hits are those SceneData.kt's rayTriangle() finds: no fused multiply-adds.
target_compile_options(orcinus_mesh_tree PRIVATE -ffp-contract=off)
set_target_properties(orcinus_mesh_tree PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(orcinus_scene SHARED scene_bridge.cpp convex_hull.cpp)
target_link_libraries(orcinus_scene PRIVATE orcinus_mesh_tree qhull)
target_link_options(orcinus_scene PRIVATE "-Wl,--exclude-libs,ALL")
set_target_properties(
    orcinus_scene
    PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/jniLibs/${ANDROID_ABI}"
)
