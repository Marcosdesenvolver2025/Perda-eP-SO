// A imagem do chão.
//
// O bairro inteiro é pintado UMA vez, de cima, numa imagem quadrada: terra,
// asfalto, calçada, faixa, vaga, mancha de óleo, remendo, bueiro. Depois disso
// o terreno.js só lê essa imagem em perspectiva, quadro a quadro.
//
// É por isso que dá para ter faixa de pedestre e vaga pintada num jogo que
// desenha o chão pixel a pixel: o trabalho caro já foi feito antes de começar.

import { TAU, corTexto, criarSorteio, entre, limitar, tonalizar } from '../nucleo/matematica.js';
import { VIA } from './paleta.js';

const LADO_ENTORNO = 256;

export function gerarMapa(mundo, ambiente, qualidade = 'alta') {
  const pixels = qualidade === 'alta' ? 1600 : qualidade === 'media' ? 1200 : 900;
  const metros = mundo.metros;
  const escala = pixels / metros;

  const tela = document.createElement('canvas');
  tela.width = pixels;
  tela.height = pixels;
  const ctx = tela.getContext('2d', { willReadFrequently: true });
  const sortear = criarSorteio(mundo.semente ^ 0x2c1b);
  const f = mundo.ficha;

  // 1. Terreno de base, com granulado.
  ctx.fillStyle = corTexto(f.base);
  ctx.fillRect(0, 0, pixels, pixels);
  aplicarGranulado(ctx, pixels, f.base, mundo.semente, 0.09);
  manchasLargas(ctx, pixels, f.base, sortear);

  // A partir daqui desenhamos em METROS.
  ctx.save();
  ctx.setTransform(escala, 0, 0, escala, pixels / 2, pixels / 2);

  // 2. Calçada por baixo, asfalto por cima: a calçada vira a moldura da rua.
  for (const via of mundo.vias) faixaDaVia(ctx, via, mundo.calcada, f.corCalcada, true);
  if (mundo.pista) tracoDaPista(ctx, mundo.pista, mundo.pista.largura + mundo.calcada * 2, f.corCalcada);
  for (const anel of mundo.aneis) faixaCircular(ctx, anel, mundo.calcada, f.corCalcada, true);
  for (const r of mundo.rotatorias) faixaCircular(ctx, r, mundo.calcada, f.corCalcada, true);
  for (const via of mundo.vias) faixaDaVia(ctx, via, 0, f.corAsfalto, false);

  if (mundo.patio) {
    const q = mundo.patio;
    ctx.fillStyle = corTexto(f.corCalcada);
    ctx.fillRect(q.x0 - 1.2, q.z0 - 1.2, q.largura + 2.4, q.profundidade + 2.4);
    ctx.fillStyle = corTexto(tonalizar(f.corAsfalto, 1.08));
    ctx.fillRect(q.x0, q.z0, q.largura, q.profundidade);
  }

  // 3. Desgaste do asfalto — remendo, trinca, óleo, bueiro.
  desgaste(ctx, mundo, sortear);

  // 4. Pintura de trânsito.
  if (!f.terra) for (const via of mundo.vias) pintarVia(ctx, via, mundo, sortear);
  faixasDePedestre(ctx, mundo, sortear);

  // 4b. O anel e a rotatória vêm DEPOIS da rua, e por cima dela. É o que
  //     resolve a emenda: a faixa da rua morre na borda da pista circular em
  //     vez de atravessá-la, e a rotatória não fica com risco de rua no meio.
  //     A ordem aqui é a mesma da hierarquia viária de verdade.
  if (mundo.pista) pintarPista(ctx, mundo.pista, f, sortear);
  for (const anel of mundo.aneis) faixaCircular(ctx, anel, 0, f.corAsfalto, false);
  for (const r of mundo.rotatorias) faixaCircular(ctx, r, 0, f.corAsfalto, false);
  desgastarCirculos(ctx, mundo, sortear);
  if (!f.terra) {
    for (const anel of mundo.aneis) pintarAnel(ctx, anel);
    for (const r of mundo.rotatorias) pintarRotatoria(ctx, r, mundo);
  }
  for (const r of mundo.rotatorias) ilhaDaRotatoria(ctx, r, f, sortear);
  if (mundo.pista) pintarLargadaNaPista(ctx, mundo.pista);
  if (mundo.largada && mundo.aneis.length) pintarLargada(ctx, mundo.aneis[0], mundo.largada.angulo);

  // 5. Vagas. A vaga da missão é pintada de verde e com o miolo tingido: é
  //    ela que a pessoa tem que achar do outro lado do quarteirão.
  for (const vaga of mundo.vagas) {
    if (vaga.alvo) destacarVaga(ctx, vaga);
    else pintarVaga(ctx, vaga, 0xf0ede4);
  }

  // 6. Sombra dos prédios, na direção do sol. É o que assenta a cidade no chão.
  sombrasNoChao(ctx, mundo, ambiente);

  ctx.restore();

  if (ambiente.molhado) molhar(ctx, pixels, sortear);

  const dados = ctx.getImageData(0, 0, pixels, pixels).data;
  return {
    dados,
    pixels,
    metros,
    tela,
    entorno: gerarEntorno(f.base, mundo.semente),
    entornoLado: LADO_ENTORNO,
  };
}

