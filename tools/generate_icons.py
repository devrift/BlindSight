#!/usr/bin/env python3
"""Render the BlindSight PWA icons from the same geometry as static/icon.svg.

The manifest used to declare only an SVG icon. That is not enough: iOS ignores
an SVG `apple-touch-icon`, and Chrome's install criteria want real PNGs, so the
app was not reliably installable — the one thing a PWA for a blind user must be.

Outputs, into edge_server/static/:
    icon-192.png          standard
    icon-512.png          standard
    icon-512-maskable.png full-bleed, content inside the central 80% safe zone

Regenerate with:  python3 tools/generate_icons.py
Requires:         Pillow, numpy
"""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
STATIC = ROOT / "edge_server" / "static"

# Draw large, then downscale: PIL has no anti-aliased stroke primitive.
SS = 4  # supersample factor
BASE = 512
CANVAS = BASE * SS

AMBER = (245, 158, 11)
SKY = (56, 189, 248)
BORDER = (51, 65, 85)
CORE_DARK = (10, 10, 12)
GLOW_INNER = (30, 41, 59)


def radial_background(size: int) -> Image.Image:
    """#1e293b at the centre falling off to #0a0a0c at the edges."""
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    c = size / 2.0
    r = np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / (c * math.sqrt(2))
    r = np.clip(r, 0.0, 1.0)[..., None]
    inner = np.array(GLOW_INNER, dtype=np.float32)
    outer = np.array(CORE_DARK, dtype=np.float32)
    rgb = (inner * (1 - r) + outer * r).astype(np.uint8)
    return Image.fromarray(rgb, "RGB")


def quad_bezier(p0, p1, p2, steps=64):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
        pts.append((x, y))
    return pts


def draw_icon(scale: float = 1.0, maskable: bool = False) -> Image.Image:
    img = radial_background(CANVAS)
    d = ImageDraw.Draw(img, "RGBA")
    s = CANVAS / BASE

    def p(v):
        return v * s

    # Maskable icons get cropped to a circle/squircle by the OS, so all
    # artwork must sit inside the central 80% safe zone and the background must
    # bleed to the edges with no rounded corners of its own.
    if maskable:
        inner = 0.80 * scale
        offset = (1 - inner) / 2 * BASE
    else:
        inner = scale
        offset = 0.0

    def q(x, y):
        return p(offset + x * inner), p(offset + y * inner)

    def line(coords, width, color, opacity=255):
        # joint="curve" avoids notches where the sampled Bezier segments meet
        d.line(
            [q(*c) for c in coords],
            fill=(*color, int(255 * opacity)),
            width=int(p(width * inner)),
            joint="curve",
        )

    def arc(bbox, width, color, opacity=1.0, start=180, end=360):
        box = [q(bbox[0], bbox[1])[0], q(bbox[0], bbox[1])[1], q(bbox[2], bbox[3])[0], q(bbox[2], bbox[3])[1]]
        d.arc(box, start=start, end=end, fill=(*color, int(255 * opacity)), width=int(p(width * inner)))

    if not maskable:
        # Rounded-rect clip to match the SVG's rx=112 card.
        mask = Image.new("L", (CANVAS, CANVAS), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, CANVAS - 1, CANVAS - 1], radius=int(p(112)), fill=255)
        img.putalpha(mask)
        d = ImageDraw.Draw(img, "RGBA")
        d.rounded_rectangle(
            [q(4, 4)[0], q(4, 4)[1], q(508, 508)[0], q(508, 508)[1]],
            radius=int(p(108 * inner)), outline=(*BORDER, 255), width=int(p(6 * inner)),
        )

    # Radar wavefronts (three nested arcs, amber -> sky)
    arc((76, 170, 436, 530), 16, AMBER, opacity=0.40)
    arc((126, 180, 386, 440), 20, AMBER, opacity=0.75)
    arc((176, 190, 336, 350), 24, SKY, opacity=1.0)

    # Central emitter core: amber->sky disc with a dark pupil
    cx, cy = q(256, 240)
    rad = p(38 * inner)
    d.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=(*AMBER, 255))
    pr = p(16 * inner)
    d.ellipse([cx - pr, cy - pr, cx + pr, cy + pr], fill=(*CORE_DARK, 255))

    # Acoustic waves below (quadratic Beziers from the SVG)
    line(quad_bezier((196, 380), (256, 420), (316, 380)), 14, SKY, opacity=0.80)
    line(quad_bezier((166, 410), (256, 460), (346, 410)), 10, SKY, opacity=0.40)

    return img.resize((BASE, BASE), Image.LANCZOS)


def main() -> int:
    STATIC.mkdir(parents=True, exist_ok=True)

    standard = draw_icon(scale=1.0, maskable=False).convert("RGBA")
    standard.save(STATIC / "icon-512.png")
    standard.resize((192, 192), Image.LANCZOS).save(STATIC / "icon-192.png")

    maskable = draw_icon(scale=1.0, maskable=True).convert("RGBA")
    maskable.save(STATIC / "icon-512-maskable.png")

    for name in ("icon-192.png", "icon-512.png", "icon-512-maskable.png"):
        path = STATIC / name
        with Image.open(path) as im:
            print(f"wrote {path.relative_to(ROOT)}  {im.size[0]}x{im.size[1]}  {path.stat().st_size} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
