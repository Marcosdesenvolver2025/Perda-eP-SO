// Hora do dia e tempo.
//
// Isto aqui é metade do remédio contra o enjoo: a mesma rua às sete da manhã,
// no fim da tarde e debaixo de chuva parece três lugares diferentes. E não é só
// pintura — a chuva tira aderência de verdade, a noite acende os faróis, a
// neblina encurta o que dá para ver e obriga a andar mais devagar.

export const CLIMAS = {
  manha: {
    id: 'manha',
    nome: 'manhã',
    corCeuAlto: 0x2f6fb5, corCeuBaixo: 0xa9d4ee, corHorizonte: 0xcfe4f2,
    corNuvem: 0xffffff, forcaNuvem: 0.7,
    astro: { azimute: 1.1, elevacao: 0.42, tamanho: 0.030, cor: 0xfff3c4, corNucleo: 0xfffdf0 },
    direcaoSol: { x: -0.52, y: 0.62, z: -0.58 },
    luz: 1.0, ambienteLuz: 0.44, alcanceNevoa: 130,
    atrito: 1.0, chuva: 0, farois: false, forcaSilhueta: 0.85,
  },

  tarde: {
    id: 'tarde',
    nome: 'fim de tarde',
    corCeuAlto: 0x2a3f7a, corCeuBaixo: 0xf3a15c, corHorizonte: 0xe9b07a,
    corNuvem: 0xffd9a8, forcaNuvem: 0.9,
    astro: { azimute: -1.9, elevacao: 0.10, tamanho: 0.045, cor: 0xff9a3c, corNucleo: 0xffe2a8 },
    direcaoSol: { x: 0.80, y: 0.22, z: 0.55 },
    luz: 0.92, ambienteLuz: 0.40, alcanceNevoa: 105,
    atrito: 1.0, chuva: 0, farois: false, forcaSilhueta: 0.92,
    tonalidade: 0xffb36b, forcaTonalidade: 0.14,
  },

  noite: {
    id: 'noite',
    nome: 'noite',
    corCeuAlto: 0x05070f, corCeuBaixo: 0x141d33, corHorizonte: 0x1b2540,
    corNuvem: 0x39425c, forcaNuvem: 0.35, estrelas: 0.9,
    astro: { azimute: 2.4, elevacao: 0.55, tamanho: 0.022, cor: 0xbfd0e8, corNucleo: 0xeef3ff, crateras: true },
    direcaoSol: { x: 0.36, y: 0.78, z: 0.50 },
    luz: 0.30, ambienteLuz: 0.30, alcanceNevoa: 62,
    atrito: 0.97, chuva: 0, farois: true, forcaSilhueta: 1,
    postes: true,
  },

  chuva: {
    id: 'chuva',
    nome: 'chuva',
    corCeuAlto: 0x39424f, corCeuBaixo: 0x707c8a, corHorizonte: 0x7d8895,
    corNuvem: 0x9aa4b0, forcaNuvem: 0.95,
    astro: null,
    direcaoSol: { x: -0.2, y: 0.9, z: -0.3 },
    luz: 0.62, ambienteLuz: 0.55, alcanceNevoa: 58,
    atrito: 0.74, chuva: 1, farois: true, molhado: true, forcaSilhueta: 0.55,
    tonalidade: 0x6f7c8a, forcaTonalidade: 0.10,
  },

  neblina: {
    id: 'neblina',
    nome: 'neblina',
    corCeuAlto: 0x9aa7ad, corCeuBaixo: 0xccd4d6, corHorizonte: 0xd4dbdc,
    corNuvem: 0xe4eaea, forcaNuvem: 0.5, semNuvens: true,
    astro: { azimute: 0.6, elevacao: 0.35, tamanho: 0.05, cor: 0xe8eef0, corNucleo: 0xf4f8f9 },
    direcaoSol: { x: -0.3, y: 0.85, z: -0.4 },
    luz: 0.80, ambienteLuz: 0.70, alcanceNevoa: 30,
    atrito: 0.88, chuva: 0, farois: true, molhado: true, forcaSilhueta: 0.18,
  },

  seco: {
    id: 'seco',
    nome: 'sol a pino',
    corCeuAlto: 0x1f7fd0, corCeuBaixo: 0xbfe2f5, corHorizonte: 0xe3d9bd,
    corNuvem: 0xffffff, forcaNuvem: 0.35,
    astro: { azimute: 0.2, elevacao: 1.15, tamanho: 0.026, cor: 0xfff6d0, corNucleo: 0xffffff },
    direcaoSol: { x: -0.12, y: 0.96, z: -0.24 },
    luz: 1.12, ambienteLuz: 0.50, alcanceNevoa: 160,
    atrito: 1.04, chuva: 0, farois: false, forcaSilhueta: 0.6,
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
