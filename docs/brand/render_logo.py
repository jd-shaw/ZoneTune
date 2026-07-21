#!/usr/bin/env python3
"""ZoneTune logo specimen — Steel Resonance (refined)."""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
FONT_DIR = Path("/Users/xiaojindong/.claude/skills/canvas-design/canvas-fonts")
SONGTI = "/System/Library/Fonts/Supplemental/Songti.ttc"
OUT_PNG = ROOT / "zonetune-logo.png"
OUT_PDF = ROOT / "zonetune-logo.pdf"

BG = (243, 244, 246)
SURFACE = (255, 255, 255)
INK = (28, 31, 38)
MUTE = (107, 114, 128)
STEEL = (47, 111, 126)
LINE = (229, 231, 235)
STEEL_PALE = (215, 232, 236)
GRID = (238, 239, 241)

W, H = 2400, 3000


def font(path: str, size: int, index: int | None = None) -> ImageFont.FreeTypeFont:
    if index is None:
        return ImageFont.truetype(path, size)
    return ImageFont.truetype(path, size, index=index)


def text_size(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.ImageFont) -> tuple[float, float]:
    bbox = draw.textbbox((0, 0), text, font=fnt)
    return bbox[2] - bbox[0], bbox[3] - bbox[1]


def draw_mark(
    draw: ImageDraw.ImageDraw,
    cx: float,
    cy: float,
    size: float,
    *,
    filled: bool = True,
) -> None:
    """
    Listening-field mark.
    A soft plate holds nested zone rings — open at staggered angles —
    so the form reads as a mapped acoustic territory, not a signal glyph.
    """
    r = size / 2
    corner = size * 0.22

    if filled:
        draw.rounded_rectangle(
            [cx - r, cy - r, cx + r, cy + r],
            radius=corner,
            fill=STEEL,
        )
        fg = (255, 255, 255)
        fg_soft = (210, 230, 234)
    else:
        draw.rounded_rectangle(
            [cx - r, cy - r, cx + r, cy + r],
            radius=corner,
            outline=STEEL,
            width=max(2, int(size * 0.026)),
        )
        fg = STEEL
        fg_soft = (91, 163, 181)

    # Outer domain circle — the 域 boundary
    domain = r * 0.78
    draw.ellipse(
        [cx - domain, cy - domain, cx + domain, cy + domain],
        outline=fg_soft if filled else STEEL_PALE,
        width=max(1, int(size * 0.012)),
    )

    # Nested open rings — staggered gaps suggest a field under study
    # (patient concentric mapping, not stacked "bars")
    rings = [
        # (radius ratio, stroke ratio, start deg, end deg)
        (0.62, 0.038, -35, 215),
        (0.44, 0.044, 25, 275),
        (0.26, 0.050, -20, 200),
    ]
    for rad_ratio, width_ratio, start, end in rings:
        rad = r * rad_ratio
        width = max(2, int(size * width_ratio))
        draw.arc(
            [cx - rad, cy - rad, cx + rad, cy + rad],
            start=start,
            end=end,
            fill=fg,
            width=width,
        )

    # Origin
    core_r = r * 0.078
    draw.ellipse([cx - core_r, cy - core_r, cx + core_r, cy + core_r], fill=fg)

    # Calibration ticks — cardinal, inset from plate edge
    tick_len = r * 0.055
    tick_w = max(1, int(size * 0.011))
    inset = r * 0.90
    for angle_deg in (0, 90, 180, 270):
        ang = math.radians(angle_deg)
        ox = cx + math.cos(ang) * inset
        oy = cy + math.sin(ang) * inset
        ix = cx + math.cos(ang) * (inset - tick_len)
        iy = cy + math.sin(ang) * (inset - tick_len)
        draw.line([(ix, iy), (ox, oy)], fill=fg, width=tick_w)


def draw_spaced_cn(
    draw: ImageDraw.ImageDraw,
    x: float,
    y: float,
    text: str,
    fnt: ImageFont.ImageFont,
    fill: tuple[int, int, int],
    tracking: float,
) -> float:
    """Draw Chinese with manual tracking; returns total width."""
    cursor = x
    for i, ch in enumerate(text):
        draw.text((cursor, y), ch, font=fnt, fill=fill)
        cw, _ = text_size(draw, ch, fnt)
        cursor += cw + (tracking if i < len(text) - 1 else 0)
    return cursor - x


