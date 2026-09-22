// Os modelos.
//
// Carro e cenário são montados com bloco, bloco afunilado e cilindro — o mesmo
// tipo de modelo do jogo de referência. Sem textura, sem malha importada: as
// proporções fazem todo o trabalho, e é por isso que dá para reconhecer um
// fusca, uma picape e um furgão à distância mesmo sendo tudo caixa.

import { Construtor } from './malha.js';
import { criarSorteio, entre, inteiro, misturarCor, tonalizar } from '../nucleo/matematica.js';
import {
  CARRO, PREDIO, VEGETACAO, CENA, FACHADAS, FACHADAS_INDUSTRIAIS,
} from './paleta.js';

const VIDRO = CARRO.vidro;
const VIDRO_CLARO = CARRO.vidroClaro;
const BORRACHA = CARRO.borracha;
const ARO = CARRO.aro;
const CROMO = CARRO.cromo;
const FAROL = CARRO.farol;
const LANTERNA = CARRO.lanterna;

const cache = new Map();

function memo(chave, fabrica) {
  let m = cache.get(chave);
  if (!m) { m = fabrica(); cache.set(chave, m); }
  return m;
}

// ---------------------------------------------------------------------------
// CARRO
// ---------------------------------------------------------------------------

/**
 * Monta as peças de um carro: carroceria, roda e os pontos onde ficam as luzes.
 * Devolve tudo no referencial do carro — X direita, Y para cima a partir do
 * chão, Z para trás (a frente aponta para -Z).
 */
export function construirCarro(modelo, corAlternativa) {
  const chave = `carro:${modelo.id}:${corAlternativa ?? 'padrao'}`;
  return memo(chave, () => montarCarro(modelo, corAlternativa));
}