function faixaDaVia(ctx, via, folga, cor, comMeioFio) {
  const largura = via.largura + folga * 2;
  ctx.fillStyle = corTexto(cor);
  if (via.eixo === 'x') {
    ctx.fillRect(via.de, via.centro - largura / 2, via.ate - via.de, largura);
    if (comMeioFio) {
      ctx.fillStyle = corTexto(VIA.meioFio);
      ctx.fillRect(via.de, via.centro - largura / 2, via.ate - via.de, 0.18);
      ctx.fillRect(via.de, via.centro + largura / 2 - 0.18, via.ate - via.de, 0.18);
    }
  } else {
    ctx.fillRect(via.centro - largura / 2, via.de, largura, via.ate - via.de);
    if (comMeioFio) {
      ctx.fillStyle = corTexto(VIA.meioFio);
      ctx.fillRect(via.centro - largura / 2, via.de, 0.18, via.ate - via.de);
      ctx.fillRect(via.centro + largura / 2 - 0.18, via.de, 0.18, via.ate - via.de);
    }
  }
}

/** A faixa de asfalto (ou de calçada) de uma pista circular. */
function faixaCircular(ctx, pista, folga, cor, comMeioFio) {
  ctx.save();
  ctx.strokeStyle = corTexto(cor);
  ctx.lineWidth = pista.largura + folga * 2;
  ctx.beginPath();
  ctx.arc(pista.x, pista.z, pista.raio, 0, TAU);
  ctx.stroke();
  if (comMeioFio) {
    ctx.strokeStyle = corTexto(VIA.meioFio);
    ctx.lineWidth = 0.18;
    for (const lado of [-1, 1]) {
      ctx.beginPath();
      ctx.arc(pista.x, pista.z, pista.raio + lado * (pista.largura / 2 + folga), 0, TAU);
      ctx.stroke();
    }
  }
  ctx.restore();
}

/** A ilha do meio da rotatória: jardim com guia em volta. */
function ilhaDaRotatoria(ctx, r, f, sortear) {
  const interno = r.raio - r.largura / 2;
  ctx.save();
  ctx.beginPath();
  ctx.arc(r.x, r.z, interno, 0, TAU);
  ctx.clip();
  ctx.fillStyle = corTexto(f.base);
  ctx.fillRect(r.x - interno, r.z - interno, interno * 2, interno * 2);
  // Manchas de grama: sem isso a ilha fica um disco chapado no meio da tela,
  // e disco chapado é a coisa que mais denuncia chão desenhado.
  for (let i = 0; i < 16; i++) {
    const a = entre(sortear, 0, TAU);
    const d = entre(sortear, 0, interno);
    const raio = entre(sortear, interno * 0.12, interno * 0.4);
    ctx.fillStyle = corTexto(tonalizar(f.base, entre(sortear, 0.84, 1.14))) + '66';
    ctx.beginPath();
    ctx.ellipse(r.x + Math.cos(a) * d, r.z + Math.sin(a) * d,
      raio, raio * entre(sortear, 0.5, 1), entre(sortear, 0, TAU), 0, TAU);
    ctx.fill();
  }
  ctx.restore();

  // Guia branca e vermelha em volta: é o que se vê de longe e avisa que tem
  // ilha ali — o meio-fio 3D só aparece de perto.
  ctx.save();
  ctx.lineWidth = 0.55;
  ctx.strokeStyle = corTexto(0xf2eee2);
  ctx.beginPath();
  ctx.arc(r.x, r.z, interno, 0, TAU);
  ctx.stroke();
  ctx.strokeStyle = corTexto(0xd04a3c);
  ctx.lineWidth = 0.55;
  ctx.setLineDash([1.5, 1.5]);
  ctx.beginPath();
  ctx.arc(r.x, r.z, interno, 0, TAU);
  ctx.stroke();
  ctx.setLineDash([]);
  ctx.restore();
}

