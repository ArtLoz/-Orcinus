include_guard(GLOBAL)

# Autotools dependencies (GMP, MPFR) are cross-compiled with the NDK compilers
# from a POSIX shell. On Windows the shell, make, and m4 come from the portable
# MSYS2 prepared by scripts/engine.ps1.
set(ENGINE_POSIX_SHELL "" CACHE FILEPATH "bash used for autotools dependencies (MSYS2 bash on Windows)")
if(NOT ENGINE_POSIX_SHELL)
    if(CMAKE_HOST_WIN32)
        message(FATAL_ERROR "Set ENGINE_POSIX_SHELL to MSYS2 bash; scripts/engine.ps1 prepares it")
    endif()
    find_program(ENGINE_POSIX_SHELL bash REQUIRED)
endif()

# Reads the download URLs and SHA256 of an upstream recipe that calls
# ExternalProject_Add directly, so the pinned archive stays OrcaSlicer's.
function(orca_recipe_download recipe out_urls out_sha256)
    file(STRINGS "${recipe}" lines)
    set(urls "")
    set(sha256 "")
    foreach(line IN LISTS lines)
        string(REGEX REPLACE "#.*$" "" line "${line}")
        string(REGEX MATCHALL "https?://[^ \t\"]+\\.tar\\.[a-z0-9]+" found "${line}")
        list(APPEND urls ${found})
        if(line MATCHES "URL_HASH[ \t]+SHA256=([0-9a-fA-F]+)")
            set(sha256 "${CMAKE_MATCH_1}")
        endif()
    endforeach()
    if(NOT urls OR NOT sha256)
        message(FATAL_ERROR "Cannot read the archive URL and SHA256 from ${recipe}")
    endif()
    set(${out_urls} "${urls}" PARENT_SCOPE)
    set(${out_sha256} "${sha256}" PARENT_SCOPE)
endfunction()

# android_autotools_project(<Name>
#     RECIPE <upstream recipe providing URL and SHA256>
#     [PATCHES <patch>...]
#     [BEFORE_CONFIGURE <shell command>]
#     CFLAGS <flags>
#     CONFIGURE_ARGS <arg>...
#     [DEPENDS <target>...])
function(android_autotools_project name)
    cmake_parse_arguments(A "" "RECIPE;BEFORE_CONFIGURE;CFLAGS" "PATCHES;CONFIGURE_ARGS;DEPENDS" ${ARGN})
    orca_recipe_download("${A_RECIPE}" urls sha256)

    set(source_dir "${CMAKE_BINARY_DIR}/dep_${name}-prefix/src/dep_${name}")
    set(script_dir "${CMAKE_BINARY_DIR}/autotools/${name}")
    list(JOIN A_CONFIGURE_ARGS " " configure_args)

    set(env_script "${script_dir}/env.sh")
    file(CONFIGURE OUTPUT "${env_script}" @ONLY CONTENT [[
set -eu
to_posix() {
    if command -v cygpath >/dev/null 2>&1; then cygpath -u "$1"; else printf '%s\n' "$1"; fi
}
export PATH="$(to_posix '@ANDROID_TOOLCHAIN_ROOT@')/bin:$PATH"
SOURCE_DIR="$(to_posix '@source_dir@')"
PREFIX="$(to_posix '@DESTDIR@')"
export CC="clang --target=@ANDROID_LLVM_TRIPLE@"
export CXX="clang++ --target=@ANDROID_LLVM_TRIPLE@"
export AR=llvm-ar NM=llvm-nm RANLIB=llvm-ranlib STRIP=llvm-strip
export CC_FOR_BUILD="${CC_FOR_BUILD:-gcc}"
export CFLAGS="@A_CFLAGS@"
export CXXFLAGS="@A_CFLAGS@"
cd "$SOURCE_DIR"
]])
    file(CONFIGURE OUTPUT "${script_dir}/configure.sh" @ONLY CONTENT [[
. '@env_script@'
@A_BEFORE_CONFIGURE@
./configure --host=@TOOLCHAIN_PREFIX@ --prefix="$PREFIX" @configure_args@
]])
    file(CONFIGURE OUTPUT "${script_dir}/build.sh" @ONLY CONTENT [[
. '@env_script@'
make -j@NPROC@
]])
    file(CONFIGURE OUTPUT "${script_dir}/install.sh" @ONLY CONTENT [[
. '@env_script@'
make install
]])

    set(shell ${CMAKE_COMMAND} -E env MSYSTEM=MSYS CHERE_INVOKING=1 MSYS2_PATH_TYPE=minimal "${ENGINE_POSIX_SHELL}" -l)
    set(patch_command "")
    if(A_PATCHES)
        # A Windows checkout with core.autocrlf stores upstream patches with CRLF,
        # which turns empty context lines into "\r"; apply them with the
        # repository's LF endings instead.
        set(patch_lines "")
        foreach(patch IN LISTS A_PATCHES)
            get_filename_component(patch_name "${patch}" NAME)
            string(APPEND patch_lines
                "tr -d '\\r' < \"$(to_posix '${patch}')\" > \"$(to_posix '${script_dir}')/${patch_name}\"\n"
                "\"$(to_posix '${GIT_EXECUTABLE}')\" apply --verbose \"$(to_posix '${script_dir}')/${patch_name}\"\n")
        endforeach()
        file(CONFIGURE OUTPUT "${script_dir}/patch.sh" @ONLY CONTENT [[
. '@env_script@'
@patch_lines@
]])
        set(patch_command PATCH_COMMAND ${shell} "${script_dir}/patch.sh")
    endif()

    ExternalProject_Add(
        dep_${name}
        EXCLUDE_FROM_ALL ON
        URL ${urls}
        URL_HASH SHA256=${sha256}
        DOWNLOAD_DIR "${DEP_DOWNLOAD_DIR}/${name}"
        SOURCE_DIR "${source_dir}"
        BUILD_IN_SOURCE ON
        LIST_SEPARATOR |
        ${patch_command}
        CONFIGURE_COMMAND ${shell} "${script_dir}/configure.sh"
        BUILD_COMMAND ${shell} "${script_dir}/build.sh"
        INSTALL_COMMAND ${shell} "${script_dir}/install.sh"
        DEPENDS ${A_DEPENDS}
    )
endfunction()