function montarCarro(modelo, corAlternativa) {
  const c = corAlternativa === undefined
    ? modelo.corpo
    : { ...modelo.corpo, cor: corAlternativa };
  const f = modelo.ficha;
  const L = f.comprimento, A = f.largura;
  const frente = -L / 2, tras = L / 2;
  const meiaA = A / 2;

  const cor = c.cor;
  const escuro = tonalizar(cor, 0.72);
  const claro = tonalizar(cor, 1.12);
  const baixo = c.alturaChassi;
  const topoCorpo = baixo + c.alturaCorpo;

  const capo = c.capo ?? 0.9;
  const bagageiro = c.cacamba ? 0 : (c.bagageiro ?? 0.6);
  const cabineZ0 = frente + capo;
  const cabineZ1 = c.cacamba ? cabineZ0 + (L - capo - c.cacamba) : tras - bagageiro;
  const alturaCabine = c.alturaCabine;
  const topoCabine = topoCorpo + alturaCabine;
  const recuo = c.recuoCabine ?? 0.1;

  const b = new Construtor();

  // Chassi: a faixa escura que faz o carro parecer apoiado, não flutuando.
  b.caixa(0, baixo * 0.55, 0, A * 0.94, baixo * 1.1, L * 0.92, tonalizar(cor, 0.35));

  if (c.tipo === 'van') {
    // Furgão: uma caixa só, capô curtinho e pára-brisa quase em pé.
    b.tronco(0, (baixo + topoCorpo + alturaCabine) / 2, (cabineZ0 + tras) / 2,
      A, c.alturaCorpo + alturaCabine, (tras - cabineZ0),
      A * 0.96, (tras - cabineZ0) * 0.97, 0, 0.02, cor,
      { cores: { topo: claro, frente: claro } });
    b.tronco(0, baixo + c.alturaCorpo * 0.5, frente + capo * 0.5,
      A * 0.97, c.alturaCorpo, capo, A * 0.9, capo * 0.8, 0, 0.06, cor);
    // pára-brisa deitado por cima do capô
    b.tronco(0, topoCorpo + alturaCabine * 0.5, cabineZ0 - 0.02,
      A * 0.9, alturaCabine * 1.2, 0.34, A * 0.86, 0.30, 0, 0.24, VIDRO,
      { cores: { frente: VIDRO_CLARO } });
    janelasLaterais(b, meiaA, topoCorpo + alturaCabine * 0.15, alturaCabine * 0.95,
      cabineZ0 + 0.15, cabineZ0 + 1.9);
  } else if (c.tipo === 'picape') {
    // Cabine dupla + caçamba aberta.
    b.caixa(0, (baixo + topoCorpo) / 2, (frente + cabineZ1) / 2,
      A, c.alturaCorpo, cabineZ1 - frente, cor, { cores: { topo: claro } });
    b.tronco(0, (topoCorpo + topoCabine) / 2, (cabineZ0 + cabineZ1) / 2,
      A - recuo, alturaCabine, cabineZ1 - cabineZ0,
      A - recuo * 2.2, (cabineZ1 - cabineZ0) * 0.74, 0, 0.06, cor,
      { cores: { topo: claro, frente: VIDRO, tras: VIDRO } });
    janelasLaterais(b, meiaA - recuo * 0.5, topoCorpo + alturaCabine * 0.12,
      alturaCabine * 0.7, cabineZ0 + 0.14, cabineZ1 - 0.16);
    // caçamba: assoalho e as quatro bordas
    const cz0 = cabineZ1, cz1 = tras;
    b.caixa(0, topoCorpo - 0.05, (cz0 + cz1) / 2, A * 0.98, 0.1, cz1 - cz0, tonalizar(cor, 0.55));
    b.caixa(0, topoCorpo + 0.16, cz1 - 0.06, A * 0.98, 0.42, 0.12, escuro);
    b.caixa(-meiaA + 0.06, topoCorpo + 0.16, (cz0 + cz1) / 2, 0.12, 0.42, cz1 - cz0, cor);
    b.caixa(meiaA - 0.06, topoCorpo + 0.16, (cz0 + cz1) / 2, 0.12, 0.42, cz1 - cz0, cor);
  } else if (c.tipo === 'perua') {
    // Perua: sedã que não termina. O teto segue reto até a traseira e cai de
    // uma vez — é a linha do teto comprido que faz a silhueta, não o tamanho.
    b.tronco(0, baixo + c.alturaCorpo / 2, frente + capo / 2,
      A * 0.97, c.alturaCorpo, capo, A * 0.92, capo * 0.9, 0, 0.04, cor,
      { cores: { topo: claro } });
    b.caixa(0, (baixo + topoCorpo) / 2, (cabineZ0 + tras) / 2,
      A, c.alturaCorpo, tras - cabineZ0, cor, { cores: { topo: tonalizar(cor, 0.9) } });
    const fimDoTeto = tras - 0.10;
    b.tronco(0, (topoCorpo + topoCabine) / 2, (cabineZ0 + fimDoTeto) / 2,
      A - recuo, alturaCabine, fimDoTeto - cabineZ0,
      A - recuo * 2.1, (fimDoTeto - cabineZ0) * 0.88, 0, 0.14, cor,
      { cores: { topo: claro, frente: VIDRO, tras: VIDRO } });
    // Rack de teto: duas longarinas. É o que diz "perua" antes de qualquer coisa.
    for (const lado of [-1, 1]) {
      b.caixa(lado * (A * 0.5 - recuo * 1.5), topoCabine + 0.04,
        (cabineZ0 + fimDoTeto) / 2 + 0.22, 0.05, 0.06, (fimDoTeto - cabineZ0) * 0.7,
        tonalizar(cor, 0.35));
    }
    janelasLaterais(b, meiaA - recuo * 0.45, topoCorpo + alturaCabine * 0.1,
      alturaCabine * 0.72, cabineZ0 + 0.16, fimDoTeto - 0.16);
  } else if (c.tipo === 'cupe') {
    // Cupê: capô comprido, cabine atrasada e o teto descendo direto na tampa —
    // a rabeta inteira é uma reta só, que é o que define um fastback.
    b.tronco(0, baixo + c.alturaCorpo * 0.94 / 2, frente + capo / 2,
      A * 0.97, c.alturaCorpo * 0.94, capo, A * 0.93, capo * 0.88, 0, 0.05, cor,
      { cores: { topo: claro } });
    b.caixa(0, (baixo + topoCorpo) / 2, (cabineZ0 + cabineZ1) / 2,
      A, c.alturaCorpo, cabineZ1 - cabineZ0, cor, { cores: { topo: tonalizar(cor, 0.9) } });
    b.tronco(0, baixo + c.alturaCorpo / 2, tras - bagageiro / 2,
      A * 0.97, c.alturaCorpo, bagageiro, A * 0.94, bagageiro * 0.9, 0, -0.04, cor,
      { cores: { topo: claro } });
    // O teto: estreito em cima e ATRASADO, então a linha de trás vira rampa.
    b.tronco(0, (topoCorpo + topoCabine) / 2, (cabineZ0 + cabineZ1) / 2,
      A - recuo, alturaCabine, (cabineZ1 - cabineZ0) * 1.24,
      A - recuo * 3.1, (cabineZ1 - cabineZ0) * 0.40, 0, 0.30, cor,
      { cores: { topo: claro, frente: VIDRO, tras: VIDRO } });
    // Faixa de capô, de ponta a ponta. Cupê sem faixa é sedã de duas portas.
    if (c.faixa) {
      for (const lado of [-1, 1]) {
        b.placa(lado * A * 0.10, topoCorpo + 0.002, (frente + cabineZ0) / 2,
          A * 0.11, capo * 0.88, c.corSecundaria);
        b.placa(lado * A * 0.10, topoCorpo + 0.002, tras - bagageiro / 2,
          A * 0.11, bagageiro * 0.8, c.corSecundaria);
      }
    }
    janelasLaterais(b, meiaA - recuo * 0.45, topoCorpo + alturaCabine * 0.12,
      alturaCabine * 0.62, cabineZ0 + 0.20, cabineZ1 - 0.10);
  } else if (c.tipo === 'caminhao') {
    // Caminhãozinho: cabine em cima do motor, baú atrás, e uma folga entre os
    // dois. A folga é o detalhe — sem ela é um furgão, com ela é um caminhão.
    const fimCabine = frente + capo + 1.30;
    b.tronco(0, (baixo + topoCorpo + alturaCabine) / 2, (frente + fimCabine) / 2,
      A * 0.98, c.alturaCorpo + alturaCabine, fimCabine - frente,
      A * 0.94, (fimCabine - frente) * 0.94, 0, 0.04, cor,
      { cores: { topo: claro } });
    b.tronco(0, topoCorpo + alturaCabine * 0.62, frente + capo * 0.7,
      A * 0.88, alturaCabine * 0.78, 0.26, A * 0.84, 0.22, 0, 0.20, VIDRO,
      { cores: { frente: VIDRO_CLARO } });
    janelasLaterais(b, meiaA, topoCorpo + alturaCabine * 0.22,
      alturaCabine * 0.72, frente + capo * 0.85, fimCabine - 0.18);
    // O baú, mais alto que a cabine e recuado.
    const bau0 = fimCabine + 0.16;
    const alturaBau = c.alturaBau || 1.5;
    b.caixa(0, baixo + 0.12 + alturaBau / 2, (bau0 + tras) / 2,
      A * 0.99, alturaBau, tras - bau0, c.corSecundaria,
      { cores: { topo: tonalizar(c.corSecundaria, 1.14) } });
    // Quinas e travessas do baú, que é o que tira a cara de tijolo.
    for (const t of [0.3, 0.55, 0.8]) {
      const z = bau0 + (tras - bau0) * t;
      for (const lado of [-1, 1]) {
        b.caixa(lado * (A * 0.495 + 0.004), baixo + 0.12 + alturaBau / 2, z,
          0.01, alturaBau * 0.94, 0.06, tonalizar(c.corSecundaria, 0.72));
      }
    }
    b.caixa(0, baixo + 0.10, (bau0 + tras) / 2, A * 0.99, 0.09, tras - bau0,
      tonalizar(c.corSecundaria, 0.5));
  } else if (c.tipo === 'buggy') {
    // Buggy: banheira baixa e santo antônio de cano.
    b.tronco(0, (baixo + topoCorpo) / 2, 0, A * 0.92, c.alturaCorpo, L * 0.94,
      A, L * 0.88, 0, 0, cor, { cores: { topo: tonalizar(cor, 0.5) } });
    b.caixa(0, topoCorpo + 0.14, cabineZ1 - 0.1, A * 0.66, 0.3, 0.5, c.corSecundaria);
    if (c.santoAntonio) {
      const alturaBarra = topoCorpo + 0.92;
      for (const lado of [-1, 1]) {
        b.caixa(lado * (meiaA - 0.12), (topoCorpo + alturaBarra) / 2, cabineZ1 - 0.35,
          0.09, alturaBarra - topoCorpo, 0.09, 0x3a3f45);
        b.caixa(lado * (meiaA - 0.12), (topoCorpo + alturaBarra) / 2 - 0.1, cabineZ0 + 0.1,
          0.08, alturaBarra - topoCorpo - 0.2, 0.08, 0x3a3f45);
      }
      b.caixa(0, alturaBarra, cabineZ1 - 0.35, A - 0.24, 0.09, 0.09, 0x3a3f45);
      b.caixa(0, alturaBarra - 0.06, (cabineZ0 + cabineZ1) / 2, A - 0.26, 0.08, 0.08, 0x3a3f45);
    }
    // pára-brisa pequeno e inclinado
    b.tronco(0, topoCorpo + 0.26, cabineZ0 + 0.04, A * 0.74, 0.5, 0.06,
      A * 0.72, 0.06, 0, 0.16, VIDRO, { cores: { frente: VIDRO_CLARO } });
  } else {
    // Perfil comum: capô, corpo, cabine afunilada e traseira.
    const alturaCapo = c.tipo === 'esportivo' ? c.alturaCorpo * 0.86 : c.alturaCorpo;
    b.tronco(0, baixo + alturaCapo / 2, frente + capo / 2,
      A * 0.97, alturaCapo, capo, A * 0.92, capo * 0.9, 0, 0.04, cor,
      { cores: { topo: claro } });

    b.caixa(0, (baixo + topoCorpo) / 2, (cabineZ0 + cabineZ1) / 2,
      A, c.alturaCorpo, cabineZ1 - cabineZ0, cor, { cores: { topo: tonalizar(cor, 0.9) } });

    if (c.tipo === 'fusca') {
      // O fusca é redondo: a traseira sobe, não é um degrau.
      b.tronco(0, baixo + c.alturaCorpo * 0.55, tras - bagageiro / 2,
        A * 0.95, c.alturaCorpo * 1.1, bagageiro, A * 0.78, bagageiro * 0.7, 0, -0.1, cor,
        { cores: { topo: claro } });
      b.tronco(0, (topoCorpo + topoCabine) / 2, (cabineZ0 + cabineZ1) / 2,
        A - recuo, alturaCabine, (cabineZ1 - cabineZ0) * 1.05,
        A - recuo * 3, (cabineZ1 - cabineZ0) * 0.52, 0, 0.04, cor,
        { cores: { topo: claro, frente: VIDRO, tras: VIDRO } });
    } else {
      const alturaTras = c.tipo === 'hatch' ? c.alturaCorpo + alturaCabine * 0.55 : c.alturaCorpo;
      b.tronco(0, baixo + alturaTras / 2, tras - bagageiro / 2,
        A * 0.97, alturaTras, bagageiro, A * 0.93, bagageiro * 0.86, 0, -0.03, cor,
        { cores: { topo: claro } });
      const estreitaCima = c.tipo === 'esportivo' ? recuo * 3.4 : recuo * 2.4;
      b.tronco(0, (topoCorpo + topoCabine) / 2, (cabineZ0 + cabineZ1) / 2,
        A - recuo, alturaCabine, cabineZ1 - cabineZ0,
        A - estreitaCima, (cabineZ1 - cabineZ0) * (c.tipo === 'esportivo' ? 0.46 : 0.62),
        0, c.tipo === 'esportivo' ? 0.16 : 0.10, cor,
        { cores: { topo: claro, frente: VIDRO, tras: VIDRO } });
    }
    janelasLaterais(b, meiaA - recuo * 0.45, topoCorpo + alturaCabine * 0.1,
      alturaCabine * 0.72, cabineZ0 + 0.16, cabineZ1 - 0.18);
  }

  // Pára-choques.
  const corChoque = c.tipo === 'fusca' || c.tipo === 'sedan' ? CROMO : tonalizar(cor, 0.45);
  b.caixa(0, baixo + 0.13, frente + 0.07, A * 0.99, 0.22, 0.16, corChoque);
  b.caixa(0, baixo + 0.13, tras - 0.07, A * 0.99, 0.22, 0.16, corChoque);

  // Faróis e lanternas. São faces que ignoram a luz: acendem sozinhas.
  const yFarol = baixo + c.alturaCorpo * 0.62;
  const yLanterna = baixo + c.alturaCorpo * 0.66;
  for (const lado of [-1, 1]) {
    // Moldura antes da lente: é a moldura que dá profundidade ao farol e o
    // separa da lataria. Sem ela o farol é um adesivo.
    b.painel(lado * (meiaA - 0.30), yFarol, frente - 0.003, 0.40, 0.25, tonalizar(cor, 0.34));
    b.painel(lado * (meiaA - 0.30), yFarol, frente - 0.007, 0.34, 0.20, FAROL, { semLuz: true });
    b.painel(lado * (meiaA - 0.26), yLanterna, tras + 0.003, 0.36, 0.25, tonalizar(cor, 0.34));
    b.painel(lado * (meiaA - 0.26), yLanterna, tras + 0.007, 0.30, 0.20, LANTERNA, { semLuz: true });
  }

  const geo = {
    A, meiaA, L, frente, tras, baixo, topoCorpo, alturaCabine, cabineZ0, cabineZ1,
  };
  frenteDoCarro(b, c, cor, geo);
  trasDoCarro(b, c, cor, geo);
  flancoDoCarro(b, c, f, cor, geo);

  // Retrovisores: a carcaça e o braço que a prende. O braço é meio centímetro
  // de nada e é ele que tira o espelho de flutuando ao lado da porta.
  for (const lado of [-1, 1]) {
    const zEspelho = c.tipo === 'van' || c.tipo === 'picape' ? cabineZ0 + 0.22 : cabineZ0 + 0.3;
    const yEspelho = topoCorpo + alturaCabine * 0.42;
    b.caixa(lado * (meiaA + 0.03), yEspelho - 0.02, zEspelho + 0.05, 0.08, 0.035, 0.035, escuro);
    b.caixa(lado * (meiaA + 0.10), yEspelho, zEspelho, 0.07, 0.11, 0.13, escuro);
    b.painel(lado * (meiaA + 0.10), yEspelho, zEspelho + 0.068, 0.05, 0.08, CARRO.vidroClaro);
  }

  if (c.asa) {
    const zAsa = tras - 0.18;
    b.caixa(0, topoCorpo + 0.30, zAsa, A * 0.88, 0.06, 0.26, c.corSecundaria);
    for (const lado of [-1, 1]) {
      b.caixa(lado * A * 0.34, topoCorpo + 0.17, zAsa, 0.06, 0.28, 0.14, c.corSecundaria);
    }
  }

  const carroceria = b.terminar();
  const roda = construirRoda(c.raioRoda, c.larguraRoda, modelo.id);

  const eixo = f.entreEixos / 2;
  const xRoda = meiaA - c.larguraRoda * 0.5 + 0.02;

  return {
    carroceria,
    roda,
    raio: Math.max(L, A) * 0.62,
    rodas: [
      { x: -xRoda, z: -eixo, dianteira: true },
      { x: xRoda, z: -eixo, dianteira: true },
      { x: -xRoda, z: eixo, dianteira: false },
      { x: xRoda, z: eixo, dianteira: false },
    ],
    raioRoda: c.raioRoda,
    farois: [
      { x: -(meiaA - 0.30), y: yFarol, z: frente - 0.05 },
      { x: meiaA - 0.30, y: yFarol, z: frente - 0.05 },
    ],
    lanternas: [
      { x: -(meiaA - 0.26), y: baixo + c.alturaCorpo * 0.66, z: tras + 0.05 },
      { x: meiaA - 0.26, y: baixo + c.alturaCorpo * 0.66, z: tras + 0.05 },
    ],
    escapamento: { x: meiaA * 0.55, y: baixo * 0.6, z: tras + 0.02 },
    dimensoes: { comprimento: L, largura: A, altura: topoCabine },
  };
}