/** O mesmo desgaste das ruas, mas seguindo a pista circular. */
function desgastarCirculos(ctx, mundo, sortear) {
  const f = mundo.ficha;
  const pistas = [...mundo.aneis, ...mundo.rotatorias];
  for (const pista of pistas) {
    const quantos = Math.round((TAU * pista.raio) / 13);
    for (let i = 0; i < quantos; i++) {
      const a = entre(sortear, 0, TAU);
      const d = pista.raio + entre(sortear, -pista.largura / 2 + 0.4, pista.largura / 2 - 0.4);
      const x = pista.x + Math.cos(a) * d;
      const z = pista.z + Math.sin(a) * d;
      const tipo = sortear();
      ctx.save();
      if (tipo < 0.55) {
        ctx.fillStyle = corTexto(tonalizar(f.corAsfalto, entre(sortear, 0.8, 1.18)));
        ctx.globalAlpha = 0.20;
        ctx.beginPath();
        ctx.ellipse(x, z, entre(sortear, 0.6, 2.6), entre(sortear, 0.4, 1.7), a, 0, TAU);
        ctx.fill();
      } else if (tipo < 0.8) {
        ctx.globalAlpha = 0.09;
        ctx.fillStyle = '#0b0b0d';
        ctx.beginPath();
        ctx.ellipse(x, z, entre(sortear, 0.3, 0.8), entre(sortear, 0.2, 0.55), 0, 0, TAU);
        ctx.fill();
      }
      ctx.restore();
    }
  }
}

function pintarAnel(ctx, anel) {
  const meia = anel.largura / 2;
  ctx.save();
  ctx.strokeStyle = corTexto(VIA.faixa);
  ctx.lineWidth = 0.18;
  for (const lado of [-1, 1]) {
    ctx.beginPath();
    ctx.arc(anel.x, anel.z, anel.raio + lado * (meia - 0.7), 0, TAU);
    ctx.stroke();
  }
  // Eixo tracejado. O tracejado segue o arco sozinho — é a mesma conta de
  // linha pontilhada, só que o "comprimento" anda pela circunferência.
  ctx.strokeStyle = corTexto(VIA.faixaAmarela);
  ctx.lineWidth = 0.20;
  ctx.setLineDash([3.2, 3.0]);
  ctx.beginPath();
  ctx.arc(anel.x, anel.z, anel.raio, 0, TAU);
  ctx.stroke();
  ctx.setLineDash([]);
  ctx.restore();
}

function pintarRotatoria(ctx, r, mundo) {
  const meia = r.largura / 2;
  ctx.save();
  // Linha de "dê a preferência": tracejado grosso na entrada da rotatória.
  ctx.strokeStyle = corTexto(VIA.faixa);
  ctx.lineWidth = 0.42;
  ctx.setLineDash([1.1, 1.1]);
  ctx.beginPath();
  ctx.arc(r.x, r.z, r.raio + meia - 0.35, 0, TAU);
  ctx.stroke();
  ctx.setLineDash([]);

  // Uma faixa tracejada no miolo, para a rotatória não parecer um prato liso.
  ctx.strokeStyle = corTexto(VIA.faixa) + 'aa';
  ctx.lineWidth = 0.14;
  ctx.setLineDash([1.8, 2.4]);
  ctx.beginPath();
  ctx.arc(r.x, r.z, r.raio, 0, TAU);
  ctx.stroke();
  ctx.setLineDash([]);

  // Setas curvas dizendo para que lado se gira. Sem elas a rotatória é só um
  // anel; com elas, a pessoa sabe de que lado entrar antes de chegar.
  for (let i = 0; i < 4; i++) {
    const a = (i / 4) * TAU + Math.PI / 4;
    setaCurva(ctx, r.x, r.z, r.raio, a, 0.5);
  }
  ctx.restore();

  if (mundo) dentesDePreferencia(ctx, r, mundo);
}

