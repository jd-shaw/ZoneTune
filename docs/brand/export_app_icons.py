#!/usr/bin/env python3
"""Export ZoneTune app icons from the Steel Resonance mark."""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
RES = ROOT.parent.parent / "app" / "src" / "main" / "res"

STEEL = (47, 111, 126)  # #2F6F7E
WHITE = (255, 255, 255)
WHITE_SOFT = (210, 230, 234)

# Adaptive icon safe zone ≈ center 66/108; keep glyph inside ~72% of canvas
RINGS = [
    # (radius_ratio, stroke_ratio, start_deg, end_deg) — PIL: 0=east, clockwise
    (0.62, 0.038, -35, 215),
    (0.44, 0.044, 25, 275),
    (0.26, 0.050, -20, 200),
]


def draw_glyph(draw: ImageDraw.ImageDraw, cx: float, cy: float, size: float) -> None:
    """White listening-field glyph (no plate) for adaptive / circular masks."""
    r = size / 2

    domain = r * 0.78
    draw.ellipse(
        [cx - domain, cy - domain, cx + domain, cy + domain],
        outline=WHITE_SOFT,
        width=max(1, int(size * 0.012)),
    )

    for rad_ratio, width_ratio, start, end in RINGS:
        rad = r * rad_ratio
        width = max(2, int(size * width_ratio))
        draw.arc(
            [cx - rad, cy - rad, cx + rad, cy + rad],
            start=start,
            end=end,
            fill=WHITE,
            width=width,
        )

    core_r = r * 0.078
    draw.ellipse([cx - core_r, cy - core_r, cx + core_r, cy + core_r], fill=WHITE)

    tick_len = r * 0.055
    tick_w = max(1, int(size * 0.011))
    inset = r * 0.90
    for angle_deg in (0, 90, 180, 270):
        ang = math.radians(angle_deg)
        ox = cx + math.cos(ang) * inset
        oy = cy + math.sin(ang) * inset
        ix = cx + math.cos(ang) * (inset - tick_len)
        iy = cy + math.sin(ang) * (inset - tick_len)
        draw.line([(ix, iy), (ox, oy)], fill=WHITE, width=tick_w)


def draw_full_mark(draw: ImageDraw.ImageDraw, cx: float, cy: float, size: float) -> None:
    """Full mark with steel plate — for marketing / store icon."""
    r = size / 2
    corner = size * 0.22
    draw.rounded_rectangle(
        [cx - r, cy - r, cx + r, cy + r],
        radius=corner,
        fill=STEEL,
    )
    draw_glyph(draw, cx, cy, size)


def export_store_icon(path: Path, size: int = 1024) -> None:
    """Full-bleed opaque icon for stores (no transparency)."""
    img = Image.new("RGB", (size, size), STEEL)
    draw = ImageDraw.Draw(img)
    draw_glyph(draw, size / 2, size / 2, int(size * 0.66))
    img.save(path, "PNG", optimize=True)
    print(f"Wrote {path}")


def export_mark_icon(path: Path, size: int = 1024) -> None:
    """Marketing mark with rounded plate on transparent canvas."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    margin = int(size * 0.06)
    draw_full_mark(draw, size / 2, size / 2, size - 2 * margin)
    img.save(path, "PNG", optimize=True)
    print(f"Wrote {path}")


def export_legacy_mipmap(path: Path, size: int) -> None:
    """Opaque steel + glyph for pre-26 fallback."""
    img = Image.new("RGB", (size, size), STEEL)
    draw = ImageDraw.Draw(img)
    # Glyph sized for safe zone (~66%)
    glyph = int(size * 0.66)
    draw_glyph(draw, size / 2, size / 2, glyph)
    img.save(path, "PNG", optimize=True)
    print(f"Wrote {path}")


def export_adaptive_foreground_png(path: Path, size: int = 432) -> None:
    """Transparent foreground layer (glyph only) for reference / tooling."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw_glyph(draw, size / 2, size / 2, int(size * 0.62))
    img.save(path, "PNG", optimize=True)
    print(f"Wrote {path}")


def pil_point(cx: float, cy: float, r: float, deg: float) -> tuple[float, float]:
    rad = math.radians(deg)
    return cx + r * math.cos(rad), cy + r * math.sin(rad)


