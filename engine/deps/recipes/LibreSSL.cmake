# libslic3r needs OpenSSL only for the MD5 API: bbl_calc_md5() in utils.cpp and
# the G-code digests written by Format/bbs_3mf.cpp. Upstream's OpenSSL 1.1.1w
# recipe runs Perl Configure and make, which need a POSIX shell that the Windows
# build host does not provide. LibreSSL's libcrypto exposes the same
# <openssl/md5.h> API and ships a CMake build with Android support.
orcaslicer_add_cmake_project(LibreSSL
    URL https://cdn.openbsd.org/pub/OpenBSD/LibreSSL/libressl-4.3.2.tar.gz
    URL_HASH SHA256=edf01aee24c65d69e6a9efcb9d44bcda682ff9d4f3bbbd95e794e1dfa90847b5
    CMAKE_ARGS
        -DLIBRESSL_APPS=OFF
        -DLIBRESSL_TESTS=OFF
)
