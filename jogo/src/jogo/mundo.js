// O mundo de cada missão.
//
// Nada é feito à mão: dada uma semente, sai um bairro inteiro — ruas cruzando,
// calçada, quarteirão com prédio, poste, árvore, lixeira, e as vagas pintadas
// no chão. A mesma semente devolve sempre o mesmo bairro (dá para repetir uma
// missão), e semente diferente devolve outro lugar.
//
// É metade do remédio contra o enjoo: nunca é a mesma rua duas vezes.

import {
  criarSorteio, entre, inteiro, escolher, embaralhar, limitar,
  distanciaPlana, TAU, misturarCor,
} from '../nucleo/matematica.js';
import * as modelos from '../motor/modelos.js';

export const CENARIOS = {
  cidade: {
    nome: 'centro', perfil: 'cidade', base: 0x6f7a63, piso: 'grama',
    corAsfalto: 0x3b3f45, corCalcada: 0xa8a49c,
    arvores: 'arvore', densidade: 1,
  },
  praia: {
    nome: 'beira-mar', perfil: 'praia', base: 0xdcc89a, piso: 'areia',
    corAsfalto: 0x4a4a48, corCalcada: 0xcfc4ad,
    arvores: 'palmeira', densidade: 0.7,
  },
  campo: {
    nome: 'estrada de terra', perfil: 'campo', base: 0x7d8b56, piso: 'grama',
    corAsfalto: 0x6f6047, corCalcada: 0x8e9a6a, terra: true,
    arvores: 'arvore', densidade: 0.8,
  },
  industrial: {
    nome: 'zona industrial', perfil: 'industrial', base: 0x77786f, piso: 'terra',
    corAsfalto: 0x44464b, corCalcada: 0x8d8e88,
    arvores: 'arbusto', densidade: 0.45,
  },
};

export const LISTA_CENARIOS = Object.keys(CENARIOS);

/**
 * Monta um bairro.
 *
 * `opcoes.estacionamento` troca um quarteirão por um pátio com vagas pintadas —
 * é onde as missões de vaga acontecem.
 */
export function gerarMundo(semente, opcoes = {}) {
  const sortear = criarSorteio(semente);
  const cenarioId = opcoes.cenario || 'cidade';
  const cenario = CENARIOS[cenarioId] || CENARIOS.cidade;
  const metros = opcoes.metros || 152;
  const borda = 14;
  const meio = metros / 2 - borda;

  const vias = [];
  const quantidadeX = inteiro(sortear, 2, 3);   // ruas que correm no eixo X
  const quantidadeZ = inteiro(sortear, 2, 3);

  const posicoesZ = espalhar(sortear, -meio + 8, meio - 8, quantidadeX, 26);
  const posicoesX = espalhar(sortear, -meio + 8, meio - 8, quantidadeZ, 26);

  for (const z of posicoesZ) {
    vias.push({
      eixo: 'x', centro: z, de: -meio, ate: meio,
      largura: entre(sortear, 9, 11.5), principal: Math.abs(z) < 6,
    });
  }
  for (const x of posicoesX) {
    vias.push({
      eixo: 'z', centro: x, de: -meio, ate: meio,
      largura: entre(sortear, 9, 11.5), principal: Math.abs(x) < 6,
    });
  }

  const calcada = 2.6;
  const quadras = montarQuadras(posicoesX, posicoesZ, vias, meio, calcada);

  const mundo = {
    semente,
    cenario: cenarioId,
    ficha: cenario,
    metros,
    meio,
    vias,
    calcada,
    quadras,
    vagas: [],
    props: [],
    colisores: [],
    marcasNoChao: [],
    rotas: [],
    limite: meio + 2,
  };

  if (opcoes.estacionamento) montarEstacionamento(mundo, sortear, opcoes.estacionamento);

  povoarQuadras(mundo, sortear, cenario, opcoes);
  cercarOMundo(mundo, sortear, cenario);
  montarRotas(mundo, sortear);

  mundo.inicio = escolherInicio(mundo, sortear, opcoes);
  return mundo;
}

