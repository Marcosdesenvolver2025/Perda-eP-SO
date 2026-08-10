/**
 * Janela de teste de 4 dias e cálculo do reembolso.
 *
 * Como funciona para o comprador:
 *  1. ele compra e o dinheiro fica retido (não vai para o vendedor ainda);
 *  2. o produto chega -> começa a contar 4 dias corridos para testar;
 *  3. dentro desses 4 dias ele pode pedir reembolso pelo app;
 *  4. passados os 4 dias sem pedido, o repasse ao vendedor é liberado.
 *
 * Sobre a taxa: em pedido entregue pelos NOSSOS entregadores, a comissão e o
 * frete são cobrados do mesmo jeito, porque o serviço já foi prestado. Quem
 * devolve o valor do produto é o vendedor.
 */

import {
  DIAS_PARA_TESTAR,
  RETER_COMISSAO_NO_REEMBOLSO,
  RETER_FRETE_NO_REEMBOLSO,
} from './regras';
import type { ResultadoSplit } from './comissao';
import type { ModalidadeEntrega } from './regras';

const UM_DIA_MS = 24 * 60 * 60 * 1000;

/** Momento em que a janela de teste se encerra. */
export function prazoParaTestar(
  entregueEm: Date,
  dias: number = DIAS_PARA_TESTAR,
): Date {
  return new Date(entregueEm.getTime() + dias * UM_DIA_MS);
}

export function dentroDaJanelaDeTeste(
  entregueEm: Date | null | undefined,
  agora: Date = new Date(),
  dias: number = DIAS_PARA_TESTAR,
): boolean {
  // sem entrega confirmada a janela nem começou: o pedido ainda pode ser
  // cancelado, mas por outro fluxo (cancelamento, não devolução).
  if (!entregueEm) return false;
  return agora.getTime() <= prazoParaTestar(entregueEm, dias).getTime();
}

/** Dias inteiros que ainda restam para testar (0 quando a janela fechou). */
export function diasRestantesParaTestar(
  entregueEm: Date | null | undefined,
  agora: Date = new Date(),
  dias: number = DIAS_PARA_TESTAR,
): number {
  if (!entregueEm) return dias;
  const restanteMs = prazoParaTestar(entregueEm, dias).getTime() - agora.getTime();
  return restanteMs <= 0 ? 0 : Math.ceil(restanteMs / UM_DIA_MS);
}

export interface OpcoesReembolso {
  reterComissao?: boolean;
  reterFrete?: boolean;
}

export interface CalculoReembolso {
  /** Quanto volta para o comprador, em centavos. */
  valorReembolsado: number;
  /** Quanto a plataforma retém, em centavos. */
  valorRetido: number;
  /** Quanto sai do saldo do vendedor, em centavos. */
  debitoVendedor: number;
  /** Quanto sai do saldo da plataforma, em centavos. */
  debitoPlataforma: number;
  /** Quanto sai do saldo do entregador (sempre 0: o serviço foi prestado). */
  debitoEntregador: number;
  detalhamento: string[];
}

/**
 * Calcula o estorno de um pedido.
 *
 * `modalidade` decide a retenção: só há taxa retida quando a entrega foi
 * feita pelos nossos entregadores. Quando comprador e vendedor combinaram a
 * entrega entre si, a devolução é integral.
 */
export function calcularReembolso(
  split: ResultadoSplit,
  modalidade: ModalidadeEntrega,
  opcoes: OpcoesReembolso = {},
): CalculoReembolso {
  const comEntregador = modalidade === 'ENTREGADOR_PROPRIO';
  const reterComissao =
    comEntregador && (opcoes.reterComissao ?? RETER_COMISSAO_NO_REEMBOLSO);
  const reterFrete =
    comEntregador && (opcoes.reterFrete ?? RETER_FRETE_NO_REEMBOLSO);

  const frete = split.valorFrete;
  const detalhamento: string[] = [];

  let valorRetido = 0;
  if (reterComissao) {
    valorRetido += split.comissao;
    detalhamento.push(
      `Comissão retida (${(split.taxaComissao * 100).toFixed(0)}%): ${split.comissao}`,
    );
  }
  if (reterFrete && frete > 0) {
    valorRetido += frete;
    detalhamento.push(`Frete retido (entrega já realizada): ${frete}`);
  }

  const valorReembolsado = split.total - valorRetido;

  // De onde sai o dinheiro do estorno:
  //  - o valor do produto (menos a comissão retida) sai do vendedor;
  //  - o que faltar sai da plataforma;
  //  - o entregador nunca é debitado.
  const debitoVendedor = Math.min(split.valorVendedor, valorReembolsado);
  const debitoPlataforma = valorReembolsado - debitoVendedor;

  detalhamento.push(`Devolvido ao comprador: ${valorReembolsado}`);

  return {
    valorReembolsado,
    valorRetido,
    debitoVendedor,
    debitoPlataforma,
    debitoEntregador: 0,
    detalhamento,
  };
}
