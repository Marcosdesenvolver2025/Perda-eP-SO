// Hora do dia e tempo.
//
// Isto aqui é metade do remédio contra o enjoo: a mesma rua às sete da manhã,
// no fim da tarde e debaixo de chuva parece três lugares diferentes. E não é só
// pintura — a chuva tira aderência de verdade, a noite acende os faróis, a
// neblina encurta o que dá para ver e obriga a andar mais devagar.
//
// As cores e a luz saem de motor/paleta.js. O padrão é o visual claro e
// saturado dos jogos de dirigir de celular: luz ambiente alta, pouca névoa e
// céu azul de revista. Noite, chuva e neblina são o contraste — e existem
// justamente para o dia claro não cansar.

import { CEU, LUZ } from '../motor/paleta.js';

export const CLIMAS = {
  manha: {
    id: 'manha',
    nome: 'manhã',
    corCeuAlto: CEU.alto, corCeuBaixo: CEU.baixo, corHorizonte: CEU.horizonte,
    corNuvem: CEU.nuvem, forcaNuvem: 0.95,
    astro: { azimute: 1.1, elevacao: 0.46, tamanho: 0.032, cor: 0xfff6cc, corNucleo: 0xffffff },
    direcaoSol: { x: -0.46, y: 0.70, z: -0.54 },
    luz: LUZ.intensidade, ambienteLuz: LUZ.ambiente, alcanceNevoa: LUZ.nevoa,
    atrito: 1.0, chuva: 0, farois: false, forcaSilhueta: 0.9,
  },

  seco: {
    id: 'seco',
    nome: 'sol a pino',
    corCeuAlto: 0x1470cf, corCeuBaixo: 0xaee2f8, corHorizonte: 0xdff0f7,
    corNuvem: 0xffffff, forcaNuvem: 0.65,
    astro: { azimute: 0.2, elevacao: 1.15, tamanho: 0.028, cor: 0xfffae0, corNucleo: 0xffffff },
    direcaoSol: { x: -0.10, y: 0.96, z: -0.26 },
    luz: 1.18, ambienteLuz: 0.76, alcanceNevoa: 340,
    atrito: 1.04, chuva: 0, farois: false, forcaSilhueta: 0.8,
  },

  tarde: {
    id: 'tarde',
    nome: 'fim de tarde',
    corCeuAlto: 0x2a4fb8, corCeuBaixo: 0xffb865, corHorizonte: 0xffd7a2,
    corNuvem: 0xffe0b8, forcaNuvem: 0.95,
    astro: { azimute: -1.9, elevacao: 0.11, tamanho: 0.046, cor: 0xffab45, corNucleo: 0xffeec2 },
    direcaoSol: { x: 0.78, y: 0.30, z: 0.54 },
    luz: 1.04, ambienteLuz: 0.66, alcanceNevoa: 210,
    atrito: 1.0, chuva: 0, farois: false, forcaSilhueta: 0.80,
    tonalidade: 0xffc478, forcaTonalidade: 0.10,
  },

  noite: {
    id: 'noite',
    nome: 'noite',
    corCeuAlto: 0x0a1330, corCeuBaixo: 0x24386b, corHorizonte: 0x33487d,
    corNuvem: 0x4a5a86, forcaNuvem: 0.35, estrelas: 0.9,
    astro: { azimute: 2.4, elevacao: 0.55, tamanho: 0.024, cor: 0xcfdcf0, corNucleo: 0xf4f8ff, crateras: true },
    direcaoSol: { x: 0.36, y: 0.78, z: 0.50 },
    luz: 0.46, ambienteLuz: 0.50, alcanceNevoa: 120,
    atrito: 0.97, chuva: 0, farois: true, forcaSilhueta: 1,
    postes: true,
  },

  chuva: {
    id: 'chuva',
    nome: 'chuva',
    corCeuAlto: 0x51617a, corCeuBaixo: 0x91a3b6, corHorizonte: 0xa3b4c4,
    corNuvem: 0xb6c2cf, forcaNuvem: 0.95,
    astro: null,
    direcaoSol: { x: -0.2, y: 0.9, z: -0.3 },
    luz: 0.82, ambienteLuz: 0.68, alcanceNevoa: 110,
    atrito: 0.74, chuva: 1, farois: true, molhado: true, forcaSilhueta: 0.5,
    tonalidade: 0x8fa2b6, forcaTonalidade: 0.08,
  },

  neblina: {
    id: 'neblina',
    nome: 'neblina',
    corCeuAlto: 0xb9c8cd, corCeuBaixo: 0xe2e9ea, corHorizonte: 0xeaefef,
    corNuvem: 0xf0f4f4, forcaNuvem: 0.5, semNuvens: true,
    astro: { azimute: 0.6, elevacao: 0.35, tamanho: 0.05, cor: 0xf2f7f8, corNucleo: 0xffffff },
    direcaoSol: { x: -0.3, y: 0.85, z: -0.4 },
    luz: 0.96, ambienteLuz: 0.82, alcanceNevoa: 46,
    atrito: 0.88, chuva: 0, farois: true, molhado: true, forcaSilhueta: 0.16,
  },
};

export const LISTA_CLIMAS = Object.values(CLIMAS);

export function clima(id) {
  return CLIMAS[id] || CLIMAS.manha;
}

/** Aderência da pista: o clima manda, e o piso tira mais um pouco. */
export function atritoDe(ambiente, piso) {
  const base = ambiente.atrito === undefined ? 1 : ambiente.atrito;
  if (piso === 'terra') return base * 0.82;
  if (piso === 'areia') return base * 0.68;
  if (piso === 'grama') return base * 0.72;
  return base;
}