// Três passadas de detalhe que valem para TODOS os carros.
//
// Cada uma custa meia dúzia de faces e resolve o mesmo problema: sem elas o
// carro é um bloco pintado, e bloco pintado é o que denuncia jogo improvisado.
// O truque é sempre o mesmo — uma lasca fina POR FORA da superfície, a três
// milésimos de distância, que o ordenador de profundidade desenha por cima.

/** A cara: grade rebaixada, barras e a tomada de ar de baixo. */
function frenteDoCarro(b, c, cor, g) {
  if (c.tipo === 'buggy') return;
  const y = g.baixo + c.alturaCorpo * 0.36;
  const largura = g.A * (c.tipo === 'van' ? 0.50 : 0.44);

  // O fundo preto da grade. Rebaixado de verdade — é uma caixa curta para
  // dentro do capô, então de lado dá para ver que ela afunda.
  b.caixa(0, y, g.frente + 0.04, largura, 0.16, 0.08, 0x15181c);
  const barras = c.tipo === 'picape' || c.tipo === 'van' ? 2 : 3;
  for (let i = 0; i < barras; i++) {
    const yb = y - 0.06 + (i / Math.max(1, barras - 1)) * 0.12;
    b.painel(0, yb, g.frente - 0.006, largura * 0.94, 0.022,
      c.tipo === 'sedan' || c.tipo === 'fusca' ? CROMO : tonalizar(cor, 0.52));
  }

  // Tomada de ar embaixo do pára-choque, e os dois piscas nos cantos.
  b.painel(0, g.baixo + 0.045, g.frente - 0.006, g.A * 0.52, 0.075, 0x1a1d21);
  for (const lado of [-1, 1]) {
    b.painel(lado * (g.meiaA - 0.07), g.baixo + c.alturaCorpo * 0.60, g.frente - 0.006,
      0.10, 0.11, 0xf0a83a, { semLuz: true });
  }
}

