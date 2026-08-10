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


# --------------------------------------------------------------------------
# Proporcoes da sacola, medidas no icone enviado como referencia.
# Todas relativas ao LADO do quadrado do icone (0 a 1).
# --------------------------------------------------------------------------
CORPO_ESQ, CORPO_DIR = 0.270, 0.730     # laterais da sacola
CORPO_TOPO, CORPO_BASE = 0.328, 0.715   # boca e fundo da sacola
CORPO_RAIO = 0.070                      # arredondamento dos cantos
ALCA_CY = 0.340                         # centro do meio-anel da alca
ALCA_RAIO_EXT, ALCA_RAIO_INT = 0.104, 0.042
SORRISO_CY, SORRISO_RAIO = 0.5175, 0.115
SORRISO_ESPESSURA = 0.033
# extremos verticais do desenho, para centralizar a marca
MARCA_TOPO = ALCA_CY - ALCA_RAIO_EXT    # 0.236
MARCA_BASE = CORPO_BASE                 # 0.715


def _mascara_sacola(lado: int) -> Image.Image:
    """Mascara da sacola sorridente (255 = tinta) num quadrado de lado `lado`.

    A ordem importa: a alca e desenhada primeiro como um meio-anel e o corpo
    entra por cima, tapando a parte do vazado que cairia dentro da sacola -
    e assim que o icone de referencia se comporta.
    """
    L = lado
    m = Image.new("L", (L, L), 0)
    d = ImageDraw.Draw(m)
    cx = L / 2

    # alca: meio-anel acima da boca da sacola
    cy_a = ALCA_CY * L
    re, ri = ALCA_RAIO_EXT * L, ALCA_RAIO_INT * L
    d.pieslice([cx - re, cy_a - re, cx + re, cy_a + re], 180, 360, fill=255)
    d.pieslice([cx - ri, cy_a - ri, cx + ri, cy_a + ri], 180, 360, fill=0)

    # corpo da sacola
    d.rounded_rectangle(
        [CORPO_ESQ * L, CORPO_TOPO * L, CORPO_DIR * L, CORPO_BASE * L],
        radius=CORPO_RAIO * L,
        fill=255,
    )

    # sorriso vazado: arco com as pontas arredondadas
    cy_s, rs = SORRISO_CY * L, SORRISO_RAIO * L
    esp = max(2, int(round(SORRISO_ESPESSURA * L)))
    d.arc([cx - rs, cy_s - rs, cx + rs, cy_s + rs], 30, 150, fill=0, width=esp)
    raio_ponta = rs - esp / 2  # o arco do PIL cresce para dentro do raio
    for angulo in (30, 150):
        px = cx + raio_ponta * math.cos(math.radians(angulo))
        py = cy_s + raio_ponta * math.sin(math.radians(angulo))
        d.ellipse([px - esp / 2, py - esp / 2, px + esp / 2, py + esp / 2], fill=0)

    return m


def camada_sacola(lado: int, cor=BRANCO) -> Image.Image:
    """A sacola pintada em `cor`, com alca e sorriso VAZADOS (transparentes).

    Deixar os vazados transparentes faz a mesma arte servir sobre o verde
    (o fundo aparece no sorriso) e sobre o branco (idem), sem redesenhar.
    """
    L = int(lado)
    camada = Image.new("RGBA", (L, L), (0, 0, 0, 0))
    camada.paste(Image.new("RGBA", (L, L), (*cor, 255)), (0, 0), _mascara_sacola(L))
    return camada


def desenhar_simbolo(img: Image.Image, cx: float, cy: float, tam: float,
                     cor=BRANCO) -> None:
    """Desenha a sacola centrada em (cx, cy), ocupando um quadrado de lado `tam`."""
    L = int(tam)
    camada = camada_sacola(L, cor)
    # o desenho nao ocupa o quadrado inteiro: desloca para o centro otico
    desloc = (0.5 - (MARCA_TOPO + MARCA_BASE) / 2) * L
    img.alpha_composite(camada, (int(cx - L / 2), int(cy - L / 2 + desloc)))


def icone(tamanho: int, fundo=VERDE, simbolo=BRANCO, cor_furo=None,
          raio_rel: float = 0.235, transparente: bool = False) -> Image.Image:
    """Icone quadrado com cantos arredondados (padrao dos lancadores Android)."""
    n = tamanho * SS
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if not transparente:
        d.rounded_rectangle([0, 0, n - 1, n - 1], radius=int(n * raio_rel), fill=fundo)
    # as proporcoes da sacola sao relativas ao lado do icone: usar o quadrado
    # inteiro reproduz o enquadramento da referencia
    img.alpha_composite(camada_sacola(n, simbolo))
    return img.resize((tamanho, tamanho), Image.LANCZOS)


def icone_adaptativo(tamanho: int = 1024) -> Image.Image:
    """Camada de frente do adaptive icon: simbolo dentro da zona segura (66%)."""
    n = tamanho * SS
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    # o lancador mostra so os 66% centrais: encolhendo a arte na mesma
    # proporcao, o icone aparece do mesmo tamanho da referencia
    desenhar_simbolo(img, n / 2, n / 2, n * 0.667, cor=BRANCO)
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
    cor_texto = BRANCO if sobre_verde else GRAFITE

    desenhar_simbolo(img, n_a * 0.52, n_a * 0.50, n_a * 0.92, cor=cor_simbolo)

    x = n_a * 1.02
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

    desenhar_simbolo(img, n_l * 0.78, n_a * 0.5, n_a * 0.78, cor=BRANCO)

    f_titulo = _fonte(int(n_a * 0.185), negrito=True)
    f_sub = _fonte(int(n_a * 0.085), negrito=False)
    d.text((n_l * 0.07, n_a * 0.28), "vendas", font=f_titulo, fill=BRANCO)
    d.text((n_l * 0.07, n_a * 0.47), "itinga", font=f_titulo, fill=BRANCO)
    d.text((n_l * 0.075, n_a * 0.70), "compre e venda na sua cidade,", font=f_sub, fill=BRANCO)
    d.text((n_l * 0.075, n_a * 0.79), "com entrega local e 7 dias pra testar",
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