def main() -> None:
    img = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(img)

    f_cn = font(SONGTI, 88, index=1)  # Songti SC Bold — serif display
    f_en = font(str(FONT_DIR / "InstrumentSans-Regular.ttf"), 38)
    f_label = font(str(FONT_DIR / "DMMono-Regular.ttf"), 20)
    f_label_sm = font(str(FONT_DIR / "DMMono-Regular.ttf"), 15)
    f_meta = font(str(FONT_DIR / "IBMPlexMono-Regular.ttf"), 17)
    f_tiny = font(str(FONT_DIR / "DMMono-Regular.ttf"), 13)

    margin = 160

    # Header — sparse, clinical
    draw.line([(margin, 128), (W - margin, 128)], fill=LINE, width=1)
    draw.text((margin, 82), "STEEL RESONANCE  ·  SPECIMEN 01", font=f_label, fill=MUTE)
    right = "ZONE / FIELD / TUNE"
    rw, _ = text_size(draw, right, f_label)
    draw.text((W - margin - rw, 82), right, font=f_label, fill=MUTE)

    # Quiet construction field (lighter, tighter to plate)
    plate_cx, plate_cy = W / 2, 900
    plate_r = 500
    field = 620
    for x in range(int(plate_cx - field), int(plate_cx + field) + 1, 100):
        draw.line([(x, plate_cy - field), (x, plate_cy + field)], fill=GRID, width=1)
    for y in range(int(plate_cy - field), int(plate_cy + field) + 1, 100):
        draw.line([(plate_cx - field, y), (plate_cx + field, y)], fill=GRID, width=1)

    # Specimen plate
    draw.rounded_rectangle(
        [plate_cx - plate_r, plate_cy - plate_r, plate_cx + plate_r, plate_cy + plate_r],
        radius=44,
        fill=SURFACE,
        outline=LINE,
        width=1,
    )

    # Construction rings — one less, thinner, more breathing room
    for rr in (340, 280):
        draw.ellipse(
            [plate_cx - rr, plate_cy - rr, plate_cx + rr, plate_cy + rr],
            outline=LINE,
            width=1,
        )

    draw_mark(draw, plate_cx, plate_cy, 400, filled=True)

    cap = "LISTENING FIELD  ·  Ø TUNED"
    cw, _ = text_size(draw, cap, f_meta)
    draw.text((plate_cx - cw / 2, plate_cy + plate_r + 40), cap, font=f_meta, fill=MUTE)

    # Wordmark — 听域 leads, ZoneTune whispers
    lock_y = 1720
    tracking = 14
    cn_w = 0
    for i, ch in enumerate("听域"):
        cw, _ = text_size(draw, ch, f_cn)
        cn_w += cw + (tracking if i == 0 else 0)
    en = "ZoneTune"
    en_w, en_h = text_size(draw, en, f_en)
    gap = 32
    total = cn_w + gap + en_w
    x0 = (W - total) / 2

    cn_h = text_size(draw, "听", f_cn)[1]
    draw_spaced_cn(draw, x0, lock_y, "听域", f_cn, INK, tracking)

    # Optical baseline: sit English slightly above Chinese baseline foot
    en_y = lock_y + cn_h - en_h - 10
    draw.text((x0 + cn_w + gap, en_y), en, font=f_en, fill=MUTE)

    rule_y = lock_y + cn_h + 44
    draw.line([(W / 2 - 160, rule_y), (W / 2 + 160, rule_y)], fill=LINE, width=1)
    sub = "STUDIO IDENTITY"
    sw, _ = text_size(draw, sub, f_tiny)
    draw.text((W / 2 - sw / 2, rule_y + 16), sub, font=f_tiny, fill=MUTE)

    # Variants — same mark, three contexts
    row_y = 2040
    box = 200
    spacing = 140
    row_w = 3 * box + 2 * spacing
    row_x0 = (W - row_w) / 2
    labels = ("PRIMARY", "LINE", "INVERT")

    for i, label in enumerate(labels):
        x = row_x0 + i * (box + spacing)
        cx, cy = x + box / 2, row_y + box / 2

        if label == "INVERT":
            draw.rounded_rectangle([x, row_y, x + box, row_y + box], radius=24, fill=INK)
            draw_mark(draw, cx, cy, 128, filled=True)
        elif label == "LINE":
            draw.rounded_rectangle(
                [x, row_y, x + box, row_y + box],
                radius=24,
                fill=SURFACE,
                outline=LINE,
                width=1,
            )
            draw_mark(draw, cx, cy, 128, filled=False)
        else:
            draw.rounded_rectangle(
                [x, row_y, x + box, row_y + box],
                radius=24,
                fill=SURFACE,
                outline=LINE,
                width=1,
            )
            draw_mark(draw, cx, cy, 128, filled=True)

        lw, _ = text_size(draw, label, f_label_sm)
        draw.text((x + (box - lw) / 2, row_y + box + 22), label, font=f_label_sm, fill=MUTE)

    # Palette — aligned, quieter
    pal_y = 2460
    draw.text((margin, pal_y - 36), "PALETTE", font=f_label_sm, fill=MUTE)
    swatches = [
        ("STEEL", STEEL),
        ("INK", INK),
        ("MUTE", MUTE),
        ("PAPER", BG),
        ("SURFACE", SURFACE),
        ("LINE", LINE),
    ]
    sw = 148
    gap_s = 20
    for i, (name, color) in enumerate(swatches):
        x = margin + i * (sw + gap_s)
        outline = LINE if color != LINE else (210, 212, 216)
        draw.rounded_rectangle(
            [x, pal_y, x + sw, pal_y + 64],
            radius=10,
            fill=color,
            outline=outline,
            width=1,
        )
        hx = "#{:02X}{:02X}{:02X}".format(*color)
        draw.text((x, pal_y + 78), name, font=f_tiny, fill=MUTE)
        draw.text((x, pal_y + 96), hx, font=f_tiny, fill=MUTE)

    # Footer
    draw.line([(margin, H - 150), (W - margin, H - 150)], fill=LINE, width=1)
    draw.text((margin, H - 112), "ZONETUNE  ·  听域", font=f_meta, fill=INK)
    foot = "COOL PAPER  ·  GRAPHITE  ·  STEEL-TEAL"
    fw, _ = text_size(draw, foot, f_meta)
    draw.text((W - margin - fw, H - 112), foot, font=f_meta, fill=MUTE)

    img.save(OUT_PNG, "PNG", optimize=True)
    img.convert("RGB").save(OUT_PDF, "PDF", resolution=150.0)
    print(f"Wrote {OUT_PNG}")
    print(f"Wrote {OUT_PDF}")


if __name__ == "__main__":
    main()
