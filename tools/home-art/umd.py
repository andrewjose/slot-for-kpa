#!/usr/bin/env python3
"""Draws the UMD keep case mould for PSP games:
assets/home/umd_{neutral,board,board_battery}.png, _shell.svg, _chip.svg.

Original art (no photo or product art): a tall clear case with rounded
corners, the hinge on the left and a latch on the right, the game's front
cover in the insert well (a PSP cover's own tall shape, so it is shown whole);
opened, the tray holds the UMD in its caddy, the disc seen through the
caddy's window, the core's name on a sticker below it and the title printed
above it. Logical 312 x 540 (a real case is about 104 x 180 mm); PNGs at 3x.
The neutral front is grey (128 = the shell colour exactly) for the Overlay
blend the other moulds use. Run from the repository root:
    python3 tools/home-art/umd.py
"""
from PIL import Image, ImageDraw, ImageFilter
import math, os

W, H, K = 312, 540, 3
WELL = (22, 22, 280, 496)         # x, y, w, h: the cover insert (a PSP cover is ~0.57 wide)
CADDY = (56, 46, 200, 244)        # the UMD in the tray
FOOT = (81, 236, 150, 34)         # the core-name sticker on the caddy
TITLE = (91.0, 60.0, 130.0, 30.0)  # the title printed on the caddy
WINDOW = (156, 164, 62)           # cx, cy, r of the disc seen through the caddy
OUT = "assets/home"


def rr(draw, box, r, **kw):
    x0, y0, x1, y1 = box
    draw.rounded_rectangle([x0 * K, y0 * K, x1 * K, y1 * K], radius=r * K, **kw)


