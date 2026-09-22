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
import { TERRENO, VIA, FACHADAS, FACHADAS_INDUSTRIAIS, CENA } from '../motor/paleta.js';

export const CENARIOS = {
  cidade: {
    nome: 'centro', perfil: 'cidade', base: TERRENO.grama, piso: 'grama',
    corAsfalto: VIA.asfalto, corCalcada: VIA.calcada,
    fachadas: FACHADAS, arvores: 'arvore', densidade: 1,
  },
  praia: {
    nome: 'beira-mar', perfil: 'praia', base: TERRENO.areia, piso: 'areia',
    corAsfalto: VIA.asfaltoClaro, corCalcada: 0xe8dcc0,
    fachadas: FACHADAS, arvores: 'palmeira', densidade: 0.7,
  },
  campo: {
    nome: 'estrada de terra', perfil: 'campo', base: TERRENO.gramaClara, piso: 'grama',
    corAsfalto: VIA.terra, corCalcada: 0x9ac25e, terra: true,
    fachadas: FACHADAS, arvores: 'arvore', densidade: 0.8,
  },
  industrial: {
    nome: 'zona industrial', perfil: 'industrial', base: TERRENO.cascalho, piso: 'terra',
    corAsfalto: VIA.asfalto, corCalcada: 0xd2d0c4,
    fachadas: FACHADAS_INDUSTRIAIS, arvores: 'arbusto', densidade: 0.45,
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
    aneis: [],        // pista circular fechada — o traçado do modo rápido
    rotatorias: [],   // rotatória com ilha no meio
    limite: meio + 2,
  };

  // O traçado "circuito" é outro bairro: em vez de grade de ruas, uma pista
  // fechada em volta e uma rotatória no meio. É onde os modos rápido e drift
  // acontecem — grade de esquina não deixa ninguém passar de 60 por hora.
  if (opcoes.tracado === 'circuito') {
    montarCircuito(mundo, sortear, cenario);
    cercarOMundo(mundo, sortear, cenario);
    mundo.inicio = escolherInicio(mundo, sortear, opcoes);
    return mundo;
  }

  if (opcoes.estacionamento) montarEstacionamento(mundo, sortear, opcoes.estacionamento);
  if (opcoes.rotatoria) montarRotatoriaNoCruzamento(mundo, sortear);

  povoarQuadras(mundo, sortear, cenario, opcoes);
  cercarOMundo(mundo, sortear, cenario);
  montarRotas(mundo, sortear);

  mundo.inicio = escolherInicio(mundo, sortear, opcoes);
  return mundo;
}

// ---------------------------------------------------------------------------
// CIRCUITO: ANEL + ROTATÓRIA
// ---------------------------------------------------------------------------

/**
 * Pista fechada em anel, com uma rotatória no meio e duas retas ligando as
 * duas. Dá voltas, dá para abrir o carro e dá curva longa para atravessar de
 * lado — que é exatamente o que os modos rápido e drift precisam.
 */
