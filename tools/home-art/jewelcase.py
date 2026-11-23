#!/usr/bin/env python3
"""Draws the CD jewel case mould for PlayStation and TurboGrafx-CD games:
assets/home/jewelcase_{neutral,board,board_battery}.png, _shell.svg, _chip.svg.

Original art (no photo or product art): a clear case, hinge on the left, the
game's front cover in the insert well; opened, the tray holds the disc with the
core's name on a sticker and the title printed on the disc.
Logical size 312 x 276 (a real case is 142 x 125 mm); PNGs at 3x.
The neutral front is grey (128 = the shell colour exactly) for the Overlay
blend the other moulds use. Run from the repository root:
    python3 tools/home-art/jewelcase.py
"""
from PIL import Image, ImageDraw, ImageFilter
import math, os

W, H, K = 312, 276, 3
WELL = (36, 8, 260, 260)          # x, y, w, h: the cover insert (square, as a CD's)
FOOT = (87, 196, 150, 34)         # the core-name sticker on the disc
TITLE = (91.0, 52.0, 130.0, 30.0)  # the title printed on the disc
OUT = "assets/home"


def rr(draw, box, r, **kw):
    draw.rounded_rectangle([v * K for v in box], radius=r * K, **kw)


def neutral():
    im = Image.new("RGBA", (W * K, H * K), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # Case body: clear plastic over the tray's edge, grey = the shell colour.
    rr(d, (0, 0, W - 1, H - 1), 4, fill=(128, 128, 128, 255))
    # Outer lip highlight and inner shadow line.
    rr(d, (0, 0, W - 1, H - 1), 4, outline=(176, 176, 176, 255), width=2 * K)
    rr(d, (3, 3, W - 4, H - 4), 3, outline=(104, 104, 104, 255), width=K)
    # The hinge spine on the left: darker, with ridges and two knuckles.
    d.rectangle([2 * K, 3 * K, 21 * K, (H - 4) * K], fill=(96, 96, 96, 255))
    for x in range(5, 20, 3):
        d.line([x * K, 6 * K, x * K, (H - 7) * K], fill=(84, 84, 84, 255), width=K)
        d.line([(x + 1) * K, 6 * K, (x + 1) * K, (H - 7) * K], fill=(112, 112, 112, 255), width=1)
    for y0 in (10, H - 44):
        rr(d, (3, y0, 20, y0 + 34), 3, fill=(140, 140, 140, 255), outline=(88, 88, 88, 255), width=K)
    d.line([22 * K, 3 * K, 22 * K, (H - 4) * K], fill=(160, 160, 160, 255), width=K)
    # The well's rim (the label covers the well itself).
    x, y, w, h = WELL
    d.rectangle([(x - 1) * K, (y - 1) * K, (x + w + 1) * K, (y + h + 1) * K], outline=(150, 150, 150, 255), width=K)
    # A soft diagonal gloss across the clear lid's frame.
    gloss = Image.new("L", im.size, 0)
    g = ImageDraw.Draw(gloss)
    g.polygon([(0, 0), (120 * K, 0), (40 * K, H * K), (0, H * K)], fill=36)
    gloss = gloss.filter(ImageFilter.GaussianBlur(18 * K))
    white = Image.new("RGBA", im.size, (255, 255, 255, 255))
    alpha = im.split()[3]
    im = Image.composite(Image.blend(im, white, 0.0), im, Image.new("L", im.size, 0))
    lit = Image.composite(white, im, gloss)
    lit.putalpha(alpha)
    return lit


def board(battery):
    im = Image.new("RGBA", (W * K, H * K), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # The open tray: black plastic, with the radial ribs of a CD tray.
    rr(d, (0, 0, W - 1, H - 1), 4, fill=(26, 26, 28, 255))
    rr(d, (6, 6, W - 7, H - 7), 3, fill=(34, 34, 37, 255))
    cx, cy, R = 162, 140, 122
    for a in range(0, 360, 15):
        t = math.radians(a)
        d.line([(cx + math.cos(t) * (R + 4)) * K, (cy + math.sin(t) * (R + 4)) * K,
                (cx + math.cos(t) * (R + 9)) * K, (cy + math.sin(t) * (R + 9)) * K],
               fill=(48, 48, 52, 255), width=2 * K)
    # The disc's label side: a dark print (the names are inked light, as on
    # the cartridges' boards), a silver rim, a clear inner ring, the hub.
    disc = Image.new("RGBA", im.size, (0, 0, 0, 0))
    dd = ImageDraw.Draw(disc)
    def circle(r, **kw):
        dd.ellipse([(cx - r) * K, (cy - r) * K, (cx + r) * K, (cy + r) * K], **kw)
    circle(R, fill=(186, 190, 198, 255))
    circle(R - 3, fill=(52, 56, 66, 255))
    # A faint concentric sheen so it reads as a disc.
    for r in range(R - 6, 46, -6):
        circle(r, outline=(58, 62, 73, 255), width=1)
    circle(44, fill=(170, 174, 182, 255))
    circle(36, fill=(120, 124, 132, 255))
    circle(22, fill=(196, 198, 204, 255))
    circle(8, fill=(34, 34, 37, 255))
    # The tray's hub teeth through the centre hole.
    for a in range(0, 360, 45):
        t = math.radians(a)
        dd.line([(cx + math.cos(t) * 3) * K, (cy + math.sin(t) * 3) * K,
                 (cx + math.cos(t) * 8) * K, (cy + math.sin(t) * 8) * K], fill=(70, 70, 76, 255), width=K)
    shadow = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse([(cx - R + 2) * K, (cy - R + 4) * K, (cx + R + 2) * K, (cy + R + 4) * K],
                                   fill=(0, 0, 0, 150))
    im = Image.alpha_composite(im, shadow.filter(ImageFilter.GaussianBlur(4 * K)))
    im = Image.alpha_composite(im, disc)
    d = ImageDraw.Draw(im)
    # The sticker well the core's name sits on.
    x, y, w, h = FOOT
    rr(d, (x, y, x + w, y + h), 4, fill=(24, 26, 32, 255), outline=(96, 100, 112, 255), width=K)
    return im


def svg(path, body, w=W, h=H):
    with open(path, "w") as f:
        f.write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">\n{body}</svg>\n')


def main():
    os.makedirs(OUT, exist_ok=True)
    neutral().save(f"{OUT}/jewelcase_neutral.png", optimize=True)
    board(False).save(f"{OUT}/jewelcase_board.png", optimize=True)
    board(True).save(f"{OUT}/jewelcase_board_battery.png", optimize=True)
    # The back half of the case in the shell's colour (#ff00ff plastic,
    # #800080 floor, #400040 deep), as the other moulds' back shells.
    svg(f"{OUT}/jewelcase_shell.svg",
        f'  <rect x="0" y="0" width="{W}" height="{H}" rx="4" fill="#ff00ff"/>\n'
        f'  <rect x="6" y="6" width="{W-12}" height="{H-12}" rx="3" fill="#800080"/>\n'
        f'  <rect x="2" y="3" width="19" height="{H-7}" fill="#400040"/>\n'
        f'  <circle cx="162" cy="140" r="126" fill="none" stroke="#400040" stroke-width="2"/>\n')
    # The core-name sticker, at the footprint's size.
    fw, fh = FOOT[2], FOOT[3]
    svg(f"{OUT}/jewelcase_chip.svg",
        f'  <rect x="0.5" y="0.5" width="{fw-1}" height="{fh-1}" rx="4" fill="#181a20" stroke="#606470" stroke-width="1"/>\n',
        fw, fh)
    print("label", WELL, "footprint", FOOT, "title", TITLE)


if __name__ == "__main__":
    main()
