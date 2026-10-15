"""Rasterize icon.svg into Android launcher assets.

Requires librsvg's rsvg-convert and Pillow. Run from the repository root:
    python3 tools/generate_launcher_icons.py
"""

from io import BytesIO
from pathlib import Path
import subprocess

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def source_image() -> Image.Image:
    rendered = subprocess.run(
        ["rsvg-convert", "-w", "1024", str(ROOT / "icon.svg")],
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    return Image.open(BytesIO(rendered)).convert("RGBA")


def fitted(image: Image.Image, width: int) -> Image.Image:
    height = round(width * image.height / image.width)
    return image.resize((width, height), Image.Resampling.LANCZOS)


def main() -> None:
    artwork = source_image()
    for density, size in SIZES.items():
        target = RES / f"mipmap-{density}"
        target.mkdir(parents=True, exist_ok=True)

        legacy = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        ImageDraw.Draw(legacy).rounded_rectangle(
            (0, 0, size - 1, size - 1),
            radius=round(size * 0.22),
            fill="white",
        )
        logo = fitted(artwork, round(size * 0.82))
        legacy.alpha_composite(logo, ((size - logo.width) // 2, (size - logo.height) // 2))
        legacy.save(target / "ic_launcher.png", optimize=True)

        adaptive_size = round(size * 108 / 48)
        foreground = Image.new("RGBA", (adaptive_size, adaptive_size), (0, 0, 0, 0))
        logo = fitted(artwork, round(adaptive_size * 0.60))
        foreground.alpha_composite(
            logo,
            ((adaptive_size - logo.width) // 2, (adaptive_size - logo.height) // 2),
        )
        foreground.save(target / "ic_launcher_foreground.png", optimize=True)


if __name__ == "__main__":
    main()
