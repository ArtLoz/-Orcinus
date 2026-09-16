cmake_minimum_required(VERSION 3.24)

# Android dependency bundle for the OrcaSlicer engine.
#
# Versions, archive hashes, patches, and CMake options come from OrcaSlicer's own
# recipes in upstream/OrcaSlicer/deps/<Name>/<Name>.cmake. This project replaces
# only the desktop driver around them, so a dependency bump in Orca reaches the
# Android build without copying anything into this repository.

project(OrcinusEngineDeps LANGUAGES C CXX)

if(NOT ANDROID)
    message(FATAL_ERROR "Configure with engine/cmake/android-arm64.toolchain.cmake")
endif()

get_filename_component(ORCA_UPSTREAM_DIR "${CMAKE_CURRENT_LIST_DIR}/../../upstream/OrcaSlicer" ABSOLUTE)
set(ORCA_DEPS_DIR "${ORCA_UPSTREAM_DIR}/deps")

include(ExternalProject)
include(ProcessorCount)
ProcessorCount(NPROC)
if(NPROC EQUAL 0)
    set(NPROC 1)
endif()

find_package(Git REQUIRED)

set(DESTDIR "${CMAKE_BINARY_DIR}/prefix" CACHE PATH "Install prefix of the Android dependency bundle")
set(DEP_DOWNLOAD_DIR "${CMAKE_CURRENT_LIST_DIR}/../build/downloads" CACHE PATH "Downloaded dependency archives")
# Same options as upstream deps-linux.cmake.
set(DEP_CMAKE_OPTS "-DCMAKE_POSITION_INDEPENDENT_CODE=ON")
set(PATCH_CMD ${GIT_EXECUTABLE} apply --verbose --ignore-space-change --whitespace=fix)

# Android replacement for the orcaslicer_add_cmake_project() defined in
# upstream deps/CMakeLists.txt. ANDROID_ARGS_<Name> adds target-specific options
# without touching the recipe.
function(orcaslicer_add_cmake_project projectname)
    cmake_parse_arguments(P_ARGS "" "INSTALL_DIR;BUILD_COMMAND;INSTALL_COMMAND" "CMAKE_ARGS" ${ARGN})
    ExternalProject_Add(
        dep_${projectname}
        EXCLUDE_FROM_ALL ON
        INSTALL_DIR "${DESTDIR}"
        DOWNLOAD_DIR "${DEP_DOWNLOAD_DIR}/${projectname}"
        CMAKE_ARGS
            -DCMAKE_POLICY_VERSION_MINIMUM=3.5
            -DCMAKE_TOOLCHAIN_FILE:FILEPATH=${CMAKE_TOOLCHAIN_FILE}
            -DCMAKE_BUILD_TYPE:STRING=Release
            -DCMAKE_INSTALL_PREFIX:PATH=${DESTDIR}
            -DCMAKE_PREFIX_PATH:PATH=${DESTDIR}
            -DCMAKE_FIND_ROOT_PATH:PATH=${DESTDIR}
            -DCMAKE_MODULE_PATH:PATH=${ORCA_UPSTREAM_DIR}/cmake/modules
            -DBUILD_SHARED_LIBS:BOOL=OFF
            ${DEP_CMAKE_OPTS}
            ${P_ARGS_CMAKE_ARGS}
            ${ANDROID_ARGS_${projectname}}
        ${P_ARGS_UNPARSED_ARGUMENTS}
        BUILD_COMMAND ${CMAKE_COMMAND} --build . --config Release -- -j${NPROC}
        INSTALL_COMMAND ${CMAKE_COMMAND} --build . --target install --config Release
    )
endfunction()

# zlib is part of the stable NDK API, the Android counterpart of the system
# zlib that upstream uses on Linux.
set(ZLIB_PKG "")

include("${ORCA_DEPS_DIR}/PNG/PNG.cmake")
include("${ORCA_DEPS_DIR}/EXPAT/EXPAT.cmake")

set(DEP_Boost_COMPONENTS system iostreams filesystem thread log locale regex date_time)
include("${ORCA_DEPS_DIR}/Boost/Boost.cmake")
include("${ORCA_DEPS_DIR}/Cereal/Cereal.cmake")
include("${ORCA_DEPS_DIR}/Qhull/Qhull.cmake")
include("${ORCA_DEPS_DIR}/TBB/TBB.cmake")

include("${CMAKE_CURRENT_LIST_DIR}/cmake/AndroidAutotools.cmake")
include("${CMAKE_CURRENT_LIST_DIR}/recipes/GMP_MPFR.cmake")
include("${ORCA_DEPS_DIR}/Eigen/Eigen.cmake")
include("${ORCA_DEPS_DIR}/CGAL/CGAL.cmake")

include("${ORCA_DEPS_DIR}/NLopt/NLopt.cmake")
include("${ORCA_DEPS_DIR}/libnoise/libnoise.cmake")
include("${ORCA_DEPS_DIR}/Draco/Draco.cmake")
include("${ORCA_DEPS_DIR}/JPEG/JPEG.cmake")
include("${CMAKE_CURRENT_LIST_DIR}/recipes/LibreSSL.cmake")

# Model.hpp includes the STEP importer header, which includes OpenCASCADE, so
# every Print translation unit needs OCCT exactly as on desktop.
include("${ORCA_DEPS_DIR}/FREETYPE/FREETYPE.cmake")
set(FREETYPE_PKG dep_FREETYPE)
include("${ORCA_DEPS_DIR}/OCCT/OCCT.cmake")

add_custom_target(
    deps ALL
    DEPENDS
        dep_PNG
        dep_EXPAT
        dep_Boost
        dep_Cereal
        dep_Qhull
        dep_TBB
        dep_GMP
        dep_MPFR
        dep_Eigen
        dep_CGAL
        dep_NLopt
        dep_libnoise
        dep_Draco
        dep_JPEG
        dep_LibreSSL
        dep_FREETYPE
        dep_OCCT
)
