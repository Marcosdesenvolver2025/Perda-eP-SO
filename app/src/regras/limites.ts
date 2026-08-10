/**
 * Limites de peso e tamanho, iguais aos do servidor.
 *
 * Existe uma cópia aqui de propósito: o app avisa a pessoa enquanto ela digita,
 * sem depender de rede. O servidor valida de novo — é ele quem manda.
 * Se mudar um valor aqui, mude também em `servidor/src/dominio/regras.ts`.
 */

export const PESO_MAXIMO_G = 20_000; // 20 kg
export const DIMENSAO_MAXIMA_CM = 60;
export const DIAS_PARA_TESTAR = 4;

export const COMISSAO_SEM_ENTREGADOR = 0.16;
export const COMISSAO_COM_ENTREGADOR = 0.18;

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
    erros.push(
      `o lado maior tem ${maior} cm e o limite é ${DIMENSAO_MAXIMA_CM} cm.`,
    );
  }

  if (erros.length) {
    erros.push('você ainda pode anunciar combinando a entrega direto com o comprador.');
  }

  return { valido: erros.length === 0, erros };
}

/** Quanto o vendedor recebe, para mostrar antes de publicar. */
export function quantoVouReceber(preco: number, comEntregador: boolean): number {
  const taxa = comEntregador ? COMISSAO_COM_ENTREGADOR : COMISSAO_SEM_ENTREGADOR;
  return preco - Math.floor(preco * taxa);
}