/** A traseira: placa com moldura, refletores e a ponta do escapamento. */
function trasDoCarro(b, c, cor, g) {
  b.painel(0, g.baixo + 0.30, g.tras + 0.004, 0.42, 0.17, tonalizar(cor, 0.34));
  b.painel(0, g.baixo + 0.30, g.tras + 0.008, 0.36, 0.13, CARRO.placa, { semLuz: true });
  for (const lado of [-1, 1]) {
    b.painel(lado * (g.meiaA - 0.16), g.baixo + 0.10, g.tras + 0.006, 0.09, 0.05, 0xb03028);
  }
  // A ponta do escapamento fica ENFIADA embaixo do pára-choque, não saindo
  // dele: escapamento que ultrapassa a traseira também ultrapassa a caixa de
  // colisão, e aí o carro bate com uma coisa que ninguém vê.
  if (c.tipo !== 'eletrico') {
    b.caixa(g.meiaA * 0.55, g.baixo * 0.62, g.tras - 0.03, 0.085, 0.085, 0.11, 0x53585e);
  }
}

/** O flanco: caixa de roda, vinco da porta e a saia embaixo. */
function flancoDoCarro(b, c, f, cor, g) {
  const r = c.raioRoda;
  const eixo = f.entreEixos / 2;
  const fora = g.meiaA + 0.006;
  const arco = tonalizar(cor, 0.44);

  // A caixa de roda é um CONTORNO, não um painel cheio.
  //
  // Painel cheio foi a primeira tentativa e estava errado: a roda é desenhada
  // por fora do flanco, mas o CENTRO dela fica para dentro do painel, e o
  // ordenador de profundidade compara centro com centro. Resultado: o painel
  // ganhava da roda e a roda de trás virava um buraco preto na lataria.
  //
  // Três barras finas que passam por FORA do pneu — duas colunas e o lábio em
  // cima — não encostam nele, então não há o que ordenar errado. E contorno é
  // o que o olho lê como arco de roda de qualquer jeito.
  // O arco acompanha o pneu, mas nunca sobe acima da lataria: num carro baixo
  // 2,06 raios passa do teto do corpo, e aí o contorno fica boiando no ar em
  // cima do para-lama. Quem manda é o menor dos dois.
  const topoArco = Math.min(r * 2.06, g.topoCorpo - 0.04);
  const baseArco = Math.min(r * 1.16, topoArco - 0.10);
  for (const z of [-eixo, eixo]) {
    for (const lado of [-1, 1]) {
      // Só os ombros do arco: a perna inteira até o chão vira duas barras
      // pretas ladeando a roda, que é pior que não ter arco nenhum.
      for (const ponta of [-1, 1]) {
        b.caixa(lado * fora, (baseArco + topoArco) / 2, z + ponta * r * 1.06,
          0.008, topoArco - baseArco, 0.04, arco);
      }
      b.caixa(lado * fora, topoArco, z, 0.008, 0.042, r * 2.12, arco);
      // O lábio do para-lama, pousado na borda de cima do arco.
      b.caixa(lado * (g.meiaA + 0.018), topoArco, z, 0.042, 0.045, r * 1.60,
        tonalizar(cor, 0.84));
    }
  }

  const meioCabine = (g.cabineZ0 + g.cabineZ1) / 2;
  // O vinco passa da cabine para o capô e para a traseira, mas PARA na
  // lataria: linha que vaza para fora do carro estica a caixa do modelo, e a
  // caixa do modelo é o que a colisão e a sombra usam.
  const vincoZ0 = Math.max(g.frente + 0.18, g.cabineZ0 - 0.35);
  const vincoZ1 = Math.min(g.tras - 0.18, g.cabineZ1 + 0.35);
  const cromada = c.tipo === 'fusca' || c.tipo === 'sedan';
  for (const lado of [-1, 1]) {
    // Vinco da porta, na altura do ombro: a linha que quebra a chapa lisa.
    if (vincoZ1 - vincoZ0 > 0.4) {
      b.caixa(lado * fora, g.topoCorpo - 0.07, (vincoZ0 + vincoZ1) / 2,
        0.008, 0.028, vincoZ1 - vincoZ0, tonalizar(cor, 0.62));
    }
    // Saia: a faixa escura rente ao chão. É ela que assenta o carro.
    b.caixa(lado * (g.meiaA - 0.004), g.baixo + 0.045, 0,
      0.012, 0.09, g.L * 0.58, tonalizar(cor, 0.42));
    // A fresta entre as portas, para o flanco ter onde começar e onde acabar.
    if (c.tipo !== 'buggy' && g.cabineZ1 - g.cabineZ0 > 1.1) {
      b.caixa(lado * fora, g.topoCorpo - c.alturaCorpo * 0.34, meioCabine,
        0.008, c.alturaCorpo * 0.62, 0.018, tonalizar(cor, 0.52));
    }
    // Maçaneta.
    b.caixa(lado * (g.meiaA + 0.012), g.topoCorpo - 0.16, meioCabine - 0.40,
      0.03, 0.045, 0.16, cromada ? CROMO : tonalizar(cor, 0.5));
  }
}