/** Seta desenhada sobre o arco, apontando no sentido de giro. */
function setaCurva(ctx, cx, cz, raio, angulo, abertura) {
  ctx.save();
  ctx.strokeStyle = corTexto(VIA.faixa) + 'cc';
  ctx.lineWidth = 0.28;
  ctx.lineCap = 'butt';
  ctx.beginPath();
  ctx.arc(cx, cz, raio, angulo, angulo + abertura);
  ctx.stroke();

  // A ponta: um triângulo na tangente do fim do arco.
  const fim = angulo + abertura;
  const px = cx + Math.cos(fim) * raio;
  const pz = cz + Math.sin(fim) * raio;
  const tx = -Math.sin(fim), tz = Math.cos(fim);   // tangente
  const nx = Math.cos(fim), nz = Math.sin(fim);    // normal (para fora)
  ctx.fillStyle = corTexto(VIA.faixa) + 'cc';
  ctx.beginPath();
  ctx.moveTo(px + tx * 1.1, pz + tz * 1.1);
  ctx.lineTo(px - nx * 0.55, pz - nz * 0.55);
  ctx.lineTo(px + nx * 0.55, pz + nz * 0.55);
  ctx.closePath();
  ctx.fill();
  ctx.restore();
}

/** Os "dentes de tubarão" onde cada rua desemboca na rotatória. */
function dentesDePreferencia(ctx, r, mundo) {
  const borda = r.raio + r.largura / 2;
  ctx.save();
  ctx.fillStyle = corTexto(VIA.faixa) + 'e0';
  for (const via of mundo.vias) {
    const desvio = via.eixo === 'x' ? via.centro - r.z : via.centro - r.x;
    if (Math.abs(desvio) >= borda) continue;
    const meio = Math.sqrt(borda * borda - desvio * desvio);
    for (const lado of [-1, 1]) {
      // Ponto onde o eixo da rua encosta na borda da rotatória.
      const px = via.eixo === 'x' ? r.x + lado * meio : via.centro;
      const pz = via.eixo === 'x' ? via.centro : r.z + lado * meio;
      if (via.eixo === 'x' ? (px < via.de || px > via.ate) : (pz < via.de || pz > via.ate)) continue;
      // Recuado 1,2 m para fora: o dente fica ANTES da rotatória, não nela.
      const rx = via.eixo === 'x' ? px + lado * 1.2 : px;
      const rz = via.eixo === 'x' ? pz : pz + lado * 1.2;
      // Só a metade da pista por onde se entra (mão da direita).
      const meiaVia = via.largura / 2;
      for (let t = 0.15; t < 0.95; t += 0.16) {
        const off = (via.eixo === 'x' ? -lado : lado) * t * meiaVia;
        const dx = via.eixo === 'x' ? rx : rx + off;
        const dz = via.eixo === 'x' ? rz + off : rz;
        dente(ctx, dx, dz, via.eixo === 'x' ? -lado : 0, via.eixo === 'x' ? 0 : -lado);
      }
    }
  }
  ctx.restore();
}

function dente(ctx, x, z, dx, dz) {
  const px = -dz, pz = dx;   // perpendicular
  ctx.beginPath();
  ctx.moveTo(x + dx * 0.9, z + dz * 0.9);
  ctx.lineTo(x + px * 0.28, z + pz * 0.28);
  ctx.lineTo(x - px * 0.28, z - pz * 0.28);
  ctx.closePath();
  ctx.fill();
}

/** Um traço grosso seguindo o traçado — serve de asfalto e de calçada. */
function tracoDaPista(ctx, pista, espessura, cor) {
  ctx.save();
  ctx.lineWidth = espessura;
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  ctx.strokeStyle = corTexto(cor);
  ctx.beginPath();
  pista.pontos.forEach((p, i) => (i ? ctx.lineTo(p.x, p.z) : ctx.moveTo(p.x, p.z)));
  ctx.closePath();
  ctx.stroke();
  ctx.restore();
}

/**
 * A pista pintada.
 *
 * O truque das bordas brancas é desenhar o mesmo traçado três vezes: branco na
 * largura cheia, asfalto um pouco mais fino por cima (o que sobra do branco
 * vira as duas faixas de borda), e o eixo tracejado no meio. Sai mais barato e
 * mais certo do que calcular duas linhas paralelas a uma curva.
 */
