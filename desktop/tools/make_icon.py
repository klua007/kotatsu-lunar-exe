#!/usr/bin/env python3
"""Generates the Kotatsu Lunar application icon (PNG + Windows ICO).

The icon is an original asset of this port (it is not the Kotatsu-Redo launcher icon), so it can be
redistributed with the GPL-3.0-or-later sources of this repository. The script only uses the Python
standard library so that the binary asset is reproducible without extra tooling:

    python3 desktop/tools/make_icon.py desktop/resources/icon
"""

import os
import struct
import sys
import zlib


def rounded_rect_mask(width, height, radius):
    for y in range(height):
        for x in range(width):
            cx = min(max(x, radius), width - 1 - radius)
            cy = min(max(y, radius), height - 1 - radius)
            dx = x - cx
            dy = y - cy
            inside = dx * dx + dy * dy <= radius * radius
            if inside:
                yield x, y


def render(size):
    """Returns RGBA bytes of the icon at the given size."""
    scale = size / 256.0

    def s(value):
        return int(round(value * scale))

    accent_top = (242, 97, 122)
    accent_bottom = (200, 40, 70)
    pixels = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]

    # Background: rounded square with a vertical gradient
    radius = s(48)
    for x, y in rounded_rect_mask(size, size, radius):
        t = y / max(size - 1, 1)
        r = int(accent_top[0] * (1 - t) + accent_bottom[0] * t)
        g = int(accent_top[1] * (1 - t) + accent_bottom[1] * t)
        b = int(accent_top[2] * (1 - t) + accent_bottom[2] * t)
        pixels[y][x] = (r, g, b, 255)

    # "Book" glyph: two white pages with a dark spine
    page_w = s(64)
    page_h = s(108)
    left_x = s(60)
    right_x = left_x + page_w + s(8)
    top = s(76)
    for y in range(top, min(top + page_h, size)):
        for x in range(left_x, min(left_x + page_w, size)):
            pixels[y][x] = (250, 250, 250, 255)
        for x in range(right_x, min(right_x + page_w, size)):
            pixels[y][x] = (250, 250, 250, 255)
    for y in range(top, top + page_h):
        for x in range(left_x + page_w, min(right_x, size)):
            if 0 <= y < size:
                pixels[y][x] = (245, 245, 245, 255)

    # Text lines on the pages
    line_thickness = max(1, s(7))
    gutter = s(14)
    for index in range(4):
        line_y = top + s(22) + index * s(24)
        for y in range(line_y, min(line_y + line_thickness, size)):
            for x in range(left_x + gutter, min(left_x + page_w - gutter, size)):
                pixels[y][x] = (60, 60, 72, 255)
            for x in range(right_x + gutter, min(right_x + page_w - gutter, size)):
                pixels[y][x] = (60, 60, 72, 255)

    return bytes(component for row in pixels for pixel in row for component in pixel)


def write_png(path, size):
    raw = render(size)
    # Convert RGBA to PNG scanlines with filter byte 0
    stride = size * 4
    data = b"".join(b"\x00" + raw[y * stride:(y + 1) * stride] for y in range(size))

    def chunk(kind, payload):
        body = kind + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(data, 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as handle:
        handle.write(png)


def write_ico(path, sizes):
    """Writes an uncompressed 32-bit ICO (BMP entries), accepted by WiX and by Windows."""
    images = []
    for size in sizes:
        rgba = render(size)
        # BGRA bottom-up rows
        rows = []
        for y in range(size - 1, -1, -1):
            row = bytearray()
            for x in range(size):
                offset = (y * size + x) * 4
                r, g, b, a = rgba[offset:offset + 4]
                row += bytes((b, g, r, a))
            rows.append(bytes(row))
        pixels = b"".join(rows)
        mask_stride = ((size + 31) // 32) * 4
        mask = b"\x00" * (mask_stride * size)
        header = struct.pack("<IiiHHIIiiII", 40, size, size * 2, 1, 32, 0, len(pixels) + len(mask), 0, 0, 0, 0)
        images.append(header + pixels + mask)

    header = struct.pack("<HHH", 0, 1, len(images))
    offset = 6 + 16 * len(images)
    entries = b""
    for size, image in zip(sizes, images):
        entries += struct.pack(
            "<BBBBHHII",
            size if size < 256 else 0,
            size if size < 256 else 0,
            0,
            0,
            1,
            32,
            len(image),
            offset,
        )
        offset += len(image)
    with open(path, "wb") as handle:
        handle.write(header + entries + b"".join(images))


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "desktop/resources/icon"
    os.makedirs(os.path.dirname(base) or ".", exist_ok=True)
    write_png(base + ".png", 256)
    write_ico(base + ".ico", [16, 24, 32, 48, 64, 128, 256])
    print("wrote", base + ".png", "and", base + ".ico")


if __name__ == "__main__":
    main()