function montarCircuito(mundo, sortear, cenario) {
  const raio = mundo.meio * 0.66;
  const largura = 14;
  mundo.aneis.push({ x: 0, z: 0, raio, largura });

  const raioRotatoria = 13;
  const larguraRotatoria = 9;
  mundo.rotatorias.push({ x: 0, z: 0, raio: raioRotatoria, largura: larguraRotatoria });

  // A linha de largada fica num ângulo fixo do anel. Quem conta volta é o modo;
  // aqui só existe o lugar, pintado no chão e guardado no mundo.
  mundo.largada = { angulo: 0, x: raio, z: 0 };

  // As duas retas que ligam a rotatória ao anel. Ficam como "vias" comuns,
  // então tudo que já sabe lidar com via (pintura, piso, trânsito) funciona.
  const alcance = raio + largura / 2;
  mundo.vias.length = 0;
  mundo.vias.push(
    { eixo: 'x', centro: 0, de: -alcance, ate: alcance, largura: 11, principal: true },
    { eixo: 'z', centro: 0, de: -alcance, ate: alcance, largura: 11, principal: true },
  );

  guardaCorpoDoAnel(mundo, raio - largura / 2 - 0.9, 'dentro');
  guardaCorpoDoAnel(mundo, raio + largura / 2 + 0.9, 'fora');
  meioFioDaIlha(mundo, raioRotatoria - larguraRotatoria / 2);

  // Dentro da ilha da rotatória: um pedaço de jardim que se vê de longe.
  plantarArvore(mundo, sortear, cenario, 0, 0);
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * TAU + 0.3;
    const d = (raioRotatoria - larguraRotatoria / 2) * 0.55;
    if (sortear() < 0.7) {
      adicionar(mundo, {
        tipo: 'arbusto', malha: modelos.arbusto(0.9, Math.floor(sortear() * 1e6)),
        x: Math.cos(a) * d, z: Math.sin(a) * d, guinada: entre(sortear, 0, TAU), raio: 1.3,
      }, { largura: 1.4, comprimento: 1.4, solido: true, leve: true, altura: 1.4 });
    }
  }

  // Cenário: fora do anel, o bairro; dentro, entre a rotatória e a pista,
  // um parque. Nada encosta na pista — ela tem que ficar livre.
  // Os outdoors escolhem lugar ANTES do resto do cenário: eles têm posição
  // certa (rente à pista, de frente para ela) e a árvore não tem.
  outdoorsNaPista(mundo, sortear, raio + largura / 2 + 4.6, 7);
  povoarEmVolta(mundo, sortear, cenario, raio + largura / 2 + 10, mundo.meio - 4, 26);
  povoarEmVolta(mundo, sortear, cenario, raioRotatoria + larguraRotatoria, raio - largura / 2 - 6, 14);

  // Trânsito dando voltas, nos dois sentidos, em faixas diferentes.
  for (const sentido of [1, -1]) {
    const faixa = raio + sentido * largura * 0.22;
    const pontos = [];
    const passos = 28;
    for (let i = 0; i < passos; i++) {
      const a = (sentido > 0 ? i : passos - i) / passos * TAU;
      pontos.push({ x: Math.cos(a) * faixa, z: Math.sin(a) * faixa });
    }
    mundo.rotas.push({ pontos, anel: mundo.aneis[0], sentido });
  }
}

/**
 * Outdoors plantados em volta do anel, de frente para quem passa.
 *
 * Servem a duas coisas ao mesmo tempo: enchem o vazio do lado de fora da
 * pista, e — porque são grandes, coloridos e sempre no mesmo lugar — viram
 * marcação de curva. Depois de três voltas você freia no outdoor amarelo sem
 * pensar, que é exatamente como se decora um traçado.
 */
function outdoorsNaPista(mundo, sortear, raio, quantos) {
  // O guarda-corpo é uma parede contínua em volta da pista inteira, então
  // testar contra ele reprova qualquer lugar e não nasce outdoor nenhum. O
  // que importa aqui é não subir em cima de árvore ou prédio.
  const cenario = mundo.colisores.filter((c) => !c.parede);
  for (let i = 0; i < quantos; i++) {
    const a = (i / quantos) * TAU + entre(sortear, -0.1, 0.1);
    const x = Math.cos(a) * raio;
    const z = Math.sin(a) * raio;
    if (colide(cenario, x, z, 10, 10)) continue;
    const largura = entre(sortear, 7, 10);
    const altura = entre(sortear, 6.5, 8.5);
    // O painel fica de frente para o centro da pista: a face do cartaz é -Z,
    // e frente(guinada) = (-sen, -cos), então apontar para dentro pede
    // guinada = PI/2 - a.
    adicionar(mundo, {
      tipo: 'outdoor', malha: modelos.outdoor(largura, altura, Math.floor(sortear() * 1e6)),
      x, z, guinada: Math.PI / 2 - a, raio: largura,
    }, {
      largura, comprimento: 0.6, guinada: Math.PI / 2 - a,
      solido: true, altura, parede: true,
    });
  }
}

