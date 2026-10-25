"""
Gera os assets da identidade visual 4.0 (#30) — o "T" em gradiente
turquesa → violeta dentro do anel luminoso, sobre azul-marinho:

- mipmap-*/ic_launcher.png e ic_launcher_round.png (ícone legado, 5 DPIs)
- mipmap-*/ic_launcher_foreground.png + mipmap-anydpi-v26/*.xml (ícone adaptativo)
- drawable-xhdpi/app_banner.png (320x180, banner da Android TV / topo no celular)
- drawable-xxhdpi/splash_logo.png (ícone da splash do sistema)
- branding/tartatv-logo-4.0.png (1024px, para loja/divulgação)

A geometria é a mesma de `drawTartaMark` em ui/common/BrandLogo.kt
(coordenadas 0–100). Re-rodável: sobrescreve os arquivos a cada execução.

Uso:  python branding/generate-assets.py   (requer Pillow)
"""
import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

NAVY = (11, 18, 28)
NAVY_LIGHT = (24, 36, 50)
TEAL = (52, 209, 182)
SKY = (91, 195, 245)
MID = (127, 162, 240)
VIOLET = (166, 123, 235)
WHITE = (255, 255, 255)

SS = 4  # supersampling

BAR = [(15, 4), (96, 4), (87, 15), (6, 24)]


def stem_points(steps=24):
    pts = [(39, 18), (62, 14), (59, 82)]
    # cúbica (59,82) → (40,86) com controles (58,96) e (41,97)
    p0, p1, p2, p3 = (59, 82), (58, 96), (41, 97), (40, 86)
    for i in range(1, steps + 1):
        t = i / steps
        x = (1 - t) ** 3 * p0[0] + 3 * (1 - t) ** 2 * t * p1[0] + 3 * (1 - t) * t ** 2 * p2[0] + t ** 3 * p3[0]
        y = (1 - t) ** 3 * p0[1] + 3 * (1 - t) ** 2 * t * p1[1] + 3 * (1 - t) * t ** 2 * p2[1] + t ** 3 * p3[1]
        pts.append((x, y))
    return pts


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def gradient(size, stops, horizontal):
    """Imagem RGB com gradiente linear passando pelas cores de `stops`
    (monta uma linha de 1px e estica — rápido mesmo em supersampling)."""
    w, h = size
    length = w if horizontal else h
    n = len(stops) - 1
    line = Image.new("RGB", (length, 1) if horizontal else (1, length))
    px = line.load()
    for i in range(length):
        t = i / max(1, length - 1)
        seg = min(int(t * n), n - 1)
        c = lerp(stops[seg], stops[seg + 1], t * n - seg)
        if horizontal:
            px[i, 0] = c
        else:
            px[0, i] = c
    return line.resize((w, h), Image.NEAREST)


def rounded_mask(size, polygon, side, origin):
    """Máscara do polígono (coords 0–100) com cantos levemente arredondados."""
    w, h = size
    mask = Image.new("L", (w, h), 0)
    k = side / 100
    pts = [(origin[0] + x * k, origin[1] + y * k) for x, y in polygon]
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    r = max(1, int(side * 0.012))
    mask = mask.filter(ImageFilter.GaussianBlur(r)).point(lambda v: 255 if v > 110 else 0)
    return mask


