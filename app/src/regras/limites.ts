/**
 * Regras que o app precisa saber sem depender da rede.
 *
 * Existe uma cópia aqui de propósito: o app avisa a pessoa enquanto ela digita.
 * O servidor valida de novo — é ele quem manda. Se mudar um valor aqui, mude
 * também em `servidor/src/dominio/regras.ts`.
 */

export const PESO_MAXIMO_G = 20_000; // 20 kg
export const DIMENSAO_MAXIMA_CM = 60;
export const DIAS_PARA_TESTAR = 7;

/** Comissão da plataforma sobre o valor do produto. */
export const COMISSAO = 0.12;

/** Valor mínimo de um anúncio, em centavos. */
export const VALOR_MINIMO_VENDA = 1_000; // R$ 10,00

/** Tarifa fixa por faixa de preço. A última faixa é aberta. */
export const TABELA_DE_TARIFAS = [
  { ateInclusive: 2_499, tarifa: 250 }, // até R$ 24,99  -> R$ 2,50
  { ateInclusive: 4_999, tarifa: 450 }, // até R$ 49,99  -> R$ 4,50
  { ateInclusive: 9_999, tarifa: 650 }, // até R$ 99,99  -> R$ 6,50
  { ateInclusive: 19_999, tarifa: 850 }, // até R$ 199,99 -> R$ 8,50
  { ateInclusive: Number.POSITIVE_INFINITY, tarifa: 1_850 }, // R$ 200+ -> R$ 18,50
] as const;

export function tarifaFixa(valorProduto: number): number {
  return TABELA_DE_TARIFAS.find((f) => valorProduto <= f.ateInclusive)!.tarifa;
}

export interface Medidas {
  pesoG: number;
  comprimentoCm: number;
  larguraCm: number;
  alturaCm: number;
}

export function validarMedidas(m: Medidas): { valido: boolean; erros: string[] } {
  const erros: string[] = [];
  const dimensoes = [m.comprimentoCm, m.larguraCm, m.alturaCm];

  if (m.pesoG > PESO_MAXIMO_G) {
    erros.push(
      `esse produto tem ${(m.pesoG / 1000).toFixed(1)} kg e nossos entregadores levam até ${
        PESO_MAXIMO_G / 1000
      } kg.`,
    );
  }

  const maior = Math.max(...dimensoes);
  if (maior > DIMENSAO_MAXIMA_CM) {
    erros.push(`o lado maior tem ${maior} cm e o limite é ${DIMENSAO_MAXIMA_CM} cm.`);
  }

  if (erros.length) {
    erros.push('você ainda pode anunciar combinando a entrega direto com o comprador.');
  }

  return { valido: erros.length === 0, erros };
}

export interface Descontos {
  comissao: number;
  tarifa: number;
  total: number;
  /** O que sobra para o vendedor. */
  vendedor: number;
}

/**
 * Quanto a plataforma desconta e quanto o vendedor recebe.
 * Mesmo cálculo do servidor, para o anúncio mostrar o valor antes de publicar.
 */
export function calcularDescontos(preco: number): Descontos {
  const comissao = Math.floor(preco * COMISSAO);
  const tarifa = tarifaFixa(preco);
  const total = comissao + tarifa;
  return { comissao, tarifa, total, vendedor: Math.max(0, preco - total) };
}
