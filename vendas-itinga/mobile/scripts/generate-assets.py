#!/usr/bin/env python3
"""
Gera os assets visuais do Vendas Itinga (icone, splash, adaptive icon,
favicon, icone de notificacao e feature graphic da Play Store).

Uso:  python3 scripts/generate-assets.py
Saida: ../assets/*.png  e  ../store/*.png

Paleta: branco (#FFFFFF) e verde (#2BCC3E).
"""

import os
from PIL import Image, ImageDraw, ImageFont

GREEN = (43, 204, 62)
GREEN_DARK = (28, 168, 45)
WHITE = (255, 255, 255)
INK = (26, 32, 28)

BASE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(BASE, "..", "assets")
STORE = os.path.join(BASE, "..", "store")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def draw_bag(draw, cx, cy, size, color, stroke_ratio=0.085):
    """Desenha a sacola de compras (marca do Vendas Itinga)."""
    w = size
    h = size * 1.02
    left = cx - w / 2
    top = cy - h / 2 + h * 0.16
    right = cx + w / 2
    bottom = cy + h / 2

    # A alca vem primeiro para que o corpo a cubra na base - assim ela
    # parece sair de dentro da sacola, sem sobreposicao visivel.
    stroke = max(2, int(w * stroke_ratio * 1.6))
    handle_w = w * 0.46
    handle_top = cy - h / 2 - h * 0.02
    draw.arc(
        [cx - handle_w / 2, handle_top, cx + handle_w / 2, top + h * 0.22],
        start=180,
        end=360,
        fill=color,
        width=stroke,
    )

    radius = w * 0.16
    draw.rounded_rectangle([left, top, right, bottom], radius=radius, fill=color)


def draw_bag_smile(draw, cx, cy, size, bag_color, cut_color):
    """Sacola com um 'sorriso' vazado - identidade leve, estilo marketplace."""
    draw_bag(draw, cx, cy, size, bag_color)
    # Sorriso vazado no corpo da sacola.
    sw = size * 0.44
    sy = cy + size * 0.16
    draw.arc(
        [cx - sw / 2, sy - sw / 2, cx + sw / 2, sy + sw / 2],
        start=20,
        end=160,
        fill=cut_color,
        width=max(2, int(size * 0.075)),
    )


def make_icon(path, px=1024):
    img = Image.new("RGB", (px, px), GREEN)
    d = ImageDraw.Draw(img)
    draw_bag_smile(d, px / 2, px / 2 - px * 0.02, px * 0.46, WHITE, GREEN)
    img.save(path)
    print("ok", path)


def make_adaptive_icon(path, px=1024):
    # Foreground transparente: o Android aplica a mascara. Mantemos a marca
    # dentro da safe zone (66% central).
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_bag_smile(d, px / 2, px / 2, px * 0.38, WHITE + (255,), (0, 0, 0, 0))
    img.save(path)
    print("ok", path)


def make_monochrome_icon(path, px=1024):
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_bag(d, px / 2, px / 2, px * 0.38, (0, 0, 0, 255))
    img.save(path)
    print("ok", path)


def make_notification_icon(path, px=96):
    # Android exige icone de notificacao branco com fundo transparente.
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_bag(d, px / 2, px / 2, px * 0.62, WHITE + (255,))
    img.save(path)
    print("ok", path)


def make_splash(path, w=1284, h=2778):
    img = Image.new("RGB", (w, h), WHITE)
    d = ImageDraw.Draw(img)
    cx, cy = w / 2, h / 2 - h * 0.04
    draw_bag_smile(d, cx, cy, w * 0.28, GREEN, WHITE)

    f_title = font(FONT_BOLD, int(w * 0.088))
    f_sub = font(FONT_REG, int(w * 0.037))

    title = "vendas itinga"
    tb = d.textbbox((0, 0), title, font=f_title)
    d.text((cx - (tb[2] - tb[0]) / 2, cy + w * 0.22), title, font=f_title, fill=INK)

    sub = "o brechó da nossa cidade"
    sb = d.textbbox((0, 0), sub, font=f_sub)
    d.text((cx - (sb[2] - sb[0]) / 2, cy + w * 0.34), sub, font=f_sub, fill=(120, 130, 122))

    img.save(path)
    print("ok", path)


def make_favicon(path, px=196):
    img = Image.new("RGB", (px, px), GREEN)
    d = ImageDraw.Draw(img)
    draw_bag_smile(d, px / 2, px / 2, px * 0.5, WHITE, GREEN)
    img.save(path)
    print("ok", path)


def make_feature_graphic(path, w=1024, h=500):
    """Feature graphic exigido pela ficha da Play Store."""
    img = Image.new("RGB", (w, h), GREEN)
    d = ImageDraw.Draw(img)

    # Faixa branca diagonal decorativa.
    d.polygon([(w * 0.58, 0), (w, 0), (w, h), (w * 0.72, h)], fill=GREEN_DARK)

    draw_bag_smile(d, w * 0.19, h * 0.5, h * 0.46, WHITE, GREEN)

    f_title = font(FONT_BOLD, 78)
    f_sub = font(FONT_REG, 34)
    d.text((w * 0.32, h * 0.30), "vendas itinga", font=f_title, fill=WHITE)
    d.text((w * 0.325, h * 0.52), "compre e venda pertinho de você", font=f_sub, fill=WHITE)

    img.save(path)
    print("ok", path)


if __name__ == "__main__":
    os.makedirs(ASSETS, exist_ok=True)
    os.makedirs(STORE, exist_ok=True)

    make_icon(os.path.join(ASSETS, "icon.png"))
    make_adaptive_icon(os.path.join(ASSETS, "adaptive-icon.png"))
    make_monochrome_icon(os.path.join(ASSETS, "monochrome-icon.png"))
    make_notification_icon(os.path.join(ASSETS, "notification-icon.png"))
    make_splash(os.path.join(ASSETS, "splash.png"))
    make_favicon(os.path.join(ASSETS, "favicon.png"))
    make_feature_graphic(os.path.join(STORE, "feature-graphic.png"))
