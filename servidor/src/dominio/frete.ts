/**
 * Limites do pacote e o que cada modalidade aceita.
 *
 * Os limites valem **só para a modalidade PLATAFORMA**: são o que cabe na
 * moto do entregador. Na modalidade VENDEDOR não há limite — quem carrega é
 * o próprio vendedor, e o problema é dele.
 *
 * A validação roda no servidor (aqui) e também no app, para o vendedor saber
 * antes de terminar o cadastro se pode usar nossos entregadores.
 */

import {
  ALTURA_MAXIMA_CM,
  COMPRIMENTO_MAXIMO_CM,
  LARGURA_MAXIMA_CM,
  PESO_MAXIMO_G,
  type ModalidadeEntrega,
} from './regras';

export interface Medidas {
  /** Peso em gramas. */
  pesoG: number;
  /** Dimensões da embalagem em centímetros. */
  comprimentoCm: number;
  larguraCm: number;
  alturaCm: number;
}

export interface ResultadoValidacao {
  valido: boolean;
  erros: string[];
}

function kg(gramas: number): string {
  return (gramas / 1000).toFixed(gramas % 1000 === 0 ? 0 : 1).replace('.', ',');
}

/**
 * O pacote cabe na entrega da plataforma?
 *
 * Retorna TODOS os problemas de uma vez, para o app mostrar tudo junto em vez
 * de o vendedor descobrir um por um.
 */
export function cabeNaEntregaDaPlataforma(m: Medidas): ResultadoValidacao {
  const erros: string[] = [];

  if (!Number.isFinite(m.pesoG) || m.pesoG <= 0) {
    erros.push('Informe o peso do produto.');
  } else if (m.pesoG > PESO_MAXIMO_G) {
    erros.push(
      `${kg(m.pesoG)} kg passa do limite de ${kg(PESO_MAXIMO_G)} kg dos nossos entregadores.`,
    );
  }

  const dimensoes: Array<[string, number, number]> = [
    ['largura', m.larguraCm, LARGURA_MAXIMA_CM],
    ['altura', m.alturaCm, ALTURA_MAXIMA_CM],
    ['comprimento', m.comprimentoCm, COMPRIMENTO_MAXIMO_CM],
  ];

  for (const [nome, valor, maximo] of dimensoes) {
    if (!Number.isFinite(valor) || valor <= 0) {
      erros.push(`Informe ${nome === 'altura' ? 'a' : 'o'} ${nome} do produto.`);
    } else if (valor > maximo) {
      erros.push(`${nome} de ${valor} cm passa do limite de ${maximo} cm.`);
    }
  }

  return { valido: erros.length === 0, erros };
}

/**
 * Valida as medidas conforme a modalidade escolhida.
 * Em VENDEDOR só exigimos que os números façam sentido; limite não há.
 */
export function validarMedidas(
  m: Medidas,
  modalidade: ModalidadeEntrega,
): ResultadoValidacao {
  if (modalidade === 'PLATAFORMA') return cabeNaEntregaDaPlataforma(m);

  const erros: string[] = [];
  if (!Number.isFinite(m.pesoG) || m.pesoG <= 0) {
    erros.push('Informe o peso do produto.');
  }
  for (const [nome, valor] of [
    ['comprimento', m.comprimentoCm],
    ['largura', m.larguraCm],
    ['altura', m.alturaCm],
  ] as const) {
    if (!Number.isFinite(valor) || valor <= 0) {
      erros.push(`Informe ${nome === 'altura' ? 'a' : 'o'} ${nome} do produto.`);
    }
  }
  return { valido: erros.length === 0, erros };
}

/** Peso cubado (padrão logístico: volume em cm³ dividido por 6.000). */
export function pesoCubadoG(m: Medidas): number {
  return Math.round(((m.comprimentoCm * m.larguraCm * m.alturaCm) / 6000) * 1000);
}

/** O que a operação considera para dimensionar a corrida. */
export function pesoTaxavelG(m: Medidas): number {
  return Math.max(m.pesoG, pesoCubadoG(m));
}