function pintarPista(ctx, pista, f, sortear) {
  tracoDaPista(ctx, pista, pista.largura, VIA.faixa);
  tracoDaPista(ctx, pista, pista.largura - 1.3, f.corAsfalto);

  // Desgaste, seguindo a pista.
  ctx.save();
  for (let i = 0; i < pista.pontos.length; i += 3) {
    const p = pista.pontos[i];
    if (sortear() > 0.5) continue;
    ctx.globalAlpha = 0.18;
    ctx.fillStyle = corTexto(tonalizar(f.corAsfalto, entre(sortear, 0.8, 1.18)));
    ctx.beginPath();
    ctx.ellipse(p.x + entre(sortear, -4, 4), p.z + entre(sortear, -4, 4),
      entre(sortear, 0.7, 2.6), entre(sortear, 0.5, 1.8), entre(sortear, 0, TAU), 0, TAU);
    ctx.fill();
  }
  ctx.restore();

  if (f.terra) return;
  ctx.save();
  ctx.strokeStyle = corTexto(VIA.faixaAmarela);
  ctx.lineWidth = 0.22;
  ctx.setLineDash([3.4, 3.2]);
  ctx.lineJoin = 'round';
  ctx.beginPath();
  pista.pontos.forEach((p, i) => (i ? ctx.lineTo(p.x, p.z) : ctx.moveTo(p.x, p.z)));
  ctx.closePath();
  ctx.stroke();
  ctx.setLineDash([]);
  ctx.restore();
}

/** O quadriculado da largada, atravessado no começo do traçado. */
function pintarLargadaNaPista(ctx, pista) {
  const p = pista.pontos[0];
  const q = pista.pontos[1] || p;
  const dx = q.x - p.x, dz = q.z - p.z;
  const l = Math.hypot(dx, dz) || 1;
  // Ao longo da pista e atravessado nela.
  const ax = dx / l, az = dz / l;
  const nx = -az, nz = ax;
  const colunas = 10, linhas = 3;
  const passoN = pista.largura / colunas;
  const passoA = 1.0;
  ctx.save();
  for (let i = 0; i < colunas; i++) {
    for (let j = 0; j < linhas; j++) {
      ctx.fillStyle = (i + j) % 2 ? '#f4f2ea' : '#1c1d20';
      const n0 = -pista.largura / 2 + i * passoN;
      const a0 = (j - linhas / 2) * passoA;
      const cantos = [[n0, a0], [n0 + passoN, a0], [n0 + passoN, a0 + passoA], [n0, a0 + passoA]];
      ctx.beginPath();
      cantos.forEach(([n, a], k) => {
        const x = p.x + nx * n + ax * a;
        const z = p.z + nz * n + az * a;
        if (k) ctx.lineTo(x, z); else ctx.moveTo(x, z);
      });
      ctx.closePath();
      ctx.fill();
    }
  }
  ctx.restore();
}

/** O quadriculado da largada, atravessado na pista. */
function pintarLargada(ctx, anel, angulo) {
  const interno = anel.raio - anel.largura / 2;
  const colunas = 10;
  const linhas = 3;
  const passoR = anel.largura / colunas;
  const passoA = 1.0 / anel.raio;          // ~1 m de comprimento por quadrado
  ctx.save();
  for (let i = 0; i < colunas; i++) {
    for (let j = 0; j < linhas; j++) {
      ctx.fillStyle = (i + j) % 2 ? '#f4f2ea' : '#1c1d20';
      const r0 = interno + i * passoR;
      const a0 = angulo + (j - linhas / 2) * passoA;
      ctx.beginPath();
      ctx.arc(anel.x, anel.z, r0, a0, a0 + passoA);
      ctx.arc(anel.x, anel.z, r0 + passoR, a0 + passoA, a0, true);
      ctx.closePath();
      ctx.fill();
    }
  }
  ctx.restore();
}

function pintarVia(ctx, via, mundo, sortear) {
  const meia = via.largura / 2;
  ctx.save();
  ctx.lineCap = 'butt';

  // Bordas contínuas. Branco forte e linha grossa: num asfalto cinza-médio a
  // pintura tem que saltar, é ela que diz onde está a pista.
  ctx.strokeStyle = corTexto(VIA.faixa);
  ctx.lineWidth = 0.18;
  traco(ctx, via, -meia + 0.6);
  traco(ctx, via, meia - 0.6);

  // Eixo tracejado.
  ctx.strokeStyle = corTexto(VIA.faixaAmarela);
  ctx.lineWidth = 0.20;
  ctx.setLineDash([2.6, 2.4]);
  traco(ctx, via, 0);
  ctx.setLineDash([]);

  // Setas de direção, de vez em quando.
  const comprimento = via.ate - via.de;
  const quantas = Math.floor(comprimento / 34);
  for (let i = 0; i < quantas; i++) {
    if (sortear() > 0.55) continue;
    const t = (i + 0.5) / quantas;
    const p = via.de + t * comprimento;
    for (const lado of [-1, 1]) {
      const desvio = lado * meia * 0.5;
      if (via.eixo === 'x') seta(ctx, p, via.centro + desvio, lado > 0 ? -Math.PI / 2 : Math.PI / 2);
      else seta(ctx, via.centro + desvio, p, lado > 0 ? Math.PI : 0);
    }
  }
  ctx.restore();
}