def draw_mark(canvas, side, origin):
    """Desenha a marca num canvas RGBA (já em supersampling)."""
    size = canvas.size
    k = side / 100

    # Travessa: gradiente horizontal turquesa → azul alinhado a ela.
    x0, x1 = int(origin[0] + 6 * k), int(origin[0] + 96 * k)
    bar_grad = Image.new("RGB", size, SKY)
    bar_grad.paste(TEAL, (0, 0, max(0, x0), size[1]))
    bar_grad.paste(gradient((max(1, x1 - x0), size[1]), [TEAL, SKY], horizontal=True), (x0, 0))
    canvas.paste(bar_grad, (0, 0), rounded_mask(size, BAR, side, origin))

    # Haste: gradiente vertical azul → violeta.
    y0, y1 = int(origin[1] + 14 * k), int(origin[1] + 96 * k)
    stem_grad = Image.new("RGB", size, VIOLET)
    stem_grad.paste(SKY, (0, 0, size[0], max(0, y0)))
    stem_grad.paste(gradient((size[0], max(1, y1 - y0)), [SKY, MID, VIOLET], horizontal=False), (0, y0))
    canvas.paste(stem_grad, (0, 0), rounded_mask(size, stem_points(), side, origin))

    # Dobra: sombra suave onde a haste sai da travessa.
    pts = [(origin[0] + x * k, origin[1] + y * k) for x, y in [(39, 18), (62, 14), (61.5, 24), (39.5, 27)]]
    fold_mask = Image.new("L", size, 0)
    ImageDraw.Draw(fold_mask).polygon(pts, fill=46)
    fold_mask = fold_mask.filter(ImageFilter.GaussianBlur(max(1, int(side * 0.02))))
    shadow = Image.new("RGBA", size, (0, 0, 0, 0))
    shadow.putalpha(fold_mask)
    canvas.alpha_composite(shadow)


def draw_ring(canvas, center, radius, width, start_deg=-60, sweep=300):
    """Anel luminoso: trilho tênue + arco em gradiente turquesa→violeta + brilho."""
    size = canvas.size
    glow = Image.new("RGBA", size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    bbox = [center[0] - radius, center[1] - radius, center[0] + radius, center[1] + radius]
    gd.ellipse(bbox, outline=SKY + (60,), width=max(1, int(width * 0.6)))
    steps = 120
    for i in range(steps):
        t = i / steps
        a0 = start_deg + sweep * t
        a1 = start_deg + sweep * (t + 1 / steps) + 0.6
        col = lerp(SKY, VIOLET, t)
        alpha = int(120 + 135 * t)
        gd.arc(bbox, a0, a1, fill=col + (alpha,), width=int(width))
    blur = glow.filter(ImageFilter.GaussianBlur(width * 1.6))
    canvas.alpha_composite(blur)
    canvas.alpha_composite(glow)


def navy_background(size, rounded=False):
    w, h = size
    bg = Image.new("RGBA", size, NAVY + (255,))
    halo = Image.new("L", size, 0)
    ImageDraw.Draw(halo).ellipse([w * 0.1, h * 0.05, w * 0.9, h * 0.85], fill=120)
    halo = halo.filter(ImageFilter.GaussianBlur(min(w, h) * 0.18))
    light = Image.new("RGBA", size, NAVY_LIGHT + (255,))
    bg = Image.composite(light, bg, halo)
    if rounded:
        mask = Image.new("L", size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=int(min(w, h) * 0.22), fill=255)
        bg.putalpha(mask)
    return bg


def icon(px, with_bg=True, rounded=True, ring=True, mark_scale=0.5):
    S = px * SS
    canvas = navy_background((S, S), rounded=rounded) if with_bg else Image.new("RGBA", (S, S), (0, 0, 0, 0))
    if ring:
        draw_ring(canvas, (S / 2, S / 2), S * 0.36, S * 0.026)
    side = S * mark_scale
    draw_mark(canvas, side, ((S - side) / 2, (S - side) / 2 + side * 0.02))
    return canvas.resize((px, px), Image.LANCZOS)


def font(size):
    for path in [
        os.environ.get("BRAND_FONT", ""),
        "C:/Windows/Fonts/segoeuib.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/Library/Fonts/Arial Bold.ttf",
    ]:
        if path and Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


LAUNCHER = {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96, "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}
FOREGROUND = {"mipmap-mdpi": 108, "mipmap-hdpi": 162, "mipmap-xhdpi": 216, "mipmap-xxhdpi": 324, "mipmap-xxxhdpi": 432}


def gen_launcher():
    for folder, px in LAUNCHER.items():
        out = RES / folder
        out.mkdir(parents=True, exist_ok=True)
        icon(px).save(out / "ic_launcher.png", optimize=True)
        # Redondo: mesma arte recortada em círculo.
        round_icon = icon(px, rounded=False)
        mask = Image.new("L", (px, px), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, px - 1, px - 1], fill=255)
        round_icon.putalpha(mask)
        round_icon.save(out / "ic_launcher_round.png", optimize=True)
        print(f"  {folder}/ic_launcher(.png|_round.png) {px}px")


def gen_adaptive():
    # Primeiro plano 108dp com a arte dentro da zona segura de 66dp.
    for folder, px in FOREGROUND.items():
        out = RES / folder
        S = px * SS
        canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        draw_ring(canvas, (S / 2, S / 2), S * 0.25, S * 0.018)
        side = S * 0.34
        draw_mark(canvas, side, ((S - side) / 2, (S - side) / 2 + side * 0.02))
        canvas.resize((px, px), Image.LANCZOS).save(out / "ic_launcher_foreground.png", optimize=True)
    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />\n'
        '</adaptive-icon>\n'
    )
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (anydpi / name).write_text(xml, encoding="utf-8")
    print("  mipmap-*/ic_launcher_foreground.png + mipmap-anydpi-v26")


