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
  // Um bairro de 300 m numa imagem de 1600 dava cinco pixels por metro: uma
  // faixa de 15 cm não chegava a ocupar um pixel inteiro, e era daí que vinha
  // o ar de papelão de perto. 2048 dobra a nitidez onde ela é vista.
  const pixels = qualidade === 'alta' ? 2048 : qualidade === 'media' ? 1400 : 1000;
  const metros = mundo.metros;
  const escala = pixels / metros;

  const tela = document.createElement('canvas');
  tela.width = pixels;
  tela.height = pixels;
  const ctx = tela.getContext('2d', { willReadFrequently: true });
  const sortear = criarSorteio(mundo.semente ^ 0x2c1b);
  const f = mundo.ficha;

  // 1. Terreno de base.
  //
  // Um verde só, chapado, em metade da tela: era isso que mais entregava que o
  // chão é uma imagem pintada e não um lugar. O remendo é um ladrilho de mato
  // de 22 metros feito uma vez e repetido — tufo, talo, clareira — e por cima
  // as manchas largas, que são o que quebra a repetição do ladrilho.
  ctx.fillStyle = corTexto(f.base);
  ctx.fillRect(0, 0, pixels, pixels);
  aplicarGranulado(ctx, pixels, f.base, mundo.semente, 0.055);
  ctx.fillStyle = ctx.createPattern(
    texturaDoTerreno(f.base, f.piso, mundo.semente, escala), 'repeat');
  ctx.fillRect(0, 0, pixels, pixels);
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

  // 6. Grão do asfalto e faixa de rodado. Vai por ÚLTIMO e por cima de tudo
  //    que é pista, porque é o acabamento: é ele que tira o ar de papel
  //    colorido do chão e põe textura onde a câmera passa mais tempo olhando.
  granuladoDoAsfalto(ctx, mundo, sortear);

  // 7. Sombra dos prédios, na direção do sol. É o que assenta a cidade no chão.
  sombrasNoChao(ctx, mundo, ambiente);

  ctx.restore();

  if (ambiente.molhado) molhar(ctx, pixels, sortear);

  const niveis = gerarNiveis(tela, pixels, metros);
  return {
    dados: niveis[0].dados,
    pixels,
    metros,
    tela,
    niveis,
    entorno: gerarEntorno(f.base, mundo.semente),
    entornoLado: LADO_ENTORNO,
  };
}

/**
 * A escada das faixas.
 *
 * O chão é uma imagem só, lida pixel a pixel. Perto da câmera um texel do mapa
 * cobre uma dúzia de pixels da tela e a faixa amarela vira degrau; longe, o
 * contrário — meio metro de asfalto cabe num pixel e a textura ferve a cada
 * quadro. São os dois lados do mesmo problema e cada um pede um remédio.
 *
 * Longe é o que se resolve aqui: guardamos a mesma imagem em metade e em um
 * quarto do tamanho, já borradas pelo próprio navegador, e o terreno escolhe
 * por linha qual delas ler. Custa uma vez, no carregamento. (Perto se resolve
 * lendo os quatro texels vizinhos, e isso mora no terreno.js.)
 */
