"""
Gera os assets do app a partir de branding/tartatv-logo-source.png:
- mipmap-*/ic_launcher.png nos 5 buckets de DPI
- drawable-xhdpi/app_banner.png (320x180 para Android TV)

Re-rodável: sobrescreve os arquivos a cada execução.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "branding" / "tartatv-logo-source.png"
RES = ROOT / "app" / "src" / "main" / "res"

LAUNCHER_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def gen_launcher(src: Image.Image) -> None:
    for folder, px in LAUNCHER_SIZES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        img = src.resize((px, px), Image.LANCZOS)
        img.save(out_dir / "ic_launcher.png", "PNG", optimize=True)
        # Round icon usa a mesma arte; o Android cuida do mask circular.
        img.save(out_dir / "ic_launcher_round.png", "PNG", optimize=True)
        print(f"  {folder}/ic_launcher.png ({px}x{px})")

def gen_banner(src: Image.Image) -> None:
    # TV banner: 320x180. Letterbox horizontal — colocamos a arte quadrada
    # centralizada e preenchemos as laterais com a cor de fundo do logo
    # (cantos superior-esquerdo são uma boa amostra do background).
    bg = src.getpixel((4, 4))
    if len(bg) == 4:
        bg = bg[:3]
    banner = Image.new("RGB", (320, 180), bg)
    # Logo ocupa 160x160 centralizado.
    logo = src.resize((160, 160), Image.LANCZOS).convert("RGB")
    banner.paste(logo, ((320 - 160) // 2, (180 - 160) // 2))
    out_dir = RES / "drawable-xhdpi"
    out_dir.mkdir(parents=True, exist_ok=True)
    banner.save(out_dir / "app_banner.png", "PNG", optimize=True)
    print(f"  drawable-xhdpi/app_banner.png (320x180)")

def main() -> None:
    src = Image.open(SOURCE).convert("RGBA")
    print(f"source: {SOURCE} ({src.size[0]}x{src.size[1]})")
    gen_launcher(src)
    gen_banner(src)
    print("done.")

if __name__ == "__main__":
    main()
