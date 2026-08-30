/**
 * Tema do Vendas Itinga.
 *
 * A paleta gira em torno do verde #00DF13, que ocupa o lugar do roxo do app
 * usado como referência. O verde puro é forte demais para texto sobre fundo
 * branco (contraste baixo), então textos e ícones usam `verdeEscuro`, e o
 * `verde` fica para preenchimentos, botões e destaques.
 *
 * O **âmbar** é a segunda cor da marca e existe para diferenciar: marketplace
 * de usados costuma ser de uma cor só com branco. Aqui ele marca desconto,
 * oferta em aberto e prazo correndo — coisas que pedem urgência, onde o verde
 * (que significa "tudo certo") passaria a mensagem errada. Nunca como fundo de
 * bloco grande: é tempero, não base.
 *
 * O **coral** é só para o coração de curtido e para o contador de curtidas.
 */

import { Platform } from 'react-native';

export const cores = {
  /** Cor da marca. Use em fundo de botão, aba ativa e destaque. */
  verde: '#00DF13',
  /** Verde legível como texto sobre branco. */
  verdeEscuro: '#00990D',
  /** Verde para textos pequenos e ícones que precisam de mais contraste. */
  verdeProfundo: '#00700A',
  /** Fundo claro esverdeado para cards de destaque. */
  verdeClaro: '#E8FFEA',
  verdeSuave: '#B6F7BC',

  /** Segunda cor: desconto, oferta em aberto, prazo correndo. */
  ambar: '#FF9F1C',
  ambarClaro: '#FFF3E0',
  /** Âmbar escurecido o bastante para virar texto sobre branco. */
  ambarEscuro: '#8A4B00',

  /** Curtidas. Só o coração e o contador. */
  coral: '#FF3D68',

  preto: '#111813',
  texto: '#1B2320',
  textoSuave: '#6B7A70',
  textoFraco: '#9AA79F',

  branco: '#FFFFFF',
  fundo: '#FFFFFF',
  fundoCinza: '#F5F6F5',
  borda: '#E6E9E7',

  alerta: '#D93025',
  aviso: '#B26A00',
  informacao: '#1B6FE0',
} as const;

export const espaco = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
} as const;

/**
 * Cantos. O app usa cantos mais retos que o comum em marketplace de usados —
 * é parte de ter cara própria. `pilula` fica para filtro e selo pequeno;
 * botão grande usa `botao`, que é quase reto.
 */
export const raio = {
  sm: 6,
  md: 10,
  lg: 14,
  /** Botão principal: canto discreto, não cápsula. */
  botao: 12,
  /** Cartão de produto e blocos de conteúdo. */
  cartao: 18,
  pilula: 999,
} as const;

/**
 * O app escreve tudo em caixa baixa, como no app de referência: dá um tom
 * informal, de vizinho vendendo pro vizinho.
 */
export const fonte = {
  /**
   * Títulos usam peso 800 e `letterSpacing` negativo. É o que dá a impressão
   * de tipografia desenhada em vez de tipografia padrão do sistema — e é
   * barato: não precisa carregar fonte nenhuma.
   */
  titulo: {
    fontSize: 25,
    fontWeight: '800' as const,
    letterSpacing: -0.6,
    color: cores.texto,
  },
  secao: {
    fontSize: 20,
    fontWeight: '800' as const,
    letterSpacing: -0.4,
    color: cores.texto,
  },
  subtitulo: { fontSize: 14, fontWeight: '400' as const, color: cores.textoSuave },
  corpo: { fontSize: 15, fontWeight: '400' as const, lineHeight: 22, color: cores.texto },
  rotulo: { fontSize: 13, fontWeight: '700' as const, color: cores.texto },
  pequeno: { fontSize: 12, fontWeight: '500' as const, color: cores.textoSuave },
  preco: {
    fontSize: 18,
    fontWeight: '800' as const,
    letterSpacing: -0.4,
    color: cores.texto,
  },
};

/**
 * Três níveis de elevação, em vez de um só.
 *
 * A diferença entre um app que parece caseiro e um que parece caro está mais
 * na sombra do que na cor: sombra única e dura achata tudo no mesmo plano.
 * Aqui o cartão quase encosta na página, o que flutua sobe de verdade, e o
 * botão principal ganha um halo da própria cor — truque barato que faz o
 * verde parecer aceso.
 */
export const sombra = Platform.select({
  android: { elevation: 2 },
  default: {
    shadowColor: '#0B1F0E',
    shadowOpacity: 0.06,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 2 },
  },
});

/** Cartões e blocos apoiados na página. */
export const sombraCartao = Platform.select({
  android: { elevation: 3 },
  default: {
    shadowColor: '#0B1F0E',
    shadowOpacity: 0.08,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 6 },
  },
});

/** Barra de ação, modal, coisas que ficam por cima do conteúdo. */
export const sombraFlutuante = Platform.select({
  android: { elevation: 8 },
  default: {
    shadowColor: '#0B1F0E',
    shadowOpacity: 0.14,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: 10 },
  },
});

/** Halo verde do botão principal. Faz a cor parecer acesa, não chapada. */
export const sombraVerde = Platform.select({
  android: { elevation: 4 },
  default: {
    shadowColor: cores.verdeProfundo,
    shadowOpacity: 0.35,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
  },
});
