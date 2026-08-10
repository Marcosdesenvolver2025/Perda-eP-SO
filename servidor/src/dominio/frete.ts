/**
 * Limites de peso e tamanho dos anúncios e cálculo do frete local.
 *
 * O app inteiro respeita o mesmo limite: 20 kg e 60 cm na maior dimensão.
 * A validação roda no servidor (aqui) e também no app, antes de o vendedor
 * perder tempo preenchendo o anúncio.
 */

import { DIMENSAO_MAXIMA_CM, PESO_MAXIMO_G } from './regras';

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

/**
 * Valida um pacote contra os limites operacionais dos nossos entregadores.
 * Retorna TODOS os problemas de uma vez, para o app mostrar tudo junto.
 */
export function validarMedidas(m: Medidas): ResultadoValidacao {
  const erros: string[] = [];
  const dimensoes = [m.comprimentoCm, m.larguraCm, m.alturaCm];

  if (!Number.isFinite(m.pesoG) || m.pesoG <= 0) {
    erros.push('Informe o peso do produto.');
  } else if (m.pesoG > PESO_MAXIMO_G) {
    erros.push(
      `Peso acima do limite: ${(m.pesoG / 1000).toFixed(1)} kg. O máximo é ${
        PESO_MAXIMO_G / 1000
      } kg.`,
    );
  }

  if (dimensoes.some((d) => !Number.isFinite(d) || d <= 0)) {
    erros.push('Informe comprimento, largura e altura do produto.');
  } else {
    const maior = Math.max(...dimensoes);
    if (maior > DIMENSAO_MAXIMA_CM) {
      erros.push(
        `Tamanho acima do limite: ${maior} cm. Nenhum lado pode passar de ${DIMENSAO_MAXIMA_CM} cm.`,
      );
    }
  }

  return { valido: erros.length === 0, erros };
}

/** Peso cubado (padrão logístico: volume em cm³ dividido por 6.000). */
export function pesoCubadoG(m: Medidas): number {
  return Math.round(
    ((m.comprimentoCm * m.larguraCm * m.alturaCm) / 6000) * 1000,
  );
}

/** O que a operação cobra por: o maior entre peso real e peso cubado. */
export function pesoTaxavelG(m: Medidas): number {
  return Math.max(m.pesoG, pesoCubadoG(m));
}

export interface TabelaFrete {
  /** Valor base da entrega dentro da cidade, em centavos. */
  base: number;
  /** Acréscimo por quilo taxável acima do primeiro, em centavos. */
  porKgAdicional: number;
  /** Teto do frete, em centavos. */
  teto: number;
}

export const TABELA_FRETE_PADRAO: TabelaFrete = {
  base: 800, // R$ 8,00
  porKgAdicional: 150, // R$ 1,50 por kg acima de 1 kg
  teto: 2500, // R$ 25,00
};

/**
 * Frete da entrega local, em centavos. Como tudo acontece dentro de Itinga,
 * não há faixa por distância: o que pesa no custo é peso/volume.
 */
export function calcularFrete(
  m: Medidas,
  tabela: TabelaFrete = TABELA_FRETE_PADRAO,
): number {
  const kg = pesoTaxavelG(m) / 1000;
  const adicionais = Math.max(0, Math.ceil(kg) - 1);
  const valor = tabela.base + adicionais * tabela.porKgAdicional;
  return Math.min(valor, tabela.teto);
}
