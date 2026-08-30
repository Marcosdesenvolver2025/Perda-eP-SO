/**
 * Regras que o app precisa saber sem depender da rede.
 *
 * Existe uma cópia aqui de propósito: o app avisa a pessoa enquanto ela digita.
 * O servidor valida de novo — é ele quem manda. Se mudar um valor aqui, mude
 * também em `servidor/src/dominio/regras.ts`.
 */

/** Quem faz a entrega. Escolha do vendedor, congelada no pedido. */
export type ModalidadeEntrega = 'PLATAFORMA' | 'VENDEDOR';

/** Comissão da plataforma sobre o produto. Vale nas duas modalidades. */
export const COMISSAO = 0.12;

/** Valor mínimo de um anúncio, em centavos. */
export const VALOR_MINIMO_VENDA = 1_000; // R$ 10,00

/** Tarifa fixa por faixa. Cobrada SÓ na modalidade PLATAFORMA. */
export const TABELA_DE_TARIFAS = [
  { ateInclusive: 2_499, tarifa: 250 }, //  até R$ 24,99  -> R$ 2,50
  { ateInclusive: 4_999, tarifa: 450 }, //  até R$ 49,99  -> R$ 4,50
  { ateInclusive: 9_999, tarifa: 650 }, //  até R$ 99,99  -> R$ 6,50
  { ateInclusive: 19_999, tarifa: 850 }, // até R$ 199,99 -> R$ 8,50
  { ateInclusive: 49_999, tarifa: 1_050 }, // até R$ 499,99 -> R$ 10,50
  { ateInclusive: Number.POSITIVE_INFINITY, tarifa: 1_450 }, // R$ 500+ -> R$ 14,50
] as const;

export function tarifaFixa(valorProduto: number, modalidade: ModalidadeEntrega): number {
  if (modalidade === 'VENDEDOR') return 0;
  return TABELA_DE_TARIFAS.find((f) => valorProduto <= f.ateInclusive)!.tarifa;
}

// ---------------------------------------------------------------------------
// Limites do pacote — valem SÓ na modalidade PLATAFORMA
// ---------------------------------------------------------------------------

export const PESO_MAXIMO_G = 20_000; // 20 kg
export const LARGURA_MAXIMA_CM = 100;
export const ALTURA_MAXIMA_CM = 100;

export const DIAS_PARA_TESTAR = 7;
export const DIAS_PARA_CONFIRMACAO_AUTOMATICA = 3;

export interface Medidas {
  pesoG: number;
  comprimentoCm: number;
  larguraCm: number;
  alturaCm: number;
}

function kg(gramas: number): string {
  return (gramas / 1000).toFixed(gramas % 1000 === 0 ? 0 : 1).replace('.', ',');
}

/**
 * O pacote cabe na entrega da plataforma?
 * Se não couber, o app não deixa escolher essa modalidade e explica por quê.
 */
export function cabeNaEntregaDaPlataforma(m: Medidas): {
  cabe: boolean;
  motivos: string[];
} {
  const motivos: string[] = [];

  if (m.pesoG > PESO_MAXIMO_G) {
    motivos.push(`pesa ${kg(m.pesoG)} kg e o limite é ${kg(PESO_MAXIMO_G)} kg`);
  }
  if (m.larguraCm > LARGURA_MAXIMA_CM) {
    motivos.push(`tem ${m.larguraCm} cm de largura e o limite é ${LARGURA_MAXIMA_CM} cm`);
  }
  if (m.alturaCm > ALTURA_MAXIMA_CM) {
    motivos.push(`tem ${m.alturaCm} cm de altura e o limite é ${ALTURA_MAXIMA_CM} cm`);
  }

  return { cabe: motivos.length === 0, motivos };
}

export interface Descontos {
  comissao: number;
  tarifa: number;
  total: number;
  /** O que sobra para o vendedor. */
  vendedor: number;
}

/**
 * Quanto a plataforma desconta e quanto o vendedor recebe, por modalidade.
 * É o que a tela de anúncio mostra para o vendedor comparar antes de escolher.
 */
export function calcularDescontos(
  preco: number,
  modalidade: ModalidadeEntrega,
): Descontos {
  const comissao = Math.floor(preco * COMISSAO);
  const tarifa = tarifaFixa(preco, modalidade);
  const total = comissao + tarifa;
  return { comissao, tarifa, total, vendedor: Math.max(0, preco - total) };
}

// ---------------------------------------------------------------------------
// Ofertas — espelho de `servidor/src/dominio/ofertas.ts`
// ---------------------------------------------------------------------------

/** Quantos dias uma proposta fica de pé esperando resposta. */
export const DIAS_PARA_RESPONDER_OFERTA = 3;

/** Piso da proposta, como fração do preço pedido. */
export const FRACAO_MINIMA_DA_OFERTA = 0.5;

/**
 * A menor oferta aceitável neste anúncio.
 * Nunca abaixo do mínimo de venda: metade de R$ 12,00 daria R$ 6,00, e com
 * esse valor o pedido nem poderia ser criado.
 */
export function valorMinimoDaOferta(precoAnunciado: number): number {
  return Math.max(
    Math.ceil(precoAnunciado * FRACAO_MINIMA_DA_OFERTA),
    VALOR_MINIMO_VENDA,
  );
}

function emReais(centavos: number): string {
  return `R$ ${(centavos / 100).toFixed(2).replace('.', ',')}`;
}

/**
 * Valida a proposta antes de mandar para o servidor, para a pessoa saber na
 * hora em vez de esperar a resposta. O servidor valida de novo — é ele que manda.
 */
export function validarProposta(
  valorProposto: number,
  precoAnunciado: number,
): { valido: boolean; motivo: string } {
  if (!Number.isInteger(valorProposto) || valorProposto <= 0) {
    return { valido: false, motivo: 'Informe um valor válido.' };
  }
  if (valorProposto >= precoAnunciado) {
    return {
      valido: false,
      motivo: `Esse valor é igual ou maior que o preço pedido. Para levar por ${emReais(precoAnunciado)}, é só comprar direto.`,
    };
  }
  const minimo = valorMinimoDaOferta(precoAnunciado);
  if (valorProposto < minimo) {
    return {
      valido: false,
      motivo: `A menor oferta possível aqui é ${emReais(minimo)}.`,
    };
  }
  return { valido: true, motivo: '' };
}