def neutral():
    im = Image.new("RGBA", (W * K, H * K), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # Case body: grey = the shell colour, rounded like the real case.
    rr(d, (0, 0, W - 1, H - 1), 16, fill=(128, 128, 128, 255))
    rr(d, (0, 0, W - 1, H - 1), 16, outline=(176, 176, 176, 255), width=2 * K)
    rr(d, (3, 3, W - 4, H - 4), 14, outline=(104, 104, 104, 255), width=K)
    # The hinge on the left: a darker spine with ridges.
    d.rectangle([4 * K, 14 * K, 16 * K, (H - 15) * K], fill=(98, 98, 98, 255))
    for x in range(6, 16, 3):
        d.line([x * K, 18 * K, x * K, (H - 19) * K], fill=(86, 86, 86, 255), width=K)
        d.line([(x + 1) * K, 18 * K, (x + 1) * K, (H - 19) * K], fill=(114, 114, 114, 255), width=1)
    d.line([18 * K, 10 * K, 18 * K, (H - 11) * K], fill=(160, 160, 160, 255), width=K)
    # The latch on the right edge, half-way down.
    rr(d, (W - 8, H // 2 - 26, W - 2, H // 2 + 26), 3, fill=(110, 110, 110, 255), outline=(150, 150, 150, 255), width=K)
    # The insert well's rim (the cover covers the well itself).
    x, y, w, h = WELL
    rr(d, (x - 2, y - 2, x + w + 2, y + h + 2), 4, outline=(150, 150, 150, 255), width=K)
    # A soft diagonal gloss across the clear sleeve's frame.
    gloss = Image.new("L", im.size, 0)
    ImageDraw.Draw(gloss).polygon([(0, 0), (130 * K, 0), (30 * K, H * K), (0, H * K)], fill=36)
    gloss = gloss.filter(ImageFilter.GaussianBlur(18 * K))
    alpha = im.split()[3]
    lit = Image.composite(Image.new("RGBA", im.size, (255, 255, 255, 255)), im, gloss)
    lit.putalpha(alpha)
    return lit


def caddy_mask(box):
    """The UMD caddy: rounded top corners, the bottom a wide curve."""
    x, y, w, h = box
    m = Image.new("L", (W * K, H * K), 0)
    d = ImageDraw.Draw(m)
    rr(d, (x, y, x + w, y + h - 30), 14, fill=255)
    d.ellipse([x * K, (y + h - 70) * K, (x + w) * K, (y + h) * K], fill=255)
    return m


def caddy_layer(box, fill, edge=None):
    m = caddy_mask(box)
    layer = Image.new("RGBA", m.size, fill)
    layer.putalpha(m)
    if edge:
        inner = m.filter(ImageFilter.MinFilter(2 * K + 1))
        ring = Image.new("RGBA", m.size, edge)
        ring.putalpha(Image.composite(m, Image.new("L", m.size, 0), Image.eval(inner, lambda v: 255 - v)))
        layer = Image.alpha_composite(layer, ring)
    return layer


def board(battery):
    im = Image.new("RGBA", (W * K, H * K), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # The open tray: black plastic, a raised bed for the UMD, two clips
    # that hold the manual lower down.
    rr(d, (0, 0, W - 1, H - 1), 16, fill=(26, 26, 28, 255))
    rr(d, (6, 6, W - 7, H - 7), 13, fill=(34, 34, 37, 255))
    x, y, w, h = CADDY
    rr(d, (x - 8, y - 8, x + w + 8, y + h + 8), 18, fill=(30, 30, 33, 255), outline=(48, 48, 52, 255), width=2 * K)
    for cx in (96, 216):
        rr(d, (cx - 18, 470, cx + 18, 484), 4, fill=(44, 44, 48, 255))
    for yy in range(320, 460, 12):
        d.line([30 * K, yy * K, (W - 30) * K, yy * K], fill=(38, 38, 42, 255), width=K)
    # The caddy, with a soft shadow under it.
    shadow = caddy_layer((x + 2, y + 4, w, h), (0, 0, 0, 150))
    im = Image.alpha_composite(im, shadow.filter(ImageFilter.GaussianBlur(4 * K)))
    cad = caddy_layer(CADDY, (58, 60, 68, 255), (120, 124, 134, 255))
    cd = ImageDraw.Draw(cad)
    # The window and the disc behind it: silver rim, dark data side, hub.
    wx, wy, wr = WINDOW
    def circle(r, **kw):
        cd.ellipse([(wx - r) * K, (wy - r) * K, (wx + r) * K, (wy + r) * K], **kw)
    circle(wr + 4, fill=(36, 38, 44, 255))
    circle(wr, fill=(176, 180, 190, 255))
    circle(wr - 3, fill=(64, 70, 86, 255))
    for r in range(wr - 7, 20, -5):
        circle(r, outline=(74, 80, 98, 255), width=1)
    circle(18, fill=(160, 164, 172, 255))
    circle(10, fill=(196, 198, 204, 255))
    circle(4, fill=(34, 34, 37, 255))
    # A rainbow-free sheen on the disc, a pale arc on the window.
    cd.arc([(wx - wr + 8) * K, (wy - wr + 8) * K, (wx + wr - 8) * K, (wy + wr - 8) * K],
           200, 250, fill=(140, 146, 164, 255), width=2 * K)
    # The caddy's two shutter slots beside the window.
    for sx in (x + 14, x + w - 22):
        rr(cd, (sx, wy - 30, sx + 8, wy + 30), 3, fill=(46, 48, 54, 255))
    im = Image.alpha_composite(im, cad)
    d = ImageDraw.Draw(im)
    # The sticker well the core's name sits on.
    fx, fy, fw, fh = FOOT
    rr(d, (fx, fy, fx + fw, fy + fh), 4, fill=(24, 26, 32, 255), outline=(96, 100, 112, 255), width=K)
    return im


def svg(path, body, w=W, h=H):
    with open(path, "w") as f:
        f.write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">\n{body}</svg>\n')


def main():
    os.makedirs(OUT, exist_ok=True)
    neutral().save(f"{OUT}/umd_neutral.png", optimize=True)
    board(False).save(f"{OUT}/umd_board.png", optimize=True)
    board(True).save(f"{OUT}/umd_board_battery.png", optimize=True)
    # The back half of the case in the shell's colour (#ff00ff plastic,
    # #800080 floor, #400040 deep), as the other moulds' back shells.
    x, y, w, h = CADDY
    svg(f"{OUT}/umd_shell.svg",
        f'  <rect x="0" y="0" width="{W}" height="{H}" rx="16" fill="#ff00ff"/>\n'
        f'  <rect x="6" y="6" width="{W-12}" height="{H-12}" rx="13" fill="#800080"/>\n'
        f'  <rect x="4" y="14" width="12" height="{H-29}" fill="#400040"/>\n'
        f'  <rect x="{x-8}" y="{y-8}" width="{w+16}" height="{h+16}" rx="18" fill="none" stroke="#400040" stroke-width="2"/>\n')
    fw, fh = FOOT[2], FOOT[3]
    svg(f"{OUT}/umd_chip.svg",
        f'  <rect x="0.5" y="0.5" width="{fw-1}" height="{fh-1}" rx="4" fill="#181a20" stroke="#606470" stroke-width="1"/>\n',
        fw, fh)
    print("label", WELL, "footprint", FOOT, "title", TITLE)


if __name__ == "__main__":
    main()