/** A faixa de vidro dos dois lados — uma lasca fina por fora da lataria. */
function janelasLaterais(b, x, y, altura, z0, z1) {
  const centro = (z0 + z1) / 2;
  const comprimento = Math.max(0.2, z1 - z0);
  for (const lado of [-1, 1]) {
    b.caixa(lado * (x + 0.012), y + altura / 2, centro, 0.008, altura, comprimento, VIDRO);
  }
}

function construirRoda(raio, largura, chave) {
  return memo(`roda:${chave}`, () => {
    const b = new Construtor();
    b.cilindro(0, 0, 0, raio, largura, 12, BORRACHA, BORRACHA);
    b.cilindro(0, 0, 0, raio * 0.56, largura * 1.04, 10, ARO, tonalizar(ARO, 1.1));
    // três raios grossos, para dar a entender que a roda gira
    for (let i = 0; i < 3; i++) {
      const a = (i / 3) * Math.PI * 2;
      const y = Math.sin(a) * raio * 0.33;
      const z = Math.cos(a) * raio * 0.33;
      b.caixa(0, y, z, largura * 1.06, raio * 0.16, raio * 0.16, tonalizar(ARO, 0.8));
    }
    return b.terminar();
  });
}

// ---------------------------------------------------------------------------
// CENÁRIO
// ---------------------------------------------------------------------------