function traco(ctx, via, desvio) {
  ctx.beginPath();
  if (via.eixo === 'x') {
    ctx.moveTo(via.de, via.centro + desvio);
    ctx.lineTo(via.ate, via.centro + desvio);
  } else {
    ctx.moveTo(via.centro + desvio, via.de);
    ctx.lineTo(via.centro + desvio, via.ate);
  }
  ctx.stroke();
}

function seta(ctx, x, z, angulo) {
  ctx.save();
  ctx.translate(x, z);
  ctx.rotate(angulo);
  ctx.fillStyle = corTexto(VIA.faixa) + 'd0';
  ctx.fillRect(-0.15, -0.6, 0.30, 1.7);
  ctx.beginPath();
  ctx.moveTo(0, -1.5);
  ctx.lineTo(0.5, -0.5);
  ctx.lineTo(-0.5, -0.5);
  ctx.closePath();
  ctx.fill();
  ctx.restore();
}

function faixasDePedestre(ctx, mundo, sortear) {
  const horizontais = mundo.vias.filter((v) => v.eixo === 'x');
  const verticais = mundo.vias.filter((v) => v.eixo === 'z');
  ctx.fillStyle = corTexto(VIA.faixa) + 'ee';

  for (const h of horizontais) {
    for (const v of verticais) {
      if (sortear() > 0.72) continue;
      const recuo = v.largura / 2 + 1.1;
      for (const lado of [-1, 1]) {
        const x = v.centro + lado * recuo;
        zebrado(ctx, x, h.centro, 1.9, h.largura - 1.2, 'x');
      }
      const recuoZ = h.largura / 2 + 1.1;
      for (const lado of [-1, 1]) {
        const z = h.centro + lado * recuoZ;
        zebrado(ctx, v.centro, z, v.largura - 1.2, 1.9, 'z');
      }
    }
  }
}

function zebrado(ctx, x, z, largura, profundidade, eixo) {
  const barras = 6;
  if (eixo === 'x') {
    const passo = profundidade / barras;
    for (let i = 0; i < barras; i++) {
      if (i % 2) continue;
      ctx.fillRect(x - largura / 2, z - profundidade / 2 + i * passo, largura, passo * 0.9);
    }
  } else {
    const passo = largura / barras;
    for (let i = 0; i < barras; i++) {
      if (i % 2) continue;
      ctx.fillRect(x - largura / 2 + i * passo, z - profundidade / 2, passo * 0.9, profundidade);
    }
  }
}

export function pintarVaga(ctx, vaga, cor, espessura = 0.12) {
  ctx.save();
  ctx.translate(vaga.x, vaga.z);
  ctx.rotate(-vaga.angulo);
  ctx.strokeStyle = corTexto(cor);
  ctx.lineWidth = espessura;
  const hl = vaga.largura / 2, hc = vaga.comprimento / 2;
  ctx.beginPath();
  ctx.moveTo(-hl, -hc);
  ctx.lineTo(-hl, hc);
  ctx.lineTo(hl, hc);
  ctx.lineTo(hl, -hc);
  ctx.stroke();
  ctx.restore();
}

export function destacarVaga(ctx, vaga) {
  ctx.save();
  ctx.translate(vaga.x, vaga.z);
  ctx.rotate(-vaga.angulo);
  ctx.fillStyle = 'rgba(78,214,132,0.26)';
  ctx.fillRect(-vaga.largura / 2, -vaga.comprimento / 2, vaga.largura, vaga.comprimento);
  // Um chevron apontando para o fundo da vaga, indicando o lado de entrar.
  ctx.fillStyle = 'rgba(120,240,165,0.45)';
  ctx.beginPath();
  ctx.moveTo(0, -vaga.comprimento * 0.30);
  ctx.lineTo(vaga.largura * 0.28, vaga.comprimento * 0.02);
  ctx.lineTo(-vaga.largura * 0.28, vaga.comprimento * 0.02);
  ctx.closePath();
  ctx.fill();
  ctx.restore();
  pintarVaga(ctx, vaga, 0x6cf09a, 0.20);
}