def gen_banner():
    W, H = 320 * SS, 180 * SS
    canvas = navy_background((W, H))
    ring_r = H * 0.34
    cx, cy = W * 0.27, H * 0.5
    draw_ring(canvas, (cx, cy), ring_r, H * 0.022)
    side = ring_r * 1.25
    draw_mark(canvas, side, (cx - side / 2, cy - side / 2 + side * 0.02))
    d = ImageDraw.Draw(canvas)
    f = font(int(H * 0.2))
    text = "Tarta TV"
    tx = W * 0.5
    d.text((tx, cy), text, font=f, fill=WHITE, anchor="lm")
    banner = canvas.resize((320, 180), Image.LANCZOS).convert("RGB")
    out = RES / "drawable-xhdpi"
    out.mkdir(parents=True, exist_ok=True)
    banner.save(out / "app_banner.png", optimize=True)
    print("  drawable-xhdpi/app_banner.png (320x180)")


def gen_splash():
    # windowSplashScreenAnimatedIcon: 240dp; a arte fica no círculo central (~160dp).
    px = 720  # 240dp @ xxhdpi
    S = px * SS
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw_ring(canvas, (S / 2, S / 2), S * 0.26, S * 0.016)
    side = S * 0.3
    draw_mark(canvas, side, ((S - side) / 2, (S - side) / 2 + side * 0.02))
    out = RES / "drawable-xxhdpi"
    out.mkdir(parents=True, exist_ok=True)
    canvas.resize((px, px), Image.LANCZOS).save(out / "splash_logo.png", optimize=True)
    print("  drawable-xxhdpi/splash_logo.png")


def gen_store():
    px = 1024
    S = px * SS
    canvas = navy_background((S, S))
    cx, cy = S / 2, S * 0.42
    draw_ring(canvas, (cx, cy), S * 0.3, S * 0.02)
    side = S * 0.36
    draw_mark(canvas, side, (cx - side / 2, cy - side / 2 + side * 0.02))
    d = ImageDraw.Draw(canvas)
    d.text((S / 2, S * 0.85), "Tarta TV", font=font(int(S * 0.09)), fill=WHITE, anchor="mm")
    canvas.resize((px, px), Image.LANCZOS).convert("RGB").save(
        ROOT / "branding" / "tartatv-logo-4.0.png", optimize=True)
    print("  branding/tartatv-logo-4.0.png (1024px)")


def main():
    gen_launcher()
    gen_adaptive()
    gen_banner()
    gen_splash()
    gen_store()
    print("done.")


if __name__ == "__main__":
    main()