export function predio(largura, altura, profundidade, cor, semente) {
  return memo(`predio:${largura}:${altura}:${profundidade}:${cor}:${semente}`, () => {
    const b = new Construtor();
    const sortear = criarSorteio(semente);
    b.caixa(0, altura / 2, 0, largura, altura, profundidade, cor, {
      cores: { topo: tonalizar(cor, PREDIO.topo) },
    });

    // Janelas nas QUATRO faces. Só na frente e no fundo, a lateral do prédio
    // vira uma parede lisa — e é justamente a lateral que se vê da rua.
    const andares = Math.max(1, Math.floor(altura / 3.1));
    const corJanela = PREDIO.janela;
    const fileira = (extensao, colocar) => {
      const colunas = Math.max(1, Math.floor(extensao / 2.2));
      const passo = extensao / colunas;
      for (let a = 0; a < andares; a++) {
        const y = 1.7 + a * 3.1;
        if (y > altura - 1.1) break;
        for (let j = 0; j < colunas; j++) {
          const t = -extensao / 2 + (j + 0.5) * passo;
          const acesa = sortear() > 0.68;
          const tom = acesa ? misturarCor(corJanela, PREDIO.janelaAcesa, 0.85) : corJanela;
          colocar(t, y, passo * 0.52, tom, acesa);
        }
      }
    };
    fileira(largura, (t, y, l, tom, acesa) => {
      b.painel(t, y, -profundidade / 2 - 0.02, l, 1.3, tom, { semLuz: acesa });
      b.painel(t, y, profundidade / 2 + 0.02, l, 1.3, tom, { semLuz: acesa });
    });
    fileira(profundidade, (t, y, l, tom, acesa) => {
      janelaLateral(b, -largura / 2 - 0.02, y, t, l, tom, acesa);
      janelaLateral(b, largura / 2 + 0.02, y, t, l, tom, acesa);
    });
    // Platibanda, para o prédio não terminar num corte seco.
    b.caixa(0, altura + 0.18, 0, largura * 1.04, 0.36, profundidade * 1.04, tonalizar(cor, PREDIO.platibanda));
    return b.terminar();
  });
}

/** Janela numa face voltada para ±X (a lateral do prédio). */
function janelaLateral(b, x, y, z, comprimento, cor, acesa) {
  b.caixa(x, y, z, 0.03, 1.3, comprimento, cor, { semLuz: acesa });
}

export function arvore(altura, semente) {
  return memo(`arvore:${altura.toFixed(2)}:${semente}`, () => {
    const b = new Construtor();
    const sortear = criarSorteio(semente);
    const tronco = altura * 0.36;
    b.caixa(0, tronco / 2, 0, 0.22, tronco, 0.22, VEGETACAO.tronco);
    const verde = misturarCor(VEGETACAO.folhagemEscura, VEGETACAO.folhagemClara, sortear());
    const copa = altura - tronco;
    b.tronco(0, tronco + copa * 0.32, 0, altura * 0.72, copa * 0.64, altura * 0.72,
      altura * 0.48, altura * 0.48, 0, 0, verde, { cores: { topo: tonalizar(verde, 1.18) } });
    b.tronco(0, tronco + copa * 0.76, 0, altura * 0.46, copa * 0.44, altura * 0.46,
      altura * 0.12, altura * 0.12, 0, 0, tonalizar(verde, 1.1));
    return b.terminar();
  });
}

