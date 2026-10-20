#!/usr/bin/env python3
"""Generates crisp multi-density launcher icons for Feeding Frenzy Native Port (Andy the Angelfish)."""

from pathlib import Path
from PIL import Image, ImageDraw

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def render_icon(size: int) -> Image.Image:
    ss = size * 4  # 4x supersampled anti-aliasing
    img = Image.new("RGBA", (ss, ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    pad = int(ss * 0.04)
    radius = int(ss * 0.22)
    # Deep reef gradient layers inside rounded icon
    d.rounded_rectangle([pad, pad, ss - pad, ss - pad], radius=radius, fill=(7, 52, 99, 255))
    d.ellipse([int(ss * 0.08), -int(ss * 0.2), int(ss * 0.92), int(ss * 0.65)], fill=(18, 128, 196, 175))
    d.ellipse([int(ss * 0.20), -int(ss * 0.1), int(ss * 0.80), int(ss * 0.44)], fill=(72, 206, 247, 130))

    # Tail fin (orange-gold classic Andy Angelfish)
    tail = [
        (int(ss * 0.28), int(ss * 0.52)),
        (int(ss * 0.11), int(ss * 0.34)),
        (int(ss * 0.16), int(ss * 0.52)),
        (int(ss * 0.11), int(ss * 0.70)),
    ]
    d.polygon(tail, fill=(255, 132, 24, 255))

    # Dorsal & ventral angelfish fins
    d.polygon(
        [(int(ss * 0.36), int(ss * 0.34)), (int(ss * 0.45), int(ss * 0.15)), (int(ss * 0.66), int(ss * 0.35))],
        fill=(255, 190, 28, 255),
    )
    d.polygon(
        [(int(ss * 0.38), int(ss * 0.68)), (int(ss * 0.46), int(ss * 0.86)), (int(ss * 0.65), int(ss * 0.67))],
        fill=(255, 162, 24, 255),
    )

    # Body (vibrant gold-orange with classic royal-blue angelfish stripes)
    d.ellipse([int(ss * 0.25), int(ss * 0.30), int(ss * 0.82), int(ss * 0.74)], fill=(255, 176, 24, 255))
    d.arc(
        [int(ss * 0.34), int(ss * 0.31), int(ss * 0.56), int(ss * 0.73)],
        start=110,
        end=250,
        fill=(26, 86, 219, 255),
        width=max(4, ss // 26),
    )
    d.arc(
        [int(ss * 0.46), int(ss * 0.32), int(ss * 0.68), int(ss * 0.72)],
        start=110,
        end=250,
        fill=(26, 86, 219, 255),
        width=max(4, ss // 28),
    )

    # Expressive PopCap cartoon eye + smile + bubbles
    d.ellipse([int(ss * 0.61), int(ss * 0.37), int(ss * 0.76), int(ss * 0.52)], fill=(255, 255, 255, 255))
    d.ellipse([int(ss * 0.66), int(ss * 0.40), int(ss * 0.74), int(ss * 0.49)], fill=(12, 24, 48, 255))
    d.ellipse([int(ss * 0.69), int(ss * 0.41), int(ss * 0.72), int(ss * 0.44)], fill=(255, 255, 255, 255))
    d.arc(
        [int(ss * 0.66), int(ss * 0.52), int(ss * 0.83), int(ss * 0.64)],
        start=15,
        end=155,
        fill=(145, 52, 8, 255),
        width=max(3, ss // 34),
    )

    # Bubbles
    for bx, by, br in [(0.86, 0.34, 0.045), (0.89, 0.21, 0.030), (0.79, 0.25, 0.025)]:
        cx, cy, rr = int(ss * bx), int(ss * by), int(ss * br)
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(190, 244, 255, 195), outline=(255, 255, 255, 235), width=max(2, ss // 90))

    return img.resize((size, size), Image.Resampling.LANCZOS)


def main() -> None:
    res_dir = Path("ports/feeding-frenzy/app/src/main/res")
    for folder, px in MIPMAP_SIZES.items():
        target = res_dir / folder / "ic_launcher.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        render_icon(px).save(target, "PNG")
        print(f"Generated {target} ({px}x{px})")


if __name__ == "__main__":
    main()
