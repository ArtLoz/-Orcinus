# GMP and MPFR back CGAL's CORE number type, which libslic3r's MeshBoolean.cpp
# needs through libigl's CGAL wrappers. Archives and patches come from
# upstream deps/GMP and deps/MPFR; compiler flags and configure options mirror
# those recipes, with the NDK compilers in place of the host ones.

set(ORCA_GMP_CFLAGS "-O2 -DNDEBUG -fPIC -DPIC -Wall -Wmissing-prototypes -Wpointer-arith -pedantic -fomit-frame-pointer -fno-common")

android_autotools_project(GMP
    RECIPE "${ORCA_DEPS_DIR}/GMP/GMP.cmake"
    PATCHES "${ORCA_DEPS_DIR}/GMP/0001-GMP_GCC15.patch"
    CFLAGS "${ORCA_GMP_CFLAGS}"
    CONFIGURE_ARGS --enable-shared=no --enable-cxx=yes --enable-static=yes
)

android_autotools_project(MPFR
    RECIPE "${ORCA_DEPS_DIR}/MPFR/MPFR.cmake"
    BEFORE_CONFIGURE "autoreconf -f -i"
    CFLAGS "${ORCA_GMP_CFLAGS}"
    CONFIGURE_ARGS --enable-shared=no --enable-static=yes "--with-gmp=\"$PREFIX\""
    DEPENDS dep_GMP
)