/** Guarda-corpo em volta da pista: sem ele, sair da pista é sair do jogo. */
function guardaCorpoDoAnel(mundo, raio, lado) {
  const passo = 11;
  const quantos = Math.max(12, Math.round((TAU * raio) / passo));
  for (let i = 0; i < quantos; i++) {
    const a = (i / quantos) * TAU;
    const x = Math.cos(a) * raio;
    const z = Math.sin(a) * raio;
    const comprimento = (TAU * raio) / quantos + 0.6;
    // A barreira fica tangente ao círculo: o ângulo dela é o da tangente.
    const guinada = -a;
    adicionar(mundo, {
      tipo: 'barreira', malha: modelos.barreira(comprimento),
      x, z, guinada, raio: comprimento, lado,
    }, {
      largura: 0.6, comprimento, guinada, solido: true, parede: true, altura: 0.8,
    });
  }
}

/** O meio-fio da ilha da rotatória, que impede cortar caminho por cima. */
function meioFioDaIlha(mundo, raio) {
  const quantos = Math.max(10, Math.round((TAU * raio) / 4));
  for (let i = 0; i < quantos; i++) {
    const a = (i / quantos) * TAU;
    const comprimento = (TAU * raio) / quantos + 0.4;
    adicionar(mundo, {
      tipo: 'meio-fio', malha: modelos.meioFio(comprimento),
      x: Math.cos(a) * raio, z: Math.sin(a) * raio, guinada: -a, raio: comprimento,
    }, {
      largura: 0.5, comprimento, guinada: -a, solido: true, altura: 0.35, leve: false,
    });
  }
}

/** Enche uma coroa circular de cenário, sem encostar nas bordas. */
function povoarEmVolta(mundo, sortear, cenario, raioInterno, raioExterno, quantos) {
  if (raioExterno <= raioInterno + 2) return;
  for (let i = 0; i < quantos; i++) {
    const a = entre(sortear, 0, TAU);
    const d = entre(sortear, raioInterno, raioExterno);
    const x = Math.cos(a) * d;
    const z = Math.sin(a) * d;
    if (colide(mundo.colisores, x, z, 9, 9)) continue;

    if (sortear() < 0.42 && d > raioInterno + 6) {
      const l = Math.round(entre(sortear, 6, 13));
      const p = Math.round(entre(sortear, 6, 12));
      const h = Math.round(entre(sortear, 5, 18));
      if (colide(mundo.colisores, x, z, l + 4, p + 4)) continue;
      const cor = escolher(sortear, cenario.fachadas || FACHADAS);
      adicionar(mundo, {
        tipo: 'predio', malha: modelos.predio(l, h, p, cor, Math.floor(sortear() * 1e6)),
        x, z, guinada: 0, raio: Math.hypot(l, p) / 2 + 1,
      }, { largura: l, comprimento: p, solido: true, altura: h });
    } else {
      plantarArvore(mundo, sortear, cenario, x, z);
    }
  }
}