export function palmeira(altura, semente) {
  return memo(`palmeira:${altura.toFixed(2)}:${semente}`, () => {
    const b = new Construtor();
    const sortear = criarSorteio(semente);
    const tronco = altura * 0.78;
    b.tronco(0, tronco / 2, 0, 0.30, tronco, 0.30, 0.20, 0.20,
      entre(sortear, -0.25, 0.25), entre(sortear, -0.2, 0.2), VEGETACAO.troncoPalmeira);
    const verde = VEGETACAO.palmeira;
    for (let i = 0; i < 7; i++) {
      const a = (i / 7) * Math.PI * 2 + sortear() * 0.3;
      const comp = altura * 0.32;
      b.caixa(Math.cos(a) * comp * 0.5, tronco + 0.06 - i % 2 * 0.12, Math.sin(a) * comp * 0.5,
        comp * (Math.abs(Math.cos(a)) + 0.25), 0.07, comp * (Math.abs(Math.sin(a)) + 0.25),
        tonalizar(verde, 0.85 + (i % 3) * 0.12));
    }
    b.caixa(0, tronco + 0.12, 0, 0.34, 0.24, 0.34, VEGETACAO.tronco);
    return b.terminar();
  });
}

export function arbusto(raio, semente) {
  return memo(`arbusto:${raio.toFixed(2)}:${semente}`, () => {
    const b = new Construtor();
    const sortear = criarSorteio(semente);
    const verde = misturarCor(VEGETACAO.folhagemEscura, VEGETACAO.folhagemClara, sortear());
    for (let i = 0; i < 3; i++) {
      b.caixa(entre(sortear, -raio * 0.4, raio * 0.4), raio * entre(sortear, 0.4, 0.75),
        entre(sortear, -raio * 0.4, raio * 0.4),
        raio * entre(sortear, 0.8, 1.3), raio * entre(sortear, 0.8, 1.2),
        raio * entre(sortear, 0.8, 1.3), tonalizar(verde, 0.85 + i * 0.12));
    }
    return b.terminar();
  });
}

export function cone() {
  return memo('cone', () => {
    const b = new Construtor();
    b.caixa(0, 0.03, 0, 0.42, 0.06, 0.42, 0x2b2b2b);
    b.tronco(0, 0.22, 0, 0.30, 0.32, 0.30, 0.16, 0.16, 0, 0, CENA.cone);
    b.tronco(0, 0.48, 0, 0.16, 0.20, 0.16, 0.07, 0.07, 0, 0, CENA.cone);
    b.caixa(0, 0.37, 0, 0.19, 0.07, 0.19, CENA.coneFaixa);
    return b.terminar();
  });
}

export function poste(altura) {
  return memo(`poste:${altura}`, () => {
    const b = new Construtor();
    b.caixa(0, 0.10, 0, 0.36, 0.20, 0.36, tonalizar(CENA.poste, 0.8));
    b.caixa(0, altura / 2, 0, 0.16, altura, 0.16, CENA.poste);
    b.caixa(0.55, altura - 0.08, 0, 1.10, 0.13, 0.13, CENA.poste);
    b.caixa(1.05, altura - 0.22, 0, 0.52, 0.18, 0.26, tonalizar(CENA.poste, 0.55));
    b.placa(1.05, altura - 0.32, 0, 0.44, 0.22, CENA.lampada, { semLuz: true });
    return b.terminar();
  });
}

export function muro(comprimento, altura, cor) {
  return memo(`muro:${comprimento}:${altura}:${cor}`, () => {
    const b = new Construtor();
    b.caixa(0, altura / 2, 0, comprimento, altura, 0.26, cor, {
      cores: { topo: tonalizar(cor, 1.15) },
    });
    b.caixa(0, altura + 0.06, 0, comprimento, 0.12, 0.34, tonalizar(cor, 0.7));
    return b.terminar();
  });
}

export function grade(comprimento, altura) {
  return memo(`grade:${comprimento}:${altura}`, () => {
    const b = new Construtor();
    const cor = CENA.grade;
    b.caixa(0, altura - 0.06, 0, comprimento, 0.10, 0.09, cor);
    b.caixa(0, altura * 0.45, 0, comprimento, 0.08, 0.08, cor);
    const barras = Math.max(2, Math.round(comprimento / 0.55));
    for (let i = 0; i <= barras; i++) {
      const x = -comprimento / 2 + (i / barras) * comprimento;
      b.caixa(x, altura / 2, 0, 0.07, altura, 0.07, cor);
    }
    return b.terminar();
  });
}

/** Meio-fio: a guia baixa que cerca a ilha da rotatória. */
export function meioFio(comprimento) {
  return memo(`meioFio:${comprimento.toFixed(2)}`, () => {
    const b = new Construtor();
    b.caixa(0, 0.11, 0, 0.42, 0.22, comprimento, 0xdcd8cc,
      { cores: { topo: 0xf0ece0 } });
    b.caixa(0, 0.20, 0, 0.30, 0.06, comprimento, 0xd94434);
    return b.terminar();
  });
}

