#!/usr/bin/env python3
"""Writes the third-party notices of the native engine and the bundled font.

The Android dependencies are collected at build time by the AboutLibraries
Gradle plugin from their POM files. The native libraries linked into
liborcinus_engine.so, OrcaSlicer's runtime files, and the UI font have no POM,
so this script describes them in AboutLibraries' manual configuration format
(app/notices/libraries and app/notices/licenses). License texts are read from
the sources that are actually built:

* upstream/OrcaSlicer (the pinned submodule) and its deps_src libraries;
* engine/build/deps-arm64/dep_<Name>-prefix/src/dep_<Name>, unpacked by
  scripts/engine.ps1 -Stage deps from the archives pinned by Orca's recipes;
* engine/build/downloads/Inter, downloaded by scripts/subset-fonts.ps1.

Run it after updating the OrcaSlicer submodule and rebuilding the dependencies:

    python scripts/notices/update_notices.py
"""

from __future__ import annotations

import json
import re
import shutil
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ORCA = REPO / "upstream" / "OrcaSlicer"
DEPS_SRC = ORCA / "deps_src"
DEPS_BUILD = REPO / "engine" / "build" / "deps-arm64"
INTER_ARCHIVE = REPO / "engine" / "build" / "downloads" / "Inter" / "Inter-4.1.zip"
OUTPUT = REPO / "app" / "notices"

