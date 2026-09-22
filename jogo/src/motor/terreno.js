// O chão.
//
// O mundo visto de cima é uma imagem só — asfalto, faixas, meio-fio, grama,
// areia, tudo pintado uma vez em mapa.js. Aqui essa imagem é jogada no chão em
// perspectiva, pixel a pixel, e o carro anda em cima dela.
//
// O truque que faz isso caber em tempo real: a câmera não tem rolagem, então
// TODA linha da tela cruza o plano do chão a uma distância constante. Dá para
// calcular a distância uma vez por linha e depois só caminhar em linha reta
// pela imagem, somando um passo fixo por pixel. É a mesma ideia dos jogos de
// corrida de 16 bits, só que com a câmera livre em vez de presa.
//
// Cada linha carrega também a sua névoa: quanto mais longe, mais a cor some
// no horizonte. Sem isso o chão termina num corte seco e o mundo fica de papel.

import { limitar } from '../nucleo/matematica.js';

export class Terreno {
  constructor() {
    this.tela = document.createElement('canvas');
    this.ctx = this.tela.getContext('2d', { willReadFrequently: false });
    this.imagem = null;
    this.largura = 0;
    this.altura = 0;
  }

  redimensionar(largura, altura) {
    const l = Math.max(2, Math.round(largura));
    const a = Math.max(2, Math.round(altura));
    if (l === this.largura && a === this.altura) return;
    this.largura = l;
    this.altura = a;
    this.tela.width = l;
    this.tela.height = a;
    this.imagem = this.ctx.createImageData(l, a);
  }

  /**
   * @param mapa     { dados, pixels, metros, entorno, entornoLado }
   * @param ambiente { corHorizonte, luz, alcanceNevoa, molhado }
   * @param farois   null, ou { x, z, fx, fz, alcance, abertura, forca }
   */
  desenhar(destino, camera, mapa, ambiente, farois) {
    const bw = this.largura, bh = this.altura;
    if (!bw || !bh) return;

    const dados = this.imagem.data;
    const mapaDados = mapa.dados;
    const mapaLado = mapa.pixels;
    const escala = mapa.pixels / mapa.metros;
    const meioMapa = mapa.pixels / 2;

    const entorno = mapa.entorno;
    const entornoLado = mapa.entornoLado;
    const entornoMascara = entornoLado - 1;
    const entornoDeslocamento = Math.log2(entornoLado) | 0;

    const sg = Math.sin(camera.guinada), cg = Math.cos(camera.guinada);
    const si = Math.sin(camera.inclinacao), ci = Math.cos(camera.inclinacao);
    const tan = camera.tanMeio;
    const tanX = tan * camera.aspecto;
    const camX = camera.x, camY = camera.y, camZ = camera.z;

    const u0 = (1 / bw - 1) * tanX;
    const du = (2 / bw) * tanX;

    const nevoaR = (ambiente.corHorizonte >> 16) & 255;
    const nevoaG = (ambiente.corHorizonte >> 8) & 255;
    const nevoaB = ambiente.corHorizonte & 255;
    const alcance = ambiente.alcanceNevoa || 120;
    const luz = ambiente.luz === undefined ? 1 : ambiente.luz;
    const molhado = ambiente.molhado ? 0.72 : 1;

    const temFarois = !!farois;
    let flX = 0, flZ = 0, ffX = 0, ffZ = 0, alcanceFarol = 0, aberturaFarol = 0, forcaFarol = 0;
    if (temFarois) {
      flX = farois.x; flZ = farois.z;
      ffX = farois.fx; ffZ = farois.fz;
      alcanceFarol = farois.alcance;
      aberturaFarol = farois.abertura;
      forcaFarol = farois.forca;
    }

    for (let py = 0; py < bh; py++) {
      const linha = py * bw * 4;
      const ndcY = 1 - ((py + 0.5) / bh) * 2;
      const v = ndcY * tan;
      const raioY = v * ci + si;

      // Acima do horizonte não há chão: deixa transparente para o céu aparecer.
      if (raioY > -0.0009) {
        dados.fill(0, linha, linha + bw * 4);
        continue;
      }

      const t = camY / -raioY;
      if (t > alcance * 5.5) {
        dados.fill(0, linha, linha + bw * 4);
        continue;
      }

      const k = v * si - ci;
      let wx = camX + t * (u0 * cg + sg * k);
      let wz = camZ + t * (-u0 * sg + cg * k);
      const passoX = t * du * cg;
      const passoZ = -t * du * sg;

      // Névoa: constante na linha inteira, porque a distância é constante.
      let nevoa = 1 - Math.exp(-t / alcance);
      nevoa = nevoa * nevoa * (3 - 2 * nevoa);
      const clareza = (1 - nevoa) * luz * molhado;
      const fogR = nevoa * nevoaR, fogG = nevoa * nevoaG, fogB = nevoa * nevoaB;

      let p = linha;
      for (let px = 0; px < bw; px++, wx += passoX, wz += passoZ) {
        const mx = (wx * escala + meioMapa) | 0;
        const mz = (wz * escala + meioMapa) | 0;

        let r, g, b;
        if (mx >= 0 && mx < mapaLado && mz >= 0 && mz < mapaLado) {
          const i = (mz * mapaLado + mx) << 2;
          r = mapaDados[i]; g = mapaDados[i + 1]; b = mapaDados[i + 2];
        } else {
          // Fora da área da missão o terreno continua, repetindo um pedaço de
          // mato. É o que impede o mundo de terminar num buraco.
          const ex = ((wx * 2.2) | 0) & entornoMascara;
          const ez = ((wz * 2.2) | 0) & entornoMascara;
          const i = ((ez << entornoDeslocamento) + ex) << 2;
          r = entorno[i]; g = entorno[i + 1]; b = entorno[i + 2];
        }

        let ganho = clareza;
        if (temFarois) {
          const dx = wx - flX, dz = wz - flZ;
          const adiante = dx * ffX + dz * ffZ;
          if (adiante > 0 && adiante < alcanceFarol) {
            const lado = Math.abs(-dx * ffZ + dz * ffX);
            const meia = 1.1 + adiante * aberturaFarol;
            if (lado < meia) {
              const queda = 1 - adiante / alcanceFarol;
              const borda = 1 - lado / meia;
              ganho += forcaFarol * queda * queda * borda * (1 - nevoa);
            }
          }
        }

        dados[p] = r * ganho + fogR;
        dados[p + 1] = g * ganho + fogG;
        dados[p + 2] = b * ganho + fogB;
        dados[p + 3] = 255;
        p += 4;
      }
    }

    this.ctx.putImageData(this.imagem, 0, 0);
    destino.imageSmoothingEnabled = true;
    destino.drawImage(this.tela, 0, 0, destino.canvas.width, destino.canvas.height);
  }
}

/**
 * Decide em quantos pixels o chão é desenhado. Menos pixels = mais quadros por
 * segundo; o resultado é esticado para a tela inteira e a diferença quase não
 * aparece, porque o chão é tudo textura e névoa.
 */
export function resolucaoDoChao(largura, altura, qualidade) {
  const fator = qualidade === 'alta' ? 0.72 : qualidade === 'media' ? 0.5 : 0.34;
  const l = limitar(Math.round(largura * fator), 160, 1280);
  return { largura: l, altura: Math.max(90, Math.round(l * (altura / largura))) };
}