/** Distribui n posições no intervalo, respeitando uma folga mínima. */
function espalhar(sortear, minimo, maximo, quantidade, folga) {
  const saida = [];
  let tentativas = 0;
  while (saida.length < quantidade && tentativas < 300) {
    tentativas++;
    const v = entre(sortear, minimo, maximo);
    if (saida.every((p) => Math.abs(p - v) >= folga)) saida.push(v);
  }
  saida.sort((a, b) => a - b);
  return saida;
}

function montarQuadras(posicoesX, posicoesZ, vias, meio, calcada) {
  const cortesX = [-meio, ...posicoesX, meio];
  const cortesZ = [-meio, ...posicoesZ, meio];
  const larguraDe = (centro, eixo) => {
    const via = vias.find((v) => v.eixo === eixo && v.centro === centro);
    return via ? via.largura : 0;
  };

  const quadras = [];
  for (let i = 0; i < cortesX.length - 1; i++) {
    for (let j = 0; j < cortesZ.length - 1; j++) {
      const x0 = cortesX[i] + larguraDe(cortesX[i], 'z') / 2;
      const x1 = cortesX[i + 1] - larguraDe(cortesX[i + 1], 'z') / 2;
      const z0 = cortesZ[j] + larguraDe(cortesZ[j], 'x') / 2;
      const z1 = cortesZ[j + 1] - larguraDe(cortesZ[j + 1], 'x') / 2;
      if (x1 - x0 < 8 || z1 - z0 < 8) continue;
      quadras.push({
        x0, x1, z0, z1,
        centroX: (x0 + x1) / 2, centroZ: (z0 + z1) / 2,
        largura: x1 - x0, profundidade: z1 - z0,
        calcada,
        usada: false,
      });
    }
  }
  return quadras;
}

/** Troca a maior quadra por um pátio de estacionamento com vagas pintadas. */
function montarEstacionamento(mundo, sortear, config) {
  const candidatas = mundo.quadras
    .filter((q) => q.largura > 22 && q.profundidade > 20)
    .sort((a, b) => b.largura * b.profundidade - a.largura * a.profundidade);
  const quadra = candidatas[0];
  if (!quadra) return;

  quadra.usada = true;
  quadra.patio = true;

  const fileiras = quadra.profundidade > 40 ? 2 : 1;
  const larguraVaga = config.larguraVaga || 2.65;
  const comprimentoVaga = config.comprimentoVaga || 5.2;
  const quantasPorFileira = Math.max(3, Math.floor((quadra.largura - 4) / larguraVaga));

  for (let f = 0; f < fileiras; f++) {
    const z = fileiras === 1
      ? quadra.centroZ - comprimentoVaga / 2 + 1
      : quadra.centroZ + (f === 0 ? -1 : 1) * (comprimentoVaga / 2 + 3.5) - comprimentoVaga / 2;
    for (let i = 0; i < quantasPorFileira; i++) {
      const x = quadra.x0 + 2 + (i + 0.5) * larguraVaga;
      if (x > quadra.x1 - 2) break;
      mundo.vagas.push({
        x, z: z + comprimentoVaga / 2,
        angulo: f === 0 ? 0 : Math.PI,
        largura: larguraVaga, comprimento: comprimentoVaga,
        ocupada: false, alvo: false,
      });
    }
  }
  mundo.patio = quadra;
}