function desgaste(ctx, mundo, sortear) {
  const f = mundo.ficha;
  for (const via of mundo.vias) {
    const comprimento = via.ate - via.de;
    const quantos = Math.round(comprimento / 14);
    for (let i = 0; i < quantos; i++) {
      const p = entre(sortear, via.de, via.ate);
      const d = entre(sortear, -via.largura / 2 + 0.4, via.largura / 2 - 0.4);
      const x = via.eixo === 'x' ? p : via.centro + d;
      const z = via.eixo === 'x' ? via.centro + d : p;
      const tipo = sortear();

      if (tipo < 0.42) {
        // remendo
        ctx.fillStyle = corTexto(tonalizar(f.corAsfalto, entre(sortear, 0.78, 1.2)));
        ctx.globalAlpha = 0.22;
        ctx.beginPath();
        ctx.ellipse(x, z, entre(sortear, 0.5, 2.4), entre(sortear, 0.4, 1.8), entre(sortear, 0, TAU), 0, TAU);
        ctx.fill();
      } else if (tipo < 0.62) {
        // trinca
        ctx.strokeStyle = 'rgba(20,20,22,0.16)';
        ctx.lineWidth = 0.05;
        ctx.beginPath();
        let cx = x, cz = z;
        ctx.moveTo(cx, cz);
        for (let k = 0; k < 4; k++) {
          cx += entre(sortear, -0.9, 0.9);
          cz += entre(sortear, -0.9, 0.9);
          ctx.lineTo(cx, cz);
        }
        ctx.stroke();
      } else if (tipo < 0.76) {
        // mancha de óleo
        ctx.globalAlpha = 0.10;
        ctx.fillStyle = '#0b0b0d';
        ctx.beginPath();
        ctx.ellipse(x, z, entre(sortear, 0.25, 0.7), entre(sortear, 0.2, 0.5), 0, 0, TAU);
        ctx.fill();
      } else if (tipo < 0.85) {
        // bueiro
        ctx.globalAlpha = 1;
        ctx.fillStyle = corTexto(0x4a4d52);
        ctx.beginPath();
        ctx.arc(x, z, 0.36, 0, TAU);
        ctx.fill();
        ctx.strokeStyle = corTexto(0x2f3237);
        ctx.lineWidth = 0.05;
        for (let k = -2; k <= 2; k++) {
          ctx.beginPath();
          ctx.moveTo(x - 0.3, z + k * 0.12);
          ctx.lineTo(x + 0.3, z + k * 0.12);
          ctx.stroke();
        }
      }
      ctx.globalAlpha = 1;
    }
  }
}

function sombrasNoChao(ctx, mundo, ambiente) {
  const sol = ambiente.direcaoSol;
  if (!sol || sol.y <= 0.05) return;
  const comprimento = limitar(1.1 / sol.y, 0.2, 3.2);
  const dx = -sol.x * comprimento;
  const dz = -sol.z * comprimento;
  const forca = limitar(0.17 * (ambiente.luz ?? 1), 0.04, 0.24);

  ctx.save();
  ctx.fillStyle = `rgba(14,16,22,${forca.toFixed(3)})`;
  for (const c of mundo.colisores) {
    if (!c.solido || c.leve || !c.altura || c.altura < 2) continue;
    const alcance = Math.min(c.altura, 14);
    const ox = dx * alcance, oz = dz * alcance;
    ctx.save();
    ctx.translate(c.x, c.z);
    ctx.rotate(-c.guinada);
    ctx.beginPath();
    ctx.moveTo(-c.largura / 2, -c.comprimento / 2);
    ctx.lineTo(c.largura / 2, -c.comprimento / 2);
    ctx.lineTo(c.largura / 2 + ox, c.comprimento / 2 + oz);
    ctx.lineTo(-c.largura / 2 + ox, c.comprimento / 2 + oz);
    ctx.closePath();
    ctx.fill();
    ctx.fillRect(-c.largura / 2, -c.comprimento / 2, c.largura, c.comprimento);
    ctx.restore();
  }
  ctx.restore();
}

function molhar(ctx, pixels, sortear) {
  ctx.save();
  ctx.globalCompositeOperation = 'multiply';
  ctx.fillStyle = 'rgba(96,108,122,0.55)';
  ctx.fillRect(0, 0, pixels, pixels);
  ctx.globalCompositeOperation = 'lighter';
  // poças: pontos claros que refletem o céu
  for (let i = 0; i < 90; i++) {
    const x = sortear() * pixels;
    const y = sortear() * pixels;
    const r = entre(sortear, 6, 40);
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, 'rgba(150,170,190,0.30)');
    g.addColorStop(1, 'rgba(150,170,190,0)');
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, TAU);
    ctx.fill();
  }
  ctx.restore();
}

