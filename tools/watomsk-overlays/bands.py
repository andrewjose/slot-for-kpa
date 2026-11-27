"""Band logos for the home consoles whose Watomsk packs have none.

Usage: python3 bands.py <assets/watomsk dir>

His GBA and Genesis art has a logo for the band under the picture
(export.py). PlayStation, TurboGrafx-16, PC Engine, NES and SNES only have
side frames, so their band logo is cut from the shipped 16:9 frame's right
panel: the vertical logo, without the console stripes above it, turned to
read left to right, its edges faded into black (Andrew, 2026-11-26).
Writes <name>-band.png next to the frames, at his 1080p scale like the
others.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

# The right panel of a 1920x1080 frame, and the rows holding the logo
# (below the stripes, which end at y ~315).
PANEL = (1680, 1920)
ROWS = {
    "psx": (318, 760),
    "turbografx": (90, 620),
    "pcengine": (90, 620),
    "nes": (322, 760),
    "nes-graylogo": (322, 760),
    "snes": (328, 760),
    "snes-alt": (328, 760),
    "snes-graylogo": (328, 760),
    "snes-graylogo-alt": (328, 760),
}
PAD = 36
FADE = 24


def band(key):
    x0, x1 = PANEL
    y0, y1 = ROWS[key]
    return cut(Image.open(f"{key}-16x9.png"), (x0, y0, x1, y1))


def cut(im, box):
    rgb = np.asarray(im.convert("RGB").crop(box)).astype(np.int32)
    # Room for the padding even where the logo touches the cut (black: the
    # stripes above it stay out).
    rgb = np.pad(rgb, ((PAD, PAD), (PAD, PAD), (0, 0)))
    strong = rgb.max(axis=2) > 48
    ys, xs = np.nonzero(strong)
    if len(xs) == 0:
        raise SystemExit(f"no logo in {box}")
    bx0, by0 = max(xs.min() - PAD, 0), max(ys.min() - PAD, 0)
    bx1 = min(xs.max() + 1 + PAD, rgb.shape[1])
    by1 = min(ys.max() + 1 + PAD, rgb.shape[0])
    logo = Image.fromarray(rgb[by0:by1, bx0:bx1].astype(np.uint8))
    # Fade the edges into black, so any glow or hatching behind the logo
    # never ends in a hard line inside the band.
    w, h = logo.size
    mask = Image.new("L", (w, h), 0)
    mask.paste(255, (FADE, FADE, w - FADE, h - FADE))
    mask = mask.filter(ImageFilter.GaussianBlur(FADE / 2))
    out = Image.new("RGB", (w, h), (0, 0, 0))
    out.paste(logo, (0, 0), mask)
    # Watomsk's vertical logos read top to bottom: turn them a quarter
    # anticlockwise to read left to right, as the Genesis band does.
    return out.rotate(90, expand=True)


def main(folder):
    os.chdir(folder)
    for frame in ROWS:
        img = band(frame)
        px = img.getpixel((0, 0))
        assert px == (0, 0, 0), f"{frame}: the band's colour is its corner: {px}"
        img.save(f"{frame}-band.png", optimize=True)
        print(f"{frame}-band.png {img.size}")


if __name__ == "__main__":
    main(sys.argv[1])
