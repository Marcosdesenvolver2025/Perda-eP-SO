#!/usr/bin/env python3
"""
Gerador da identidade visual do Vendas Itinga.

Desenha a marca (etiqueta de preco + alfinete de localizacao) e exporta todos os
tamanhos exigidos pelo app e pela ficha da Google Play Store.

Uso:
    pip install Pillow
    python3 tools/gerar_marca.py

Saidas:
    assets/marca/            -> arquivos de marca (uso geral / imprensa)
    app/assets/              -> icone, adaptive icon, splash e favicon do app
    loja/graficos/           -> icone 512x512 e capa 1024x500 da Play Store
"""

from __future__ import annotations

import math
import os
from PIL import Image, ImageDraw, ImageFont

# --------------------------------------------------------------------------
# Paleta - o verde #00DF13 substitui o roxo do app de referencia
# --------------------------------------------------------------------------
VERDE = (0, 223, 19)
VERDE_ESCURO = (0, 150, 13)
VERDE_PROFUNDO = (0, 92, 8)
BRANCO = (255, 255, 255)
GRAFITE = (17, 24, 19)

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SS = 4  # fator de supersampling (desenha grande e reduz -> bordas suaves)


def _fonte(tamanho: int, negrito: bool = True) -> ImageFont.FreeTypeFont:
    candidatos = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if negrito
        else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if negrito
        else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for caminho in candidatos:
        if os.path.exists(caminho):
            return ImageFont.truetype(caminho, tamanho)
    return ImageFont.load_default(tamanho)


def _alfinete(d: ImageDraw.ImageDraw, cx: float, cy: float, altura: float,
              cor, cor_miolo) -> None:
    """Alfinete de localizacao: gota (circulo + ponta) com miolo vazado."""
    r = altura * 0.34
    topo = cy - altura * 0.5 + r
    d.ellipse([cx - r, topo - r, cx + r, topo + r], fill=cor)
    # ponta da gota, tangente ao circulo
    d.polygon([(cx - r * 0.86, topo + r * 0.50),
               (cx + r * 0.86, topo + r * 0.50),
               (cx, cy + altura * 0.5)], fill=cor)
    m = r * 0.40
    d.ellipse([cx - m, topo - m, cx + m, topo + m], fill=cor_miolo)


def desenhar_simbolo(img: Image.Image, cx: float, cy: float, tam: float,
                     cor=BRANCO, cor_furo=VERDE) -> None:
    """Etiqueta de preco inclinada com um alfinete de localizacao dentro.

    A etiqueta remete a "vendas" (mesma metafora da aba de vendas do app de
    referencia) e o alfinete remete a "Itinga" - comercio dentro da cidade.
    A etiqueta e desenhada alinhada aos eixos numa camada propria e depois
    rotacionada, para que os cantos arredondados fiquem limpos.
    """
    ang = 32           # inclinacao da etiqueta, em graus (anti-horario)
    lado = tam * 1.55  # a camada precisa de folga para caber a rotacao
    n = int(lado)
    camada = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    dc = ImageDraw.Draw(camada)

    larg = tam * 0.62          # largura do corpo da etiqueta
    alt = tam * 0.98           # altura total da etiqueta (corpo + ponta)
    esq = (n - larg) / 2
    topo = (n - alt) / 2
    dir_ = esq + larg
    ponta_y = topo + alt
    corpo_y = ponta_y - larg * 0.62   # onde o corpo vira ponta
    raio = larg * 0.22

    # corpo: retangulo arredondado
    dc.rounded_rectangle([esq, topo, dir_, corpo_y + raio], radius=raio, fill=cor)
    # ponta inferior da etiqueta
    dc.polygon([(esq, corpo_y - raio * 0.2), (dir_, corpo_y - raio * 0.2),
                ((esq + dir_) / 2, ponta_y)], fill=cor)

    # alfinete de localizacao vazado no corpo da etiqueta
    _alfinete(dc, (esq + dir_) / 2, topo + larg * 0.60, larg * 0.74, cor_furo, cor)

    camada = camada.rotate(ang, resample=Image.BICUBIC, center=(n / 2, n / 2))
    img.alpha_composite(camada, (int(cx - n / 2), int(cy - n / 2)))


def icone(tamanho: int, fundo=VERDE, simbolo=BRANCO, cor_furo=None,
          raio_rel: float = 0.235, transparente: bool = False) -> Image.Image:
    """Icone quadrado com cantos arredondados (padrao dos lancadores Android)."""
    n = tamanho * SS
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if not transparente:
        d.rounded_rectangle([0, 0, n - 1, n - 1], radius=int(n * raio_rel), fill=fundo)
    desenhar_simbolo(img, n / 2, n / 2, n * 0.56, cor=simbolo,
                     cor_furo=cor_furo or (fundo if not transparente else VERDE))
    return img.resize((tamanho, tamanho), Image.LANCZOS)


def icone_adaptativo(tamanho: int = 1024) -> Image.Image:
    """Camada de frente do adaptive icon: simbolo dentro da zona segura (66%)."""
    n = tamanho * SS
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    desenhar_simbolo(img, n / 2, n / 2, n * 0.38, cor=BRANCO, cor_furo=VERDE)
    return img.resize((tamanho, tamanho), Image.LANCZOS)


def _texto_centralizado(d, texto, fonte, cx, cy, cor):
    caixa = d.textbbox((0, 0), texto, font=fonte)
    d.text((cx - (caixa[2] - caixa[0]) / 2 - caixa[0],
            cy - (caixa[3] - caixa[1]) / 2 - caixa[1]), texto, font=fonte, fill=cor)


