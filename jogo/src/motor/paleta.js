// A paleta.
//
// Todas as cores do jogo saem daqui. Mexer numa linha deste arquivo muda o
// visual inteiro — é de propósito: cor espalhada por vinte arquivos é
// impossível de acertar.
//
// O alvo é o visual dos jogos de dirigir de celular da escola do DR Driving.
// Quatro coisas fazem aquele look, e nenhuma delas é textura:
//
//   1. COR SATURADA. Céu azul de revista, grama verde de plástico, carro de
//      cor de brinquedo. Nada de tom terroso, nada de dessaturar para "ficar
//      realista".
//   2. ASFALTO CINZA-MÉDIO, não preto. Faixa branca limpa por cima. Asfalto
//      escuro engole a pintura e some com a leitura da pista.
//   3. LUZ CHAPADA. A luz ambiente é alta (~0.72) e a direcional é fraca, então
//      a diferença entre a face iluminada e a face na sombra é pequena. É o que
//      faz o carro parecer um brinquedo de plástico em vez de um objeto
//      fotografado.
//   4. AR LIMPO. Pouca névoa, nada de vinheta pesada. O mundo é nítido até o
//      horizonte.
//
// O desenho continua sendo todo nosso, feito em tempo de execução: nenhuma
// imagem, nenhuma textura e nenhum modelo vêm de lugar nenhum.

export const CEU = {
  alto: 0x1d7fd6,
  baixo: 0x9fdbf7,
  horizonte: 0xd3ecfa,
  nuvem: 0xffffff,
};

export const TERRENO = {
  grama: 0x63b544,
  gramaClara: 0x86c452,
  areia: 0xf0dca6,
  terra: 0xb08a58,
  cascalho: 0xbdbcae,
};

export const VIA = {
  asfalto: 0x70747a,
  asfaltoClaro: 0x7b7f85,
  terra: 0xb28c5c,
  calcada: 0xd8d4ca,
  meioFio: 0xbdb8ac,
  faixa: 0xfbfaf6,
  faixaAmarela: 0xf7d84a,
};

/** Fachadas: pastel claro, que é o que deixa a cidade leve em vez de pesada. */
export const FACHADAS = [
  0xf4e6d0, 0xeccdac, 0xf2d3c8, 0xd2e3ea, 0xdae8ce,
  0xf1dcac, 0xe3d4e6, 0xf7ecdc, 0xcfdce4, 0xefd2b4,
];

export const FACHADAS_INDUSTRIAIS = [
  0xc3c6c2, 0xb4b8b6, 0xcfd2cc, 0xbfc4bd, 0xd6d3c6,
];

export const PREDIO = {
  // Azul de vidro, não preto: janela escura demais vira buraco na fachada.
  janela: 0x5d80a0,
  janelaAcesa: 0xffd98a,
  platibanda: 0.82,   // fator de tom em relação à fachada
  topo: 0.86,
};

export const VEGETACAO = {
  folhagemEscura: 0x3f9b34,
  folhagemClara: 0x82ca52,
  tronco: 0x8a6440,
  palmeira: 0x4fae45,
  troncoPalmeira: 0xa88a62,
};

/** Cores de carro: tinta de brinquedo, saturada e sem meio-termo. */
export const PINTURAS = [
  0xe8392f, 0x2a7fd4, 0xf5c518, 0xf7f7f2, 0x3aa85a,
  0xf07f25, 0x8e4fc4, 0x2b3440, 0x1fb5b0, 0xe8609a,
];

export const CARRO = {
  vidro: 0x3d5f7a,
  vidroClaro: 0x6d94ad,
  borracha: 0x26282c,
  aro: 0xd7dce1,
  cromo: 0xdfe4e9,
  farol: 0xfff6d8,
  lanterna: 0xe8443a,
  placa: 0xf2f4f2,
};

export const CENA = {
  cone: 0xf4681f,
  coneFaixa: 0xfbfbf8,
  poste: 0x8b9199,
  lampada: 0xfff2c8,
  muro: 0xd8c9ae,
  muroIndustrial: 0xb6b9b2,
  grade: 0xb2bcc4,
  hidrante: 0xd94434,
  banco: 0xb07b45,
  portico: 0x3fd07a,
};

/**
 * Luz. `ambiente` alto e `intensidade` perto de 1 é o que chapa o sombreado.
 * Se um dia você quiser o visual mais "fotográfico", baixe `ambiente` para
 * perto de 0.4 e aumente `nevoa` — o jogo inteiro muda de humor.
 */
export const LUZ = {
  ambiente: 0.72,
  intensidade: 1.10,
  nevoa: 300,
  sombra: 0.16,
  vinheta: 0.13,
};