export function barreira(comprimento) {
  return memo(`barreira:${comprimento}`, () => {
    const b = new Construtor();
    b.tronco(0, 0.36, 0, 0.56, 0.72, comprimento, 0.30, comprimento, 0, 0, 0xf2eee4,
      { cores: { topo: 0xe2ded2 } });
    b.caixa(0, 0.5, 0, 0.34, 0.24, comprimento * 0.4, CENA.cone);
    return b.terminar();
  });
}

export function caixote(tamanho, cor) {
  return memo(`caixote:${tamanho}:${cor}`, () => {
    const b = new Construtor();
    b.caixa(0, tamanho / 2, 0, tamanho, tamanho, tamanho, cor, {
      cores: { topo: tonalizar(cor, 1.2) },
    });
    b.painel(0, tamanho / 2, -tamanho / 2 - 0.01, tamanho * 0.5, tamanho * 0.3, tonalizar(cor, 0.7));
    return b.terminar();
  });
}

export function barril(cor) {
  return memo(`barril:${cor}`, () => {
    const b = new Construtor();
    b.tronco(0, 0.44, 0, 0.56, 0.88, 0.56, 0.56, 0.56, 0, 0, cor,
      { cores: { topo: tonalizar(cor, 1.25) } });
    b.caixa(0, 0.26, 0, 0.60, 0.07, 0.60, tonalizar(cor, 0.65));
    b.caixa(0, 0.62, 0, 0.60, 0.07, 0.60, tonalizar(cor, 0.65));
    return b.terminar();
  });
}

export function hidrante() {
  return memo('hidrante', () => {
    const b = new Construtor();
    const cor = CENA.hidrante;
    b.caixa(0, 0.32, 0, 0.26, 0.64, 0.26, cor);
    b.caixa(0, 0.70, 0, 0.32, 0.14, 0.32, tonalizar(cor, 1.2));
    b.caixa(0, 0.80, 0, 0.18, 0.12, 0.18, tonalizar(cor, 0.8));
    b.caixa(0.20, 0.42, 0, 0.16, 0.14, 0.14, tonalizar(cor, 0.85));
    b.caixa(-0.20, 0.42, 0, 0.16, 0.14, 0.14, tonalizar(cor, 0.85));
    return b.terminar();
  });
}

export function placaDeRua(texto) {
  return memo(`placa:${texto}`, () => {
    const b = new Construtor();
    b.caixa(0, 1.1, 0, 0.08, 2.2, 0.08, CENA.poste);
    b.caixa(0, 2.2, 0, 0.9, 0.5, 0.06, 0x2b86d6, { cores: { frente: 0x3d9ae8 } });
    return b.terminar();
  });
}

export function pilhaDePneus(quantidade) {
  return memo(`pneus:${quantidade}`, () => {
    const b = new Construtor();
    for (let i = 0; i < quantidade; i++) {
      b.tronco(0, 0.13 + i * 0.24, 0, 0.72, 0.24, 0.72, 0.72, 0.72, 0, 0,
        i % 2 ? 0x22252a : 0x2c3036);
    }
    return b.terminar();
  });
}

export function banco() {
  return memo('banco', () => {
    const b = new Construtor();
    b.caixa(0, 0.40, 0, 1.6, 0.10, 0.48, CENA.banco);
    b.caixa(0, 0.66, 0.22, 1.6, 0.42, 0.08, CENA.banco);
    b.caixa(-0.7, 0.20, 0, 0.10, 0.40, 0.44, CENA.poste);
    b.caixa(0.7, 0.20, 0, 0.10, 0.40, 0.44, CENA.poste);
    return b.terminar();
  });
}

/** Pórtico do ponto de passagem: dá para ver de longe e atravessar. */
export function portico(largura, cor) {
  return memo(`portico:${largura}:${cor}`, () => {
    const b = new Construtor();
    const altura = 4.4;
    for (const lado of [-1, 1]) {
      b.caixa(lado * largura / 2, altura / 2, 0, 0.26, altura, 0.26, cor, { semLuz: true });
    }
    b.caixa(0, altura - 0.2, 0, largura + 0.26, 0.4, 0.22, cor, { semLuz: true });
    return b.terminar();
  });
}

/** Cenário de fundo: um quarteirão inteiro montado de uma vez só. */
export function quarteirao(semente, perfil) {
  return memo(`quarteirao:${semente}:${perfil}`, () => {
    const sortear = criarSorteio(semente);
    const b = new Construtor();
    const cores = perfil === 'industrial' ? FACHADAS_INDUSTRIAIS : FACHADAS;
    const quantos = inteiro(sortear, 2, 4);
    for (let i = 0; i < quantos; i++) {
      const l = entre(sortear, 5, 11);
      const p = entre(sortear, 5, 10);
      const h = perfil === 'industrial' ? entre(sortear, 4, 9) : entre(sortear, 6, 22);
      const cor = cores[inteiro(sortear, 0, cores.length - 1)];
      const x = entre(sortear, -6, 6);
      const z = entre(sortear, -6, 6);
      const m = predio(Math.round(l), Math.round(h), Math.round(p), cor, semente + i);
      const base = b.vertices.length / 3;
      for (let k = 0; k < m.vertices.length; k += 3) {
        b.vertices.push(m.vertices[k] + x, m.vertices[k + 1], m.vertices[k + 2] + z);
      }
      for (const f of m.faces) b.faces.push({ ...f, i: f.i.map((n) => n + base) });
    }
    return b.terminar();
  });
}