function gerarNiveis(tela, pixels, metros) {
  const niveis = [];
  let fonte = tela;
  let lado = pixels;
  for (let n = 0; n < 3 && lado >= 128; n++) {
    let dados;
    if (n === 0) {
      dados = tela.getContext('2d').getImageData(0, 0, lado, lado).data;
    } else {
      const meio = document.createElement('canvas');
      meio.width = lado;
      meio.height = lado;
      const c = meio.getContext('2d', { willReadFrequently: true });
      c.imageSmoothingEnabled = true;
      c.imageSmoothingQuality = 'high';
      c.drawImage(fonte, 0, 0, lado, lado);
      dados = c.getImageData(0, 0, lado, lado).data;
      fonte = meio;
    }
    niveis.push({ dados, lado, escala: lado / metros, meio: lado / 2 });
    lado = lado >> 1;
  }
  return niveis;
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

/**
 * Grão e rodado.
 *
 * Duas coisas, e as duas só no asfalto:
 *
 *   grão    salpico fino de claro e escuro. Asfalto liso é a superfície que
 *           mais entrega desenho feito às pressas, porque é a que ocupa metade
 *           da tela e é a única que se olha o tempo todo.
 *   rodado  duas faixas mais escuras e polidas onde o pneu passa. Elas
 *           desenham a trajetória da rua sem nenhuma seta, e é por isso que
 *           rua de verdade "puxa" o olho para onde se deve ir.
 */
function granuladoDoAsfalto(ctx, mundo, sortear) {
  if (mundo.ficha.terra) return;
  ctx.save();

  const salpicar = (x, z, l, p) => {
    const quantos = Math.round((l * p) / 5);
    for (let i = 0; i < quantos; i++) {
      const claro = sortear() > 0.5;
      ctx.fillStyle = claro ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.07)';
      const px = x + (sortear() - 0.5) * l;
      const pz = z + (sortear() - 0.5) * p;
      const r = entre(sortear, 0.08, 0.30);
      ctx.fillRect(px, pz, r, r);
    }
  };

  for (const via of mundo.vias) {
    const comprimento = via.ate - via.de;
    const meio = (via.de + via.ate) / 2;
    if (via.eixo === 'x') salpicar(meio, via.centro, comprimento, via.largura);
    else salpicar(via.centro, meio, via.largura, comprimento);

    // Rodado: uma faixa de cada lado do eixo, na linha em que o pneu anda.
    // Fraco de propósito. Escuro demais isso não vira marca de pneu, vira
    // mancha — e uma mancha larga no asfalto denuncia a emenda com o pedaço
    // de rua que ela não alcança.
    ctx.fillStyle = 'rgba(0,0,0,0.045)';
    const faixa = via.largura * 0.22;
    const largo = via.largura * 0.052;
    for (const lado of [-1, 1]) {
      for (const desvio of [-0.5, 0.5]) {
        const centro = lado * faixa + desvio * via.largura * 0.055;
        if (via.eixo === 'x') {
          ctx.fillRect(via.de, via.centro + centro - largo / 2, comprimento, largo);
        } else {
          ctx.fillRect(via.centro + centro - largo / 2, via.de, largo, comprimento);
        }
      }
    }
  }

  if (mundo.pista) {
    const pista = mundo.pista;
    for (let i = 0; i < pista.pontos.length; i += 2) {
      const p = pista.pontos[i];
      salpicar(p.x, p.z, pista.largura, pista.largura * 0.6);
    }
    // Rodado seguindo o traçado: quatro linhas finas, duas por roda, não duas
    // tarjas gordas. Pneu polido no asfalto é um risco estreito e claro; o
    // borrão largo que estava aqui escurecia meia pista e cortava seco onde a
    // curva jogava a faixa por cima do eixo.
    ctx.save();
    ctx.strokeStyle = 'rgba(0,0,0,0.05)';
    ctx.lineWidth = pista.largura * 0.035;
    ctx.lineJoin = 'round';
    for (const off of [-0.25, -0.20, 0.20, 0.25]) {
      ctx.beginPath();
      pista.pontos.forEach((p, i) => {
        const a = pista.pontos[(i - 1 + pista.pontos.length) % pista.pontos.length];
        const b = pista.pontos[(i + 1) % pista.pontos.length];
        const dx = b.x - a.x, dz = b.z - a.z;
        const l = Math.hypot(dx, dz) || 1;
        const d = off * pista.largura;
        const x = p.x - (dz / l) * d;
        const z = p.z + (dx / l) * d;
        if (i) ctx.lineTo(x, z); else ctx.moveTo(x, z);
      });
      ctx.closePath();
      ctx.stroke();
    }
    ctx.restore();
  }

  if (mundo.patio) {
    const q = mundo.patio;
    salpicar(q.centroX, q.centroZ, q.largura, q.profundidade);
  }
  ctx.restore();
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

/**
 * Um ladrilho de terreno que fecha em si mesmo.
 *
 * Tudo é desenhado em coordenadas de MAPA (pixel), com o lado do ladrilho
 * medido em metros, para que o tamanho do tufo não mude com a qualidade. O que
 * faz o ladrilho fechar é o `marcar`: toda marca perto da borda é desenhada de
 * novo do outro lado, então nenhuma fica cortada na emenda.
 */
function texturaDoTerreno(cor, piso, semente, escala) {
  const metrosDoLado = 22;
  const lado = Math.max(96, Math.round(metrosDoLado * escala));
  const tela = document.createElement('canvas');
  tela.width = lado;
  tela.height = lado;
  const ctx = tela.getContext('2d');
  const sortear = criarSorteio(semente ^ 0x6b21);
  const porMetro = lado / metrosDoLado;

  const marcar = (x, y, desenhar) => {
    for (const dx of [-lado, 0, lado]) {
      for (const dy of [-lado, 0, lado]) {
        const px = x + dx, py = y + dy;
        if (px < -lado * 0.2 || px > lado * 1.2 || py < -lado * 0.2 || py > lado * 1.2) continue;
        desenhar(px, py);
      }
    }
  };

  if (piso === 'areia') {
    // Areia: ondinha de vento, comprida e rasa, quase sem contraste.
    ctx.lineCap = 'round';
    for (let i = 0; i < 260; i++) {
      const x = sortear() * lado, y = sortear() * lado;
      const l = entre(sortear, 1.4, 5.5) * porMetro;
      const a = entre(sortear, -0.5, 0.5);
      const claro = sortear() > 0.5;
      ctx.strokeStyle = corTexto(tonalizar(cor, claro ? 1.07 : 0.94));
      ctx.globalAlpha = entre(sortear, 0.18, 0.4);
      ctx.lineWidth = entre(sortear, 0.08, 0.22) * porMetro;
      const curva = entre(sortear, -0.3, 0.3) * porMetro;
      marcar(x, y, (px, py) => {
        ctx.beginPath();
        ctx.moveTo(px - Math.cos(a) * l / 2, py - Math.sin(a) * l / 2);
        ctx.quadraticCurveTo(px, py + curva,
          px + Math.cos(a) * l / 2, py + Math.sin(a) * l / 2);
        ctx.stroke();
      });
    }
  } else if (piso === 'terra') {
    // Cascalho: pedrisco miúdo e poça de terra batida.
    for (let i = 0; i < 90; i++) {
      const x = sortear() * lado, y = sortear() * lado;
      const r = entre(sortear, 0.8, 3.2) * porMetro;
      ctx.fillStyle = corTexto(tonalizar(cor, entre(sortear, 0.84, 1.12)));
      ctx.globalAlpha = 0.5;
      const achatar = entre(sortear, 0.55, 1);
      marcar(x, y, (px, py) => {
        ctx.beginPath();
        ctx.ellipse(px, py, r, r * achatar, 0, 0, TAU);
        ctx.fill();
      });
    }
    for (let i = 0; i < 700; i++) {
      const x = sortear() * lado, y = sortear() * lado;
      const r = entre(sortear, 0.05, 0.16) * porMetro;
      ctx.fillStyle = corTexto(tonalizar(cor, sortear() > 0.5 ? 1.25 : 0.7));
      ctx.globalAlpha = entre(sortear, 0.3, 0.75);
      marcar(x, y, (px, py) => ctx.fillRect(px, py, r, r));
    }
  } else {
    // Mato.
    //
    // Nada aqui tem tamanho de folha. A sete pixels por metro, talo de grama
    // vira um pixel solto e mil deles viram um cinza uniforme — flat de novo,
    // só que mais caro. O que se enxerga a esta escala é CLAREIRA (três a oito
    // metros) e TUFO (meio metro a um metro e meio), e são esses dois que dão
    // relevo ao campo quando a câmera passa por cima a cem por hora.
    for (let i = 0; i < 80; i++) {
      const x = sortear() * lado, y = sortear() * lado;
      const r = entre(sortear, 1.6, 5.0) * porMetro;
      ctx.fillStyle = corTexto(tonalizar(cor, entre(sortear, 0.78, 1.16)));
      ctx.globalAlpha = entre(sortear, 0.3, 0.58);
      const achatar = entre(sortear, 0.55, 1);
      const giro = entre(sortear, 0, TAU);
      marcar(x, y, (px, py) => {
        ctx.beginPath();
        ctx.ellipse(px, py, r, r * achatar, giro, 0, TAU);
        ctx.fill();
      });
    }
    for (let i = 0; i < 900; i++) {
      const x = sortear() * lado, y = sortear() * lado;
      const r = entre(sortear, 0.22, 0.75) * porMetro;
      ctx.fillStyle = corTexto(tonalizar(cor, sortear() > 0.45 ? 1.2 : 0.74));
      ctx.globalAlpha = entre(sortear, 0.24, 0.5);
      const achatar = entre(sortear, 0.5, 1);
      const giro = entre(sortear, 0, TAU);
      marcar(x, y, (px, py) => {
        ctx.beginPath();
        ctx.ellipse(px, py, r, r * achatar, giro, 0, TAU);
        ctx.fill();
      });
    }
  }
  ctx.globalAlpha = 1;
  return tela;
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