function aplicarGranulado(ctx, pixels, cor, semente, forca) {
  const lado = 64;
  const tile = document.createElement('canvas');
  tile.width = lado;
  tile.height = lado;
  const tctx = tile.getContext('2d');
  const img = tctx.createImageData(lado, lado);
  const sortear = criarSorteio(semente ^ 0x77ab);
  const r = (cor >> 16) & 255, g = (cor >> 8) & 255, b = cor & 255;
  for (let i = 0; i < lado * lado; i++) {
    const d = (sortear() - 0.5) * 255 * forca * 2;
    img.data[i * 4] = limitar(r + d, 0, 255);
    img.data[i * 4 + 1] = limitar(g + d, 0, 255);
    img.data[i * 4 + 2] = limitar(b + d * 0.8, 0, 255);
    img.data[i * 4 + 3] = 255;
  }
  tctx.putImageData(img, 0, 0);
  const padrao = ctx.createPattern(tile, 'repeat');
  ctx.fillStyle = padrao;
  ctx.fillRect(0, 0, pixels, pixels);
}

function manchasLargas(ctx, pixels, cor, sortear) {
  ctx.save();
  for (let i = 0; i < 70; i++) {
    const x = sortear() * pixels;
    const y = sortear() * pixels;
    const r = entre(sortear, pixels * 0.02, pixels * 0.10);
    const tom = tonalizar(cor, entre(sortear, 0.82, 1.16));
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, corTexto(tom) + 'aa');
    g.addColorStop(1, corTexto(tom) + '00');
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, TAU);
    ctx.fill();
  }
  ctx.restore();
}

/** O terreno que continua além da área jogável, repetindo sem emendar. */
function gerarEntorno(cor, semente) {
  const lado = LADO_ENTORNO;
  const tela = document.createElement('canvas');
  tela.width = lado;
  tela.height = lado;
  const ctx = tela.getContext('2d', { willReadFrequently: true });
  ctx.fillStyle = corTexto(tonalizar(cor, 0.94));
  ctx.fillRect(0, 0, lado, lado);
  const sortear = criarSorteio(semente ^ 0x1f5e);
  for (let i = 0; i < 260; i++) {
    const x = sortear() * lado;
    const y = sortear() * lado;
    const r = entre(sortear, 4, 26);
    ctx.fillStyle = corTexto(tonalizar(cor, entre(sortear, 0.72, 1.18)));
    ctx.globalAlpha = 0.5;
    ctx.beginPath();
    ctx.ellipse(x, y, r, r * entre(sortear, 0.5, 1), entre(sortear, 0, TAU), 0, TAU);
    ctx.fill();
  }
  ctx.globalAlpha = 1;
  aplicarGranulado(ctx, lado, tonalizar(cor, 0.94), semente ^ 0x3311, 0.07);
  return ctx.getImageData(0, 0, lado, lado).data;
}

/**
 * Risca o chão onde o pneu escorregou. Escreve direto nos bytes da imagem que
 * o terreno lê — é barato o bastante para rodar a cada quadro, e a marca fica
 * lá até o fim da missão.
 */
export function marcarChao(mapa, x, z, raio, forca) {
  const escala = mapa.pixels / mapa.metros;
  const cx = Math.round((x * escala) + mapa.pixels / 2);
  const cz = Math.round((z * escala) + mapa.pixels / 2);
  const r = Math.max(1, Math.round(raio * escala));
  const dados = mapa.dados;
  const lado = mapa.pixels;
  const escurecer = limitar(forca, 0, 1) * 0.5;

  for (let dz = -r; dz <= r; dz++) {
    const pz = cz + dz;
    if (pz < 0 || pz >= lado) continue;
    for (let dx = -r; dx <= r; dx++) {
      const px = cx + dx;
      if (px < 0 || px >= lado) continue;
      const distancia = Math.hypot(dx, dz);
      if (distancia > r) continue;
      const peso = escurecer * (1 - distancia / r);
      const i = (pz * lado + px) << 2;
      dados[i] -= dados[i] * peso;
      dados[i + 1] -= dados[i + 1] * peso;
      dados[i + 2] -= dados[i + 2] * peso;
    }
  }
}
