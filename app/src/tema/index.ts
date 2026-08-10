/**
 * Tema do Vendas Itinga.
 *
 * A paleta gira em torno do verde #00DF13, que ocupa o lugar do roxo do app
 * usado como referência. O verde puro é forte demais para texto sobre fundo
 * branco (contraste baixo), então textos e ícones usam `verdeEscuro`, e o
 * `verde` fica para preenchimentos, botões e destaques.
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

export const raio = {
  sm: 8,
  md: 12,
  lg: 16,
  pilula: 999,
} as const;

/**
 * O app escreve tudo em caixa baixa, como no app de referência: dá um tom
 * informal, de vizinho vendendo pro vizinho.
 */
export const fonte = {
  titulo: { fontSize: 22, fontWeight: '700' as const, color: cores.texto },
  secao: { fontSize: 19, fontWeight: '700' as const, color: cores.texto },
  subtitulo: { fontSize: 14, fontWeight: '400' as const, color: cores.textoSuave },
  corpo: { fontSize: 15, fontWeight: '400' as const, color: cores.texto },
  rotulo: { fontSize: 13, fontWeight: '600' as const, color: cores.texto },
  pequeno: { fontSize: 12, fontWeight: '400' as const, color: cores.textoSuave },
  preco: { fontSize: 17, fontWeight: '700' as const, color: cores.texto },
};

export const sombra = Platform.select({
  android: { elevation: 2 },
  default: {
    shadowColor: '#000',
    shadowOpacity: 0.06,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 2 },
  },
});