def arc_path(cx: float, cy: float, r: float, start: float, end: float) -> str:
    """SVG/Android path for PIL-convention arc (clockwise from start to end)."""
    # Normalize sweep
    sweep = (end - start) % 360
    if sweep <= 0:
        sweep = 360
    large = 1 if sweep > 180 else 0
    x0, y0 = pil_point(cx, cy, r, start)
    x1, y1 = pil_point(cx, cy, r, end)
    # sweep-flag 1 = clockwise in Android/SVG y-down coords
    return (
        f"M{x0:.2f},{y0:.2f} "
        f"A{r:.2f},{r:.2f} 0 {large} 1 {x1:.2f},{y1:.2f}"
    )


def write_foreground_vector(path: Path) -> None:
    """108dp adaptive foreground — glyph only, steel comes from background."""
    cx = cy = 54.0
    # Match PNG glyph: size ≈ 0.62 of 108 ≈ 67
    size = 67.0
    r = size / 2

    domain_r = r * 0.78
    domain = (
        f"M{cx + domain_r:.2f},{cy:.2f} "
        f"A{domain_r:.2f},{domain_r:.2f} 0 1 1 {cx - domain_r:.2f},{cy:.2f} "
        f"A{domain_r:.2f},{domain_r:.2f} 0 1 1 {cx + domain_r:.2f},{cy:.2f} Z"
    )

    paths: list[tuple[str, dict]] = []

    # Soft outer domain as thin stroke
    paths.append(
        (
            domain,
            {
                "strokeColor": "#D2E6EA",
                "strokeWidth": f"{size * 0.012:.2f}",
                "fillColor": "#00000000",
            },
        )
    )

    for rad_ratio, width_ratio, start, end in RINGS:
        rad = r * rad_ratio
        width = size * width_ratio
        paths.append(
            (
                arc_path(cx, cy, rad, start, end),
                {
                    "strokeColor": "#FFFFFF",
                    "strokeWidth": f"{width:.2f}",
                    "strokeLineCap": "round",
                    "fillColor": "#00000000",
                },
            )
        )

    core_r = r * 0.078
    core = (
        f"M{cx - core_r:.2f},{cy:.2f} "
        f"A{core_r:.2f},{core_r:.2f} 0 1 0 {cx + core_r:.2f},{cy:.2f} "
        f"A{core_r:.2f},{core_r:.2f} 0 1 0 {cx - core_r:.2f},{cy:.2f} Z"
    )
    paths.append((core, {"fillColor": "#FFFFFF"}))

    # Cardinal ticks
    tick_len = r * 0.055
    tick_w = size * 0.011
    inset = r * 0.90
    for angle_deg in (0, 90, 180, 270):
        ang = math.radians(angle_deg)
        ox = cx + math.cos(ang) * inset
        oy = cy + math.sin(ang) * inset
        ix = cx + math.cos(ang) * (inset - tick_len)
        iy = cy + math.sin(ang) * (inset - tick_len)
        paths.append(
            (
                f"M{ix:.2f},{iy:.2f} L{ox:.2f},{oy:.2f}",
                {
                    "strokeColor": "#FFFFFF",
                    "strokeWidth": f"{tick_w:.2f}",
                    "strokeLineCap": "round",
                    "fillColor": "#00000000",
                },
            )
        )

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp"',
        '    android:height="108dp"',
        '    android:viewportWidth="108"',
        '    android:viewportHeight="108">',
    ]
    for path_data, attrs in paths:
        lines.append("    <path")
        for k, v in attrs.items():
            lines.append(f'        android:{k}="{v}"')
        lines.append(f'        android:pathData="{path_data}" />')
    lines.append("</vector>")
    lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {path}")


def main() -> None:
    export_store_icon(ROOT / "zonetune-app-icon-1024.png", 1024)
    export_mark_icon(ROOT / "zonetune-mark-1024.png", 1024)
    export_adaptive_foreground_png(ROOT / "zonetune-adaptive-foreground.png", 432)

    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        export_legacy_mipmap(out_dir / "ic_launcher.png", size)
        export_legacy_mipmap(out_dir / "ic_launcher_round.png", size)

    write_foreground_vector(RES / "drawable" / "ic_launcher_foreground.xml")


if __name__ == "__main__":
    main()