function povoarQuadras(mundo, sortear, cenario, opcoes) {
  const densidade = cenario.densidade * (opcoes.densidade || 1);

  for (const quadra of mundo.quadras) {
    if (quadra.patio) { povoarPatio(mundo, quadra, sortear, cenario); continue; }

    const area = quadra.largura * quadra.profundidade;
    const util = { x0: quadra.x0 + 3.2, x1: quadra.x1 - 3.2, z0: quadra.z0 + 3.2, z1: quadra.z1 - 3.2 };
    const larguraUtil = util.x1 - util.x0;
    const profundidadeUtil = util.z1 - util.z0;

    if (larguraUtil > 7 && profundidadeUtil > 7 && sortear() < 0.78 * densidade + 0.16) {
      // Quarteirão construído.
      const quantos = limitar(Math.round(area / 260), 1, 4);
      for (let i = 0; i < quantos; i++) {
        const l = Math.round(limitar(entre(sortear, 7, larguraUtil * 0.8), 5, 18));
        const p = Math.round(limitar(entre(sortear, 7, profundidadeUtil * 0.8), 5, 18));
        const alturaMax = cenario.perfil === 'industrial' ? 10 : cenario.perfil === 'campo' ? 7 : 24;
        const h = Math.round(entre(sortear, 5, alturaMax));
        const x = entre(sortear, util.x0 + l / 2, util.x1 - l / 2);
        const z = entre(sortear, util.z0 + p / 2, util.z1 - p / 2);
        if (colide(mundo.colisores, x, z, l + 2, p + 2)) continue;
        const cor = escolher(sortear, cenario.perfil === 'industrial'
          ? [0x8a8f96, 0x9aa0a6, 0x76797e]
          : [0xd8cfc0, 0xc9b9a3, 0xbfc7cc, 0xd6c2b0, 0xb08f7a, 0xa8b3ab]);
        adicionar(mundo, {
          tipo: 'predio', malha: modelos.predio(l, h, p, cor, Math.floor(sortear() * 1e6)),
          x, z, guinada: 0, raio: Math.hypot(l, p) / 2 + 1,
        }, { largura: l, comprimento: p, solido: true, altura: h });
      }
    } else {
      // Praça: gramado com árvores e um banco.
      const arvores = Math.round(limitar(area / 90, 1, 7) * densidade);
      for (let i = 0; i < arvores; i++) {
        const x = entre(sortear, util.x0, util.x1);
        const z = entre(sortear, util.z0, util.z1);
        if (colide(mundo.colisores, x, z, 2.4, 2.4)) continue;
        plantarArvore(mundo, sortear, cenario, x, z);
      }
      if (sortear() < 0.5) {
        const x = entre(sortear, util.x0, util.x1);
        const z = entre(sortear, util.z0, util.z1);
        if (colide(mundo.colisores, x, z, 2.4, 2.4)) return;
        adicionar(mundo, {
          tipo: 'banco', malha: modelos.banco(), x, z,
          guinada: entre(sortear, 0, TAU), raio: 1.2,
        }, { largura: 1.7, comprimento: 0.7, solido: true, leve: true, altura: 0.9 });
      }
    }

    enfeitarCalcada(mundo, quadra, sortear, cenario, densidade);
  }
}

function povoarPatio(mundo, quadra, sortear, cenario) {
  // Só o contorno: dentro do pátio o chão tem que ficar livre.
  for (const lado of ['x0', 'x1']) {
    // Do lado de FORA da faixa asfaltada: dentro dela é onde se manobra.
    const x = quadra[lado] + (lado === 'x0' ? -0.7 : 0.7);
    const quantos = Math.floor(quadra.profundidade / 9);
    for (let i = 0; i < quantos; i++) {
      const z = quadra.z0 + (i + 0.5) * (quadra.profundidade / quantos);
      if (colide(mundo.colisores, x, z, 2.2, 2.2)) continue;
      if (sortear() < 0.55) plantarArvore(mundo, sortear, cenario, x, z);
      else adicionar(mundo, {
        tipo: 'poste', malha: modelos.poste(6), x, z, guinada: lado === 'x0' ? 0 : Math.PI, raio: 1.6,
      }, { largura: 0.4, comprimento: 0.4, solido: true, altura: 6, luz: { y: 5.7, raio: 6, cor: 0xffe9b0 } });
    }
  }
}