def logo_horizontal(largura: int = 1600, sobre_verde: bool = False) -> Image.Image:
    """Marca completa: simbolo + 'vendas itinga' em caixa baixa."""
    altura = int(largura * 0.30)
    n_l, n_a = largura * 2, altura * 2
    fundo = VERDE if sobre_verde else (0, 0, 0, 0)
    img = Image.new("RGBA", (n_l, n_a), fundo)
    d = ImageDraw.Draw(img)

    cor_simbolo = BRANCO if sobre_verde else VERDE
    cor_furo = VERDE if sobre_verde else BRANCO
    cor_texto = BRANCO if sobre_verde else GRAFITE

    desenhar_simbolo(img, n_a * 0.50, n_a * 0.50, n_a * 0.62,
                     cor=cor_simbolo, cor_furo=cor_furo)

    x = n_a * 0.98
    disponivel = n_l - x - n_a * 0.12

    # o corpo do texto encolhe ate caber na largura restante
    tamanho = int(n_a * 0.34)
    while tamanho > 8:
        f_grande = _fonte(tamanho, negrito=True)
        if d.textlength("vendas itinga", font=f_grande) <= disponivel:
            break
        tamanho -= 2
    f_pequena = _fonte(max(8, int(tamanho * 0.42)), negrito=False)

    d.text((x, n_a * 0.26), "vendas itinga", font=f_grande, fill=cor_texto)
    d.text((x + 4, n_a * 0.64), "compre e venda na sua cidade",
           font=f_pequena, fill=BRANCO if sobre_verde else VERDE_ESCURO)
    return img.resize((largura, altura), Image.LANCZOS)


def capa_play(largura: int = 1024, altura: int = 500) -> Image.Image:
    """Feature graphic exigido pela ficha da Play Store (1024x500, sem transparencia)."""
    n_l, n_a = largura * 2, altura * 2
    img = Image.new("RGBA", (n_l, n_a), VERDE)
    d = ImageDraw.Draw(img)

    # profundidade: circulos concentricos em verde escuro
    for i, r in enumerate([n_a * 1.05, n_a * 0.78, n_a * 0.52]):
        cor = VERDE_ESCURO if i % 2 == 0 else (0, 190, 16)
        d.ellipse([n_l * 0.78 - r, n_a * 0.5 - r, n_l * 0.78 + r, n_a * 0.5 + r], fill=cor)

    desenhar_simbolo(img, n_l * 0.78, n_a * 0.5, n_a * 0.50, cor=BRANCO, cor_furo=VERDE_ESCURO)

    f_titulo = _fonte(int(n_a * 0.185), negrito=True)
    f_sub = _fonte(int(n_a * 0.085), negrito=False)
    d.text((n_l * 0.07, n_a * 0.28), "vendas", font=f_titulo, fill=BRANCO)
    d.text((n_l * 0.07, n_a * 0.47), "itinga", font=f_titulo, fill=BRANCO)
    d.text((n_l * 0.075, n_a * 0.70), "compre e venda na sua cidade,", font=f_sub, fill=BRANCO)
    d.text((n_l * 0.075, n_a * 0.79), "com entrega local e 4 dias pra testar",
           font=f_sub, fill=BRANCO)
    return img.convert("RGB").resize((largura, altura), Image.LANCZOS)


def splash(largura: int = 1284, altura: int = 2778) -> Image.Image:
    img = Image.new("RGB", (largura, altura), VERDE)
    d = ImageDraw.Draw(img)
    marca = logo_horizontal(int(largura * 0.72), sobre_verde=True)
    img.paste(marca, (int((largura - marca.width) / 2),
                      int((altura - marca.height) / 2)), marca)
    d.text((0, 0), "", fill=BRANCO)
    return img


def salvar(img: Image.Image, caminho_rel: str) -> None:
    destino = os.path.join(RAIZ, caminho_rel)
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    img.save(destino)
    print(f"  {caminho_rel}  ({img.width}x{img.height})")


def main() -> None:
    print("gerando identidade visual do Vendas Itinga (#00DF13)...")

    # --- app -------------------------------------------------------------
    salvar(icone(1024), "app/assets/icon.png")                       # icone principal
    salvar(icone_adaptativo(1024), "app/assets/adaptive-icon.png")   # camada de frente
    salvar(icone(1024, fundo=BRANCO, simbolo=VERDE, cor_furo=BRANCO),
           "app/assets/icon-claro.png")
    salvar(icone(96, transparente=True), "app/assets/notification-icon.png")
    salvar(icone(48), "app/assets/favicon.png")
    salvar(splash(), "app/assets/splash.png")
    salvar(logo_horizontal(1200), "app/assets/logo-horizontal.png")
    salvar(logo_horizontal(1200, sobre_verde=True), "app/assets/logo-horizontal-verde.png")

    # --- Play Store ------------------------------------------------------
    salvar(icone(512), "loja/graficos/icone-512.png")
    salvar(capa_play(), "loja/graficos/capa-1024x500.png")

    # --- marca (uso geral) ----------------------------------------------
    for t in (16, 32, 64, 128, 192, 256, 512, 1024):
        salvar(icone(t), f"assets/marca/icone-{t}.png")
    salvar(logo_horizontal(2400), "assets/marca/logo-horizontal.png")
    salvar(logo_horizontal(2400, sobre_verde=True), "assets/marca/logo-horizontal-verde.png")

    print("pronto.")


if __name__ == "__main__":
    main()