MIT_BODY = """Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""


def dep(name: str, *path: str) -> Path:
    """A file of a dependency unpacked by engine/deps."""
    return DEPS_BUILD / f"dep_{name}-prefix" / "src" / f"dep_{name}" / Path(*path)


def read(path: Path) -> str:
    if not path.is_file():
        sys.exit(f"missing {path.relative_to(REPO)}: build the engine dependencies and the fonts first")
    data = path.read_bytes()
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        # A few headers, such as fast_float's, are Latin-1.
        text = data.decode("latin-1")
    return text.replace("\r\n", "\n").replace("\r", "\n").strip("\n")


def read_zip(archive: Path, name: str) -> str:
    if not archive.is_file():
        read(archive)
    with zipfile.ZipFile(archive) as zip_file:
        return zip_file.read(name).decode("utf-8").replace("\r\n", "\n").strip("\n")


_COMMENT_PREFIX = re.compile(r"^\s*(?:/\*+|\*+(?!/)|//+)\s?")
_COMMENT_SUFFIX = re.compile(r"\s*\*+/?\s*$")


def comment(path: Path, first: str, last: str) -> str:
    """Lines from the one matching `first` to the next one matching `last`, without comment markup."""
    lines = read(path).split("\n")
    start = next((i for i, line in enumerate(lines) if re.search(first, line)), None)
    if start is None:
        sys.exit(f"{path.relative_to(REPO)}: no line matches {first!r}")
    end = next((i for i in range(start + 1, len(lines)) if re.search(last, lines[i])), None)
    if end is None:
        sys.exit(f"{path.relative_to(REPO)}: no line after {first!r} matches {last!r}")
    cleaned = [_COMMENT_SUFFIX.sub("", _COMMENT_PREFIX.sub("", line)).rstrip() for line in lines[start:end + 1]]
    return "\n".join(cleaned).strip("\n")


def recipe_version(recipe: str, pattern: str) -> str:
    """The version in an upstream recipe, e.g. deps/Boost/Boost.cmake."""
    text = read(ORCA / "deps" / recipe)
    match = re.search(pattern, text)
    if not match:
        sys.exit(f"deps/{recipe}: version pattern {pattern!r} not found")
    return match.group(1)


@dataclass
class License:
    id: str
    name: str
    content: str
    url: str | None = None


@dataclass
class Library:
    id: str
    name: str
    website: str
    licenses: list[str]
    description: str = ""
    version: str | None = None
    developers: list[str] = field(default_factory=list)


def licenses() -> list[License]:
    return [
        # Texts shared by several components. The ids are SPDX ids, so the
        # plugin also attaches these texts to Maven artifacts under them.
        License("AGPL-3.0-only", "GNU Affero General Public License v3.0", read(ORCA / "LICENSE.txt"), "https://www.gnu.org/licenses/agpl-3.0.html"),
        License("Apache-2.0", "Apache License 2.0", read(dep("TBB", "LICENSE.txt")), "https://www.apache.org/licenses/LICENSE-2.0"),
        License("BSL-1.0", "Boost Software License 1.0", read(dep("Boost", "LICENSE_1_0.txt")), "https://www.boost.org/LICENSE_1_0.txt"),
        License("GPL-2.0", "GNU General Public License v2.0", read(dep("GMP", "COPYINGv2")), "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"),
        License("GPL-3.0", "GNU General Public License v3.0", read(dep("GMP", "COPYINGv3")), "https://www.gnu.org/licenses/gpl-3.0.html"),
        License("LGPL-2.1", "GNU Lesser General Public License v2.1", read(dep("OCCT", "LICENSE_LGPL_21.txt")), "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"),
        License("LGPL-3.0", "GNU Lesser General Public License v3.0", read(dep("GMP", "COPYING.LESSERv3")), "https://www.gnu.org/licenses/lgpl-3.0.html"),
        License("MPL-2.0", "Mozilla Public License 2.0", read(dep("Eigen", "COPYING.MPL2")), "https://www.mozilla.org/MPL/2.0/"),
        # Texts that carry a component's own copyright notice.
        License("agg", "Anti-Grain Geometry License", read(DEPS_SRC / "agg" / "copying")),
        License("cereal", "BSD 3-Clause License", read(dep("Cereal", "LICENSE"))),
        License("cgal", "CGAL License", read(dep("CGAL", "LICENSE"))),
        License("eigen", "Eigen third-party code licenses", "\n\n".join(
            read(dep("Eigen", name)) for name in ("COPYING.README", "COPYING.BSD", "COPYING.MINPACK")
        )),
        License("expat", "MIT License", read(DEPS_SRC / "expat" / "COPYING")),
        License("fast-float", "MIT License", comment(DEPS_SRC / "fast_float" / "fast_float.h", r"fast_float by Daniel Lemire", r"DEALINGS IN THE SOFTWARE")),
        License("freetype", "FreeType License", read(dep("FREETYPE", "docs", "FTL.TXT")), "https://freetype.org/license.html"),
        License("glu-libtess", "SGI Free Software License B 2.0", comment(DEPS_SRC / "glu-libtess" / "include" / "glu-libtess.h", r"SGI FREE SOFTWARE LICENSE B", r"^\s*\*\s*Silicon Graphics, Inc\.\s*$")),
        License("inter", "SIL Open Font License 1.1", read_zip(INTER_ARCHIVE, "LICENSE.txt"), "https://openfontlicense.org"),
        License("libjpeg-turbo", "libjpeg-turbo Licenses", read(dep("JPEG", "LICENSE.md")) + "\n\n" + read(dep("JPEG", "README.ijg"))),
        License("libpng", "PNG Reference Library License", read(dep("PNG", "LICENSE"))),
        License("libressl", "OpenSSL, SSLeay and ISC Licenses", read(dep("LibreSSL", "COPYING"))),
        License("mcut", "MCUT License", read(DEPS_SRC / "mcut" / "LICENSE.txt")),
        License("miniz", "MIT License", read(DEPS_SRC / "miniz" / "LICENSE")),
        License("nanosvg", "zlib License", comment(DEPS_SRC / "nanosvg" / "nanosvg.h", r"Copyright \(c\) 2013-14 Mikko Mononen", r"This notice may not be removed")),
        License("nlohmann-json", "MIT License", comment(DEPS_SRC / "nlohmann" / "json.hpp", r"Licensed under the MIT License", r"SOFTWARE\.")),
        License("nlopt", "NLopt License", read(dep("NLopt", "COPYING"))),
        License("occt-exception", "Open CASCADE Exception 1.0 to LGPL 2.1", read(dep("OCCT", "OCCT_LGPL_EXCEPTION.txt"))),
        License("qhull", "Qhull License", read(dep("Qhull", "COPYING.txt"))),
        License("qoi", "MIT License", comment(DEPS_SRC / "qoi" / "qoi.h", r"Copyright\(c\) 2021 Dominic Szablewski", r"^SOFTWARE\.")),
        License("semver", "MIT License", "Copyright (c) 2015-2017 Tomas Aparicio\n\n" + MIT_BODY),
        License("shiny", "MIT License", comment(DEPS_SRC / "Shiny" / "Shiny.h", r"The MIT License", r"THE SOFTWARE\.")),
        License("stb-truetype", "MIT License", comment(DEPS_SRC / "imgui" / "imstb_truetype.h", r"^Copyright \(c\) 2017 Sean Barrett", r"^SOFTWARE\.")),
        License("unordered-dense", "MIT License", comment(DEPS_SRC / "ankerl" / "unordered_dense.h", r"Copyright \(c\) 2022-2024 Martin Leitner-Ankerl", r"^// SOFTWARE\.")),
    ]


def libraries() -> list[Library]:
    orca_lock = json.loads(read(REPO / "upstream" / "orca.lock.json"))
    return [
        Library(
            "native:orcaslicer", "OrcaSlicer", "https://github.com/OrcaSlicer/OrcaSlicer", ["AGPL-3.0-only"],
            "The slicing engine (libslic3r), printer and material profiles, and icons. OrcaSlicer is based on "
            "Bambu Studio by Bambu Lab, which is based on PrusaSlicer by Prusa Research, which is based on "
            "Slic3r by Alessandro Ranellucci and the RepRap community.",
            orca_lock["release"].removeprefix("v"), ["SoftFever", "OrcaSlicer contributors"],
        ),
        Library("native:admesh", "ADMesh", "https://github.com/admesh/admesh", ["GPL-2.0"],
                "Copyright (C) 1995, 1996 Anthony D. Martin; Copyright (C) 2013, 2014 ADMesh contributors. "
                "Licensed under the GNU GPL version 2 or (at your option) any later version."),
        Library("native:agg", "Anti-Grain Geometry", "https://agg.sourceforge.net/antigrain.com/", ["agg"]),
        Library("native:boost", "Boost", "https://www.boost.org", ["BSL-1.0"],
                version=recipe_version("Boost/Boost.cmake", r"boost-(\d+\.\d+\.\d+)")),
        Library("native:cereal", "cereal", "https://uscilab.github.io/cereal/", ["cereal"],
                version=recipe_version("Cereal/Cereal.cmake", r"tags/v([\d.]+)\.zip")),
        Library("native:cgal", "CGAL", "https://www.cgal.org", ["cgal", "GPL-3.0", "LGPL-3.0", "BSL-1.0"],
                version=recipe_version("CGAL/CGAL.cmake", r"CGAL-([\d.]+)\.zip")),
        Library("native:clipper", "Clipper", "https://www.angusj.com/clipper2/Docs/Overview.htm", ["BSL-1.0"],
                "Copyright (c) 2010-2017 Angus Johnson."),
        Library("native:clipper2", "Clipper2", "https://github.com/AngusJohnson/Clipper2", ["BSL-1.0"],
                "Copyright (c) 2010-2024 Angus Johnson."),
        Library("native:draco", "Draco", "https://google.github.io/draco/", ["Apache-2.0"],
                version=recipe_version("Draco/Draco.cmake", r"tags/([\d.]+)\.zip")),
        Library("native:eigen", "Eigen", "https://eigen.tuxfamily.org", ["MPL-2.0", "eigen", "Apache-2.0"],
                version=recipe_version("Eigen/Eigen.cmake", r"eigen-([\d.]+)\.zip")),
        Library("native:expat", "Expat", "https://libexpat.github.io", ["expat"]),
        Library("native:fast-float", "fast_float", "https://github.com/fastfloat/fast_float", ["fast-float"]),
        Library("native:freetype", "FreeType", "https://freetype.org", ["freetype"],
                "Portions of this software are copyright © 2022 The FreeType Project (https://freetype.org). "
                "All rights reserved.",
                recipe_version("FREETYPE/FREETYPE.cmake", r"freetype-([\d.]+)\.tar\.gz")),
        Library("native:glu-libtess", "GLU tesselator", "https://gitlab.freedesktop.org/mesa/glu", ["glu-libtess"]),
        Library("native:gmp", "GNU Multiple Precision Arithmetic Library", "https://gmplib.org", ["LGPL-3.0", "GPL-3.0"],
                version=recipe_version("GMP/GMP.cmake", r"gmp-([\d.]+)\.tar")),
        Library("native:libigl", "libigl", "https://libigl.github.io", ["MPL-2.0"]),
        Library("native:libjpeg-turbo", "libjpeg-turbo", "https://libjpeg-turbo.org", ["libjpeg-turbo"],
                "This software is based in part on the work of the Independent JPEG Group.",
                recipe_version("JPEG/JPEG.cmake", r"tags/([\d.]+)\.zip")),
        Library("native:libnest2d", "libnest2d", "https://github.com/tamasmeszaros/libnest2d", ["LGPL-3.0", "GPL-3.0"]),
        Library("native:libnoise", "libnoise", "https://libnoise.sourceforge.net", ["LGPL-2.1"],
                "Copyright (C) 2003, 2004 Jason Bevins. Licensed under the GNU LGPL version 2.1 or (at your option) "
                "any later version."),
        Library("native:libpng", "libpng", "http://www.libpng.org/pub/png/libpng.html", ["libpng"],
                version=recipe_version("PNG/PNG.cmake", r"GIT_TAG\s+v([\d.]+)")),
        Library("native:libressl", "LibreSSL (libcrypto)", "https://www.libressl.org", ["libressl"],
                version=re.search(r"libressl-([\d.]+)\.tar", read(REPO / "engine" / "deps" / "recipes" / "LibreSSL.cmake")).group(1)),
        Library("native:mcut", "MCUT", "https://github.com/cutdigital/mcut", ["mcut", "GPL-3.0"],
                "Used under the GNU General Public License option."),
        Library("native:miniz", "miniz", "https://github.com/richgel999/miniz", ["miniz"]),
        Library("native:mpfr", "GNU MPFR", "https://www.mpfr.org", ["LGPL-3.0", "GPL-3.0"],
                version=recipe_version("MPFR/MPFR.cmake", r"mpfr-([\d.]+)\.tar")),
        Library("native:nanosvg", "NanoSVG", "https://github.com/memononen/nanosvg", ["nanosvg"]),
        Library("native:nlohmann-json", "JSON for Modern C++", "https://github.com/nlohmann/json", ["nlohmann-json"]),
        Library("native:nlopt", "NLopt", "https://nlopt.readthedocs.io", ["nlopt", "LGPL-2.1"],
                version=recipe_version("NLopt/NLopt.cmake", r"archive/v([\d.]+)\.tar")),
        Library("native:occt", "Open CASCADE Technology", "https://dev.opencascade.org", ["LGPL-2.1", "occt-exception"],
                "Orcinus makes use of facilities provided by the Open CASCADE Technology software.",
                recipe_version("OCCT/OCCT.cmake", r"tags/V(\d+_\d+_\d+)\.zip").replace("_", ".")),
        Library("native:onetbb", "oneTBB", "https://github.com/uxlfoundation/oneTBB", ["Apache-2.0"],
                version=recipe_version("TBB/TBB.cmake", r"tags/v([\d.]+)\.zip")),
        Library("native:qhull", "Qhull", "http://www.qhull.org", ["qhull"],
                version=recipe_version("Qhull/Qhull.cmake", r"archive/v([\d.]+)\.zip")),
        Library("native:qoi", "QOI", "https://qoiformat.org", ["qoi"]),
        Library("native:semver", "semver.c", "https://github.com/h2non/semver.c", ["semver"]),
        Library("native:shiny", "Shiny Profiler", "https://code.google.com/archive/p/shinyprofiler/", ["shiny"]),
        Library("native:stb-truetype", "stb_truetype", "https://github.com/nothings/stb", ["stb-truetype"]),
        Library("native:unordered-dense", "unordered_dense", "https://github.com/martinus/unordered_dense", ["unordered-dense"]),
        Library("font:inter", "Inter", "https://rsms.me/inter/", ["inter"],
                "The user interface font, cut down to Latin and Cyrillic.", "4.1", ["The Inter Project Authors"]),
    ]


def write_json(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def main() -> None:
    license_list = licenses()
    library_list = libraries()
    known = {license.id for license in license_list}
    for library in library_list:
        unknown = set(library.licenses) - known
        if unknown:
            sys.exit(f"{library.id} refers to undefined licenses {sorted(unknown)}")

    shutil.rmtree(OUTPUT, ignore_errors=True)
    (OUTPUT / "licenses").mkdir(parents=True)
    (OUTPUT / "libraries").mkdir(parents=True)
    for license in license_list:
        value = {"hash": license.id, "name": license.name, "content": license.content}
        if license.url:
            value["url"] = license.url
        write_json(OUTPUT / "licenses" / f"{license.id}.json", value)
    for library in library_list:
        value = {
            "uniqueId": library.id,
            "name": library.name,
            "description": library.description,
            "website": library.website,
            "developers": [{"name": name} for name in library.developers],
            "licenses": library.licenses,
        }
        if library.version:
            value["artifactVersion"] = library.version
        write_json(OUTPUT / "libraries" / f"{library.id.split(':', 1)[1]}.json", value)
    print(f"{len(library_list)} components, {len(license_list)} license texts -> {OUTPUT.relative_to(REPO)}")


if __name__ == "__main__":
    main()