function plantarArvore(mundo, sortear, cenario, x, z) {
  const s = Math.floor(sortear() * 1e6);
  if (cenario.arvores === 'palmeira') {
    const h = entre(sortear, 4.5, 7.5);
    adicionar(mundo, { tipo: 'palmeira', malha: modelos.palmeira(h, s), x, z, guinada: entre(sortear, 0, TAU), raio: h * 0.4 },
      { largura: 0.5, comprimento: 0.5, solido: true, altura: h });
  } else if (cenario.arvores === 'arbusto') {
    const r = entre(sortear, 0.7, 1.3);
    adicionar(mundo, { tipo: 'arbusto', malha: modelos.arbusto(r, s), x, z, guinada: entre(sortear, 0, TAU), raio: r * 1.4 },
      { largura: r * 1.6, comprimento: r * 1.6, solido: true, leve: true, altura: r * 1.5 });
  } else {
    const h = entre(sortear, 4, 8);
    adicionar(mundo, { tipo: 'arvore', malha: modelos.arvore(h, s), x, z, guinada: entre(sortear, 0, TAU), raio: h * 0.45 },
      { largura: 0.6, comprimento: 0.6, solido: true, altura: h });
  }
}

function enfeitarCalcada(mundo, quadra, sortear, cenario, densidade) {
  // x0/x1 são a BORDA DO ASFALTO, e a calçada vai dali para dentro da quadra.
  // Somar para fora (x0 - 1.1) planta poste no meio da pista.
  const recuo = mundo.calcada * 0.5;
  const cantos = [
    { x: quadra.x0 + recuo, z: quadra.z0 + recuo },
    { x: quadra.x1 - recuo, z: quadra.z0 + recuo },
    { x: quadra.x1 - recuo, z: quadra.z1 - recuo },
    { x: quadra.x0 + recuo, z: quadra.z1 - recuo },
  ];
  for (const canto of cantos) {
    if (sortear() > 0.45 * densidade + 0.25) continue;
    if (colide(mundo.colisores, canto.x, canto.z, 2.2, 2.2)) continue;
    const escolha = sortear();
    if (escolha < 0.45) {
      adicionar(mundo, { tipo: 'poste', malha: modelos.poste(6.5), x: canto.x, z: canto.z, guinada: entre(sortear, 0, TAU), raio: 1.8 },
        { largura: 0.4, comprimento: 0.4, solido: true, altura: 6.5, luz: { y: 6.2, raio: 7, cor: 0xffe9b0 } });
    } else if (escolha < 0.7) {
      adicionar(mundo, { tipo: 'hidrante', malha: modelos.hidrante(), x: canto.x, z: canto.z, guinada: 0, raio: 0.5 },
        { largura: 0.4, comprimento: 0.4, solido: true, leve: true, altura: 0.9 });
    } else {
      adicionar(mundo, { tipo: 'placa', malha: modelos.placaDeRua(''), x: canto.x, z: canto.z, guinada: entre(sortear, 0, TAU), raio: 1 },
        { largura: 0.3, comprimento: 0.3, solido: true, leve: true, altura: 2.5 });
    }
  }
}

/** Muro em volta de tudo: o mundo tem fim, e o fim é visível. */
function cercarOMundo(mundo, sortear, cenario) {
  const r = mundo.limite;
  const passo = 12;
  const quantos = Math.ceil((r * 2) / passo);
  for (let i = 0; i < quantos; i++) {
    const t = -r + (i + 0.5) * (r * 2 / quantos);
    const comprimento = (r * 2) / quantos;
    const fazer = (x, z, guinada) => {
      const malha = cenario.perfil === 'praia' || cenario.perfil === 'campo'
        ? modelos.grade(comprimento, 1.3)
        : modelos.muro(comprimento, 2.4, cenario.perfil === 'industrial' ? 0x8a8d84 : 0xb9ac99);
      adicionar(mundo, { tipo: 'muro', malha, x, z, guinada, raio: comprimento }, {
        largura: comprimento, comprimento: 0.4, solido: true, guinada, altura: 2.4, parede: true,
      });
    };
    fazer(t, -r, 0);
    fazer(t, r, 0);
    fazer(-r, t, Math.PI / 2);
    fazer(r, t, Math.PI / 2);
  }
}

/** Caminhos que o trânsito segue: o miolo de cada rua, ida e volta. */
function montarRotas(mundo, sortear) {
  for (const via of mundo.vias) {
    const faixa = via.largura * 0.24;
    for (const sentido of [1, -1]) {
      const pontos = [];
      const passos = 6;
      for (let i = 0; i <= passos; i++) {
        const t = sentido > 0 ? i / passos : 1 - i / passos;
        const p = via.de + t * (via.ate - via.de);
        if (via.eixo === 'x') pontos.push({ x: p, z: via.centro + faixa * sentido });
        else pontos.push({ x: via.centro - faixa * sentido, z: p });
      }
      mundo.rotas.push({ pontos, via, sentido });
    }
  }
  return embaralhar(sortear, mundo.rotas);
}

