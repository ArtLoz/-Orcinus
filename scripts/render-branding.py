#!/usr/bin/env python3
"""Renders the store images from branding/ with headless Chrome, Chromium, or Edge.

    python scripts/render-branding.py

Writes fastlane/metadata/android/en-US/images/icon.png (512 x 512, the 32-bit PNG
Google Play asks for) and featureGraphic.png (1024 x 500). The launcher icon
itself is a vector drawable, app/src/main/res/drawable/ic_launcher_foreground.xml,
drawn from the same shapes as branding/icon.svg.
"""

from __future__ import annotations

import os
import shutil
import struct
import subprocess
import sys
import tempfile
import zlib
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
BRANDING = REPO / "branding"
IMAGES = REPO / "fastlane" / "metadata" / "android" / "en-US" / "images"

BROWSERS = [
    os.environ.get("CHROME"),
    shutil.which("google-chrome"),
    shutil.which("chromium"),
    shutil.which("chrome"),
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
]


def browser() -> str:
    for candidate in BROWSERS:
        if candidate and Path(candidate).is_file():
            return candidate
    sys.exit("Chrome, Chromium or Edge is required; set CHROME to its executable")


def screenshot(source: Path, target: Path, width: int, height: int) -> None:
    with tempfile.TemporaryDirectory() as profile:
        subprocess.run(
            [
                browser(), "--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
                f"--user-data-dir={profile}", f"--window-size={width},{height}", f"--screenshot={target}", source.as_uri(),
            ],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )


def _chunks(data: bytes):
    pos = 8
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        yield kind, data[pos + 8:pos + 8 + length]
        pos += 12 + length


def _chunk(kind: bytes, body: bytes) -> bytes:
    return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    return a if pa <= pb and pa <= pc else (b if pb <= pc else c)


def add_alpha(path: Path) -> None:
    """Rewrites an 8-bit RGB PNG as RGBA; browsers write opaque screenshots as RGB."""
    data = path.read_bytes()
    header = next(body for kind, body in _chunks(data) if kind == b"IHDR")
    width, height, depth, color = struct.unpack(">IIBB", header[:10])
    if color == 6:
        return
    if depth != 8 or color != 2:
        sys.exit(f"{path}: unexpected PNG format (depth {depth}, colour type {color})")
    raw = zlib.decompress(b"".join(body for kind, body in _chunks(data) if kind == b"IDAT"))
    bpp, stride = 3, width * 3
    previous, pos, pixels = bytearray(stride), 0, bytearray()
    for _ in range(height):
        kind, line = raw[pos], bytearray(raw[pos + 1:pos + 1 + stride])
        pos += 1 + stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = previous[i]
            c = previous[i - bpp] if i >= bpp else 0
            line[i] = (line[i] + (0, a, b, (a + b) // 2, _paeth(a, b, c))[kind]) & 0xFF
        previous = line
        pixels.append(0)
        for i in range(0, stride, 3):
            pixels += line[i:i + 3] + bytes([0xFF])

    path.write_bytes(
        bytes([0x89]) + b"PNG\r\n" + bytes([0x1A]) + b"\n"
        + _chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + _chunk(b"IDAT", zlib.compress(bytes(pixels), 9))
        + _chunk(b"IEND", b"")
    )


def main() -> None:
    IMAGES.mkdir(parents=True, exist_ok=True)
    icon = IMAGES / "icon.png"
    feature_graphic = IMAGES / "featureGraphic.png"
    screenshot(BRANDING / "icon.svg", icon, 512, 512)
    add_alpha(icon)
    screenshot(BRANDING / "feature-graphic.html", feature_graphic, 1024, 500)
    print(f"{icon.relative_to(REPO)}, {feature_graphic.relative_to(REPO)}")


if __name__ == "__main__":
    main()