/** Troca um cruzamento da grade por uma rotatória. */
function montarRotatoriaNoCruzamento(mundo, sortear) {
  const horizontais = mundo.vias.filter((v) => v.eixo === 'x');
  const verticais = mundo.vias.filter((v) => v.eixo === 'z');
  if (!horizontais.length || !verticais.length) return;

  // O cruzamento mais central: é o que tem mais espaço em volta.
  const h = horizontais.reduce((a, b) => (Math.abs(a.centro) < Math.abs(b.centro) ? a : b));
  const v = verticais.reduce((a, b) => (Math.abs(a.centro) < Math.abs(b.centro) ? a : b));

  const largura = Math.max(h.largura, v.largura) * 0.82;
  const raio = largura * 1.35;
  mundo.rotatorias.push({ x: v.centro, z: h.centro, raio, largura });
  meioFioDaIlha(mundo, raio - largura / 2);
  mundo.rotatoriaPrincipal = mundo.rotatorias[0];
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
      // Quarteirão construído: a construção vai encostada na CALÇADA, de
      // frente para a rua, formando um corredor. Prédio solto no meio do lote
      // deixa um vazio de grama dos dois lados da pista, e rua com vazio é o
      // que faz um bairro parecer maquete em vez de cidade.
      for (const lado of ['oeste', 'leste', 'norte', 'sul']) {
        fileiraDeFachadas(mundo, sortear, cenario, quadra, lado, densidade);
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
      // Terreno vazio com outdoor em cima é a coisa mais comum que existe numa
      // cidade, e é de graça: enche o buraco na paisagem e dá referência de
      // onde você está, que num bairro sorteado é o que mais falta.
      if (larguraUtil > 8 && profundidadeUtil > 6 && sortear() < 0.45 * densidade + 0.18) {
        const largura = Math.min(9, larguraUtil * 0.7);
        const x = entre(sortear, util.x0 + largura / 2, util.x1 - largura / 2);
        const z = entre(sortear, util.z0 + 1, util.z1 - 1);
        if (!colide(mundo.colisores, x, z, largura + 2, 4)) {
          const altura = entre(sortear, 6, 8);
          const guinada = sortear() < 0.5 ? 0 : Math.PI;
          adicionar(mundo, {
            tipo: 'outdoor', malha: modelos.outdoor(largura, altura, Math.floor(sortear() * 1e6)),
            x, z, guinada, raio: largura,
          }, { largura, comprimento: 0.6, guinada, solido: true, altura, parede: true });
        }
      } else if (sortear() < 0.5) {
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

/**
 * Põe uma construção no lugar indicado, sorteando entre casa, loja e prédio.
 *
 * A mistura depende do cenário: no centro tem prédio e loja, no campo quase
 * tudo é casa, na zona industrial é galpão — que aqui é prédio baixo e largo.
 * A altura pedida é só um palpite: casa alta demais vira caixa, então a casa
 * puxa a altura dela para baixo antes de nascer.
 */
function construir(mundo, sortear, cenario, x, z, largura, altura, profundidade, guinadaFixa) {
  const semente = Math.floor(sortear() * 1e6);
  const cor = escolher(sortear, cenario.fachadas || FACHADAS);
  const perfil = cenario.perfil;
  const sorte = sortear();

  const querCasa = perfil === 'campo' ? sorte < 0.82
    : perfil === 'industrial' ? sorte < 0.12
      : sorte < 0.34;
  const querLoja = !querCasa && perfil !== 'campo'
    && altura >= 5 && largura >= 7 && (perfil === 'industrial' ? sorte > 0.86 : sorte > 0.64);

  if (querCasa) {
    const h = limitar(altura, 2.8, 4.2);
    adicionar(mundo, {
      tipo: 'casa', malha: modelos.casa(largura, h, profundidade, cor, semente),
      x, z, guinada: guinadaFixa ?? orientar(sortear, largura, profundidade),
      raio: Math.hypot(largura, profundidade) / 2 + 1,
    }, {
      largura, comprimento: profundidade, solido: true, altura: h + 1.6,
      guinada: guinadaFixa,
    });
    return;
  }
  if (querLoja) {
    adicionar(mundo, {
      tipo: 'loja', malha: modelos.loja(largura, altura, profundidade, cor, semente),
      x, z, guinada: guinadaFixa ?? orientar(sortear, largura, profundidade),
      raio: Math.hypot(largura, profundidade) / 2 + 1,
    }, {
      largura, comprimento: profundidade, solido: true, altura,
      guinada: guinadaFixa,
    });
    return;
  }
  adicionar(mundo, {
    tipo: 'predio', malha: modelos.predio(largura, altura, profundidade, cor, semente),
    x, z, guinada: guinadaFixa ?? 0, raio: Math.hypot(largura, profundidade) / 2 + 1,
  }, {
    largura, comprimento: profundidade, solido: true, altura,
    guinada: guinadaFixa,
  });
}

/**
 * Uma fileira de construções encostada num lado do quarteirão, de frente
 * para a rua daquele lado.
 *
 * É assim que uma rua de cidade é feita: fachada, fresta, fachada. O miolo do
 * quarteirão fica vazio de propósito — ninguém vê o miolo de dentro do carro,
 * e enchê-lo só custa polígono.
 *
 * `quadra.x0/x1/z0/z1` são a borda do ASFALTO. A calçada vai dali para dentro
 * do quarteirão, então a fachada começa em `x0 + calcada`.
 */
function fileiraDeFachadas(mundo, sortear, cenario, quadra, lado, densidade) {
  const c = mundo.calcada;
  const aoLongoDeZ = lado === 'oeste' || lado === 'leste';
  const inicio = aoLongoDeZ ? quadra.z0 : quadra.x0;
  const fim = aoLongoDeZ ? quadra.z1 : quadra.x1;
  const fundo = aoLongoDeZ ? quadra.x1 - quadra.x0 : quadra.z1 - quadra.z0;
  const profundidadeMax = fundo / 2 - c - 0.5;
  if (fim - inicio < 11 || profundidadeMax < 4.5) return;

  // Para onde a frente aponta. A malha nasce virada para -Z, e
  // `frente(g) = (-sen g, -cos g)`.
  const guinada = { norte: 0, sul: Math.PI, oeste: Math.PI / 2, leste: -Math.PI / 2 }[lado];
  const alturaMax = cenario.perfil === 'industrial' ? 11
    : cenario.perfil === 'campo' ? 7 : 26;

  let t = inicio + entre(sortear, 0.5, 2.5);
  while (t < fim - 6) {
    const frente = Math.min(entre(sortear, 8, 17), fim - 1 - t);
    if (frente < 6) break;
    const profundidade = limitar(entre(sortear, 8, 15), 5, profundidadeMax);
    const altura = Math.round(entre(sortear, 5, alturaMax));

    // O centro fica recuado da borda do asfalto pela calçada mais meia
    // profundidade — é isso que encosta a fachada na calçada.
    const recuo = c + profundidade / 2;
    const meio = t + frente / 2;
    const x = aoLongoDeZ ? (lado === 'oeste' ? quadra.x0 + recuo : quadra.x1 - recuo) : meio;
    const z = aoLongoDeZ ? meio : (lado === 'norte' ? quadra.z0 + recuo : quadra.z1 - recuo);

    // Largura da malha é o que se vê da rua; profundidade é o que entra no
    // quarteirão. Girada, a caixa de colisão acompanha porque ela também
    // guarda a guinada.
    if (!colide(mundo.colisores, x, z,
      aoLongoDeZ ? profundidade + 1 : frente + 1,
      aoLongoDeZ ? frente + 1 : profundidade + 1)) {
      if (sortear() < 0.10 * densidade) {
        // Um lote vago de vez em quando, com um muro baixo. Fachada contínua
        // sem nenhum buraco vira parede de corredor e cansa.
        muroDeLote(mundo, x, z, frente, profundidade, guinada, aoLongoDeZ);
      } else {
        construir(mundo, sortear, cenario, x, z, frente, altura, profundidade, guinada);
      }
    }
    t += frente + entre(sortear, 0.6, 3.0);
  }
}

/** Terreno vago: um muro baixo na testada, e mato atrás. */
function muroDeLote(mundo, x, z, frente, profundidade, guinada, aoLongoDeZ) {
  const dx = aoLongoDeZ ? -Math.sin(guinada) : 0;
  const dz = aoLongoDeZ ? 0 : -Math.cos(guinada);
  adicionar(mundo, {
    tipo: 'muro-lote', malha: modelos.muro(frente, 1.5, CENA.muro),
    x: x + dx * (profundidade / 2 - 0.2), z: z + dz * (profundidade / 2 - 0.2),
    guinada, raio: frente,
  }, {
    // Mesma guinada da fachada: a malha do muro corre no eixo X, e girá-la
    // pela guinada da construção já a deixa paralela à testada. Somar mais um
    // quarto de volta punha o muro atravessado na rua.
    largura: frente, comprimento: 0.5, guinada,
    solido: true, parede: true, altura: 1.5,
  });
}

/**
 * Casa e loja têm FRENTE, e a frente precisa dar para alguma rua.
 *
 * Mas o giro de um quarto de volta só é seguro em lote quase quadrado: num
 * lote de 7 por 18, virar 90° joga dezoito metros de casa para dentro da
 * pista. Em lote comprido, portanto, só meia volta — que mantém a pegada
 * exatamente igual e ainda assim vira a fachada para o outro lado.
 */
function orientar(sortear, largura, profundidade) {
  const quadrado = Math.abs(largura - profundidade) < 1.6;
  if (!quadrado) return sortear() < 0.5 ? 0 : Math.PI;
  return Math.floor(sortear() * 4) * (Math.PI / 2);
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
        : modelos.muro(comprimento, 2.4,
          cenario.perfil === 'industrial' ? CENA.muroIndustrial : CENA.muro);
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

  // Num circuito a largada é na pista, apontando para onde ela segue.
  if (mundo.aneis.length) {
    const anel = mundo.aneis[0];
    // Logo ANTES da linha: assim a primeira passagem pela linha já é a largada
    // de verdade, e não uma volta contada de graça.
    const a = mundo.largada ? mundo.largada.angulo - 0.07 : entre(sortear, 0, TAU);
    const faixa = anel.raio + anel.largura * 0.22;
    // Seguindo o anel no sentido de `a` crescente, a tangente é
    // (-sen a, cos a). Com frente(ang) = (-sen ang, -cos ang), isso dá
    // ang = PI - a.
    return {
      x: anel.x + Math.cos(a) * faixa,
      z: anel.z + Math.sin(a) * faixa,
      angulo: Math.PI - a,
    };
  }
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

/**
 * Cabe aqui? Teste grosseiro de caixa contra caixa, mas que respeita o quarto
 * de volta.
 *
 * Sem olhar a guinada, um prédio de 8 por 15 girado 90° era medido como 8 de
 * largura quando na verdade ocupava 15 — e as esquinas do quarteirão, onde a
 * fileira de um lado encontra a do outro, empilhavam duas construções no mesmo
 * lugar. Como toda guinada de construção aqui é múltipla de 90°, basta trocar
 * largura por comprimento quando ela está de lado.
 */
function colide(colisores, x, z, largura, comprimento) {
  for (const c of colisores) {
    const deLado = Math.abs(Math.sin(c.guinada || 0)) > 0.5;
    const cl = deLado ? c.comprimento : c.largura;
    const cc = deLado ? c.largura : c.comprimento;
    if (Math.abs(c.x - x) < (cl + largura) / 2
      && Math.abs(c.z - z) < (cc + comprimento) / 2) return true;
  }
  return false;
}

/** Que piso tem embaixo do carro — decide aderência e barulho de pneu. */
export function pisoEm(mundo, x, z) {
  // Anel e rotatória vêm antes da grade: onde eles passam, mandam eles.
  for (const anel of mundo.aneis) {
    const d = Math.hypot(x - anel.x, z - anel.z);
    if (Math.abs(d - anel.raio) <= anel.largura / 2) return 'asfalto';
  }
  for (const r of mundo.rotatorias) {
    const d = Math.hypot(x - r.x, z - r.z);
    if (d <= r.raio + r.largura / 2) {
      // Dentro do meio-fio é a ilha: jardim, não pista.
      return d < r.raio - r.largura / 2 ? 'grama' : 'asfalto';
    }
  }
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