function escolherInicio(mundo, sortear, opcoes) {
  if (opcoes.inicio) return opcoes.inicio;
  const via = escolher(sortear, mundo.vias.filter((v) => v.principal)) || mundo.vias[0];
  const t = entre(sortear, 0.25, 0.75);
  const p = via.de + t * (via.ate - via.de);
  const faixa = via.largura * 0.24;
  if (via.eixo === 'x') return { x: p, z: via.centro + faixa, angulo: -Math.PI / 2 };
  return { x: via.centro - faixa, z: p, angulo: Math.PI };
}

// ---------------------------------------------------------------------------

function adicionar(mundo, prop, colisor) {
  mundo.props.push(prop);
  if (colisor) {
    mundo.colisores.push({
      x: prop.x, z: prop.z,
      guinada: colisor.guinada ?? prop.guinada ?? 0,
      largura: colisor.largura, comprimento: colisor.comprimento,
      solido: colisor.solido !== false,
      leve: !!colisor.leve,
      parede: !!colisor.parede,
      altura: colisor.altura || 2,
      prop,
    });
    if (colisor.luz) {
      prop.luz = { ...colisor.luz };
    }
  }
  return prop;
}

function colide(colisores, x, z, largura, comprimento) {
  for (const c of colisores) {
    if (Math.abs(c.x - x) < (c.largura + largura) / 2
      && Math.abs(c.z - z) < (c.comprimento + comprimento) / 2) return true;
  }
  return false;
}

/** Que piso tem embaixo do carro — decide aderência e barulho de pneu. */
export function pisoEm(mundo, x, z) {
  for (const via of mundo.vias) {
    if (via.eixo === 'x') {
      if (Math.abs(z - via.centro) <= via.largura / 2 && x >= via.de && x <= via.ate) {
        return mundo.ficha.terra ? 'terra' : 'asfalto';
      }
    } else if (Math.abs(x - via.centro) <= via.largura / 2 && z >= via.de && z <= via.ate) {
      return mundo.ficha.terra ? 'terra' : 'asfalto';
    }
  }
  if (mundo.patio) {
    const q = mundo.patio;
    if (x > q.x0 && x < q.x1 && z > q.z0 && z < q.z1) return 'asfalto';
  }
  for (const via of mundo.vias) {
    const folga = via.largura / 2 + mundo.calcada;
    if (via.eixo === 'x') {
      if (Math.abs(z - via.centro) <= folga && x >= via.de && x <= via.ate) return 'calcada';
    } else if (Math.abs(x - via.centro) <= folga && z >= via.de && z <= via.ate) return 'calcada';
  }
  return mundo.ficha.piso;
}

/** Ponto aleatório sobre o asfalto — ponto de passagem, entrega, o que for. */
export function pontoNaRua(mundo, sortear, longeDe = null, distanciaMinima = 25) {
  for (let tentativa = 0; tentativa < 80; tentativa++) {
    const via = escolher(sortear, mundo.vias);
    const t = entre(sortear, 0.12, 0.88);
    const p = via.de + t * (via.ate - via.de);
    const faixa = via.largura * 0.22;
    const ponto = via.eixo === 'x'
      ? { x: p, z: via.centro + faixa }
      : { x: via.centro - faixa, z: p };
    if (longeDe && distanciaPlana(ponto.x, ponto.z, longeDe.x, longeDe.z) < distanciaMinima) continue;
    ponto.via = via;
    return ponto;
  }
  return { x: 0, z: 0 };
}

export function corDoPiso(mundo, piso) {
  const f = mundo.ficha;
  if (piso === 'asfalto') return f.corAsfalto;
  if (piso === 'calcada') return f.corCalcada;
  return misturarCor(f.base, 0x000000, 0.1);
}
