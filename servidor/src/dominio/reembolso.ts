/**
 * Janela de teste de 7 dias e cálculo do reembolso.
 *
 * Como funciona para o comprador:
 *  1. ele compra e o dinheiro fica retido (não vai para o vendedor ainda);
 *  2. o produto chega -> começa a contar 7 dias corridos para testar;
 *  3. dentro desses 7 dias ele pode pedir devolução pelo app;
 *  4. passados os 7 dias sem pedido, o repasse ao vendedor é liberado.
 *
 * O prazo e a devolução integral vêm do artigo 49 do Código de Defesa do
 * Consumidor. Dentro da janela, o comprador recebe de volta TUDO que pagou:
 * a comissão e a tarifa saem do caixa da plataforma e o valor líquido do
 * produto sai do saldo retido do vendedor. O entregador não devolve nada —
 * ele prestou o serviço e é pago pela plataforma de qualquer jeito.
 */

import {
  DIAS_PARA_TESTAR,
  RETER_COMISSAO_NO_REEMBOLSO,
  RETER_TARIFA_NO_REEMBOLSO,
} from './regras';
import type { ResultadoSplit } from './comissao';

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
  reterTarifa?: boolean;
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
  detalhamento: string[];
}

/**
 * Calcula o estorno de um pedido.
 *
 * Por padrão a devolução é INTEGRAL, como manda o art. 49 do CDC. `opcoes`
 * permite reter comissão e tarifa em devoluções negociadas fora do prazo
 * legal — leia o aviso em `regras.ts` antes de usar.
 */
export function calcularReembolso(
  split: ResultadoSplit,
  opcoes: OpcoesReembolso = {},
): CalculoReembolso {
  const reterComissao = opcoes.reterComissao ?? RETER_COMISSAO_NO_REEMBOLSO;
  const reterTarifa = opcoes.reterTarifa ?? RETER_TARIFA_NO_REEMBOLSO;

  const detalhamento: string[] = [];
  let valorRetido = 0;

  if (reterComissao) {
    valorRetido += split.comissao;
    detalhamento.push(
      `Comissão retida (${(split.taxaComissao * 100).toFixed(0)}%): ${split.comissao}`,
    );
  }
  if (reterTarifa) {
    valorRetido += split.tarifa;
    detalhamento.push(`Tarifa fixa retida: ${split.tarifa}`);
  }

  const valorReembolsado = split.total - valorRetido;

  // De onde sai o dinheiro do estorno:
  //  - o líquido que o vendedor recebeu sai do saldo dele;
  //  - o que faltar (comissão + tarifa) sai da plataforma.
  const debitoVendedor = Math.min(split.valorVendedor, valorReembolsado);
  const debitoPlataforma = valorReembolsado - debitoVendedor;

  detalhamento.push(`Devolvido ao comprador: ${valorReembolsado}`);

  return {
    valorReembolsado,
    valorRetido,
    debitoVendedor,
    debitoPlataforma,
    detalhamento,
  };
}
