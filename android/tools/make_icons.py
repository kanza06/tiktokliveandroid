"""Buat ikon launcher Android dari 1.png (logo aplikasi Py)."""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT.parent / "1.png"
RES = ROOT / "app" / "src" / "main" / "res"

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def rounded_icon(src: Image.Image, size: int) -> Image.Image:
    img = src.convert("RGBA")
    side = min(img.size)
    left = (img.width - side) // 2
    top = (img.height - side) // 2
    img = img.crop((left, top, left + side, top + side)).resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1],
                                           radius=int(size * 0.22), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def main():
    if not SRC.exists():
        raise SystemExit(f"Logo tidak ditemukan: {SRC}")
    src = Image.open(SRC)
    for density, size in SIZES.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        icon = rounded_icon(src, size)
        icon.save(folder / "ic_launcher.png")
        print("ok", folder / "ic_launcher.png", icon.size)


if __name__ == "__main__":
    main()
