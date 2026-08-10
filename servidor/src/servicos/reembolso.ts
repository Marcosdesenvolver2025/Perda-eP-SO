/**
 * Reembolso dentro da janela de 4 dias.
 *
 * O comprador abre o pedido pelo app; a equipe aprova ou recusa. Aprovado, o
 * estorno é feito na pagar.me com o split explícito, garantindo que:
 *   - a comissão e o frete fiquem retidos quando a entrega foi nossa;
 *   - o valor do produto saia do saldo do vendedor (que ainda está retido);
 *   - o entregador não seja debitado — ele já prestou o serviço.
 */

import { ambiente } from '../ambiente';
import type { ResultadoSplit } from '../dominio/comissao';
import { calcularReembolso, dentroDaJanelaDeTeste } from '../dominio/reembolso';
import { conflito, naoEncontrado, semPermissao } from '../erros';
import * as pagarme from '../integracoes/pagarme';
import { log } from '../log';
import { prisma } from '../prisma';

/** Remonta o split a partir dos valores congelados no pedido. */
function splitDoPedido(pedido: {
  valorProduto: number;
  valorFrete: number;
  valorTotal: number;
  valorComissao: number;
  taxaComissao: number;
  valorVendedor: number;
  valorEntregador: number;
  valorPlataforma: number;
}): ResultadoSplit {
  return {
    valorProduto: pedido.valorProduto,
    valorFrete: pedido.valorFrete,
    total: pedido.valorTotal,
    comissao: pedido.valorComissao,
    taxaComissao: pedido.taxaComissao,
    valorVendedor: pedido.valorVendedor,
    valorEntregador: pedido.valorEntregador,
    valorPlataforma: pedido.valorPlataforma,
  };
}

export interface SolicitacaoDeReembolso {
  pedidoId: string;
  compradorId: string;
  motivo: string;
  descricao?: string;
  fotosUrls?: string[];
}

/** Prévia mostrada ao comprador ANTES de ele confirmar o pedido de devolução. */
export async function previa(pedidoId: string, compradorId: string) {
  const pedido = await prisma.pedido.findUnique({ where: { id: pedidoId } });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');
  if (pedido.compradorId !== compradorId) throw semPermissao();

  const calculo = calcularReembolso(splitDoPedido(pedido), pedido.modalidade);
  return {
    valorPago: pedido.valorTotal,
    valorReembolsado: calculo.valorReembolsado,
    valorRetido: calculo.valorRetido,
    // deixa explícito para o comprador por que a taxa não volta
    explicacao:
      pedido.modalidade === 'ENTREGADOR_PROPRIO'
        ? 'A comissão e o frete não são devolvidos porque a entrega já foi feita pelos nossos entregadores.'
        : 'Você recebe todo o valor de volta.',
    prazoTesteAte: pedido.prazoTesteAte,
  };
}

export async function solicitar(entrada: SolicitacaoDeReembolso) {
  const pedido = await prisma.pedido.findUnique({
    where: { id: entrada.pedidoId },
    include: { reembolso: true },
  });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');
  if (pedido.compradorId !== entrada.compradorId) throw semPermissao();
  if (pedido.reembolso) throw conflito('Já existe um pedido de devolução para esta compra.');
  if (pedido.estado !== 'ENTREGUE') {
    throw conflito('A devolução só pode ser pedida depois que o produto chega.');
  }
  if (!dentroDaJanelaDeTeste(pedido.entregueEm, new Date(), ambiente.DIAS_PARA_TESTAR)) {
    throw conflito(
      `O prazo de ${ambiente.DIAS_PARA_TESTAR} dias para testar e devolver já venceu.`,
    );
  }

  const calculo = calcularReembolso(splitDoPedido(pedido), pedido.modalidade);

  const reembolso = await prisma.$transaction(async (tx) => {
    const criado = await tx.reembolso.create({
      data: {
        pedidoId: pedido.id,
        estado: 'SOLICITADO',
        motivo: entrada.motivo,
        descricao: entrada.descricao ?? null,
        fotosUrls: entrada.fotosUrls ?? [],
        valorReembolsado: calculo.valorReembolsado,
        valorRetido: calculo.valorRetido,
        debitoVendedor: calculo.debitoVendedor,
        debitoPlataforma: calculo.debitoPlataforma,
      },
    });
    await tx.pedido.update({
      where: { id: pedido.id },
      data: { estado: 'EM_DEVOLUCAO' },
    });
    await tx.eventoPedido.create({
      data: {
        pedidoId: pedido.id,
        tipo: 'reembolso_solicitado',
        autorId: entrada.compradorId,
        detalhe: { motivo: entrada.motivo, valor: calculo.valorReembolsado },
      },
    });
    return criado;
  });

  log.info({ pedido: pedido.codigo, valor: calculo.valorReembolsado }, 'reembolso solicitado');
  return reembolso;
}

/** Aprovação pela equipe: estorna na pagar.me e fecha o pedido. */
export async function aprovar(reembolsoId: string, adminId: string, resposta?: string) {
  const reembolso = await prisma.reembolso.findUnique({
    where: { id: reembolsoId },
    include: {
      pedido: {
        include: { vendedor: { include: { recebedor: true } } },
      },
    },
  });
  if (!reembolso) throw naoEncontrado('Solicitação não encontrada.');
  if (reembolso.estado === 'CONCLUIDO') throw conflito('Este reembolso já foi concluído.');

  const { pedido } = reembolso;
  if (!pedido.pagarmeChargeId) {
    throw conflito('Pedido sem cobrança na pagar.me — verifique manualmente.');
  }

  const recebedorVendedor = pedido.vendedor.recebedor;
  if (!recebedorVendedor) {
    throw conflito('Vendedor sem recebedor cadastrado — verifique manualmente.');
  }

  /**
   * Split do estorno: cada centavo devolvido sai de um saldo específico.
   * Não incluímos o entregador aqui de propósito.
   */
  const splitDoEstorno = [
    {
      amount: reembolso.debitoVendedor,
      recipient_id: recebedorVendedor.recipientId,
      type: 'flat' as const,
      options: { charge_processing_fee: false, charge_remainder_fee: false, liable: true },
    },
    {
      amount: reembolso.debitoPlataforma,
      recipient_id: ambiente.PAGARME_RECEBEDOR_PLATAFORMA,
      type: 'flat' as const,
      options: { charge_processing_fee: true, charge_remainder_fee: true, liable: true },
    },
  ].filter((regra) => regra.amount > 0);

  const estorno = await pagarme.estornar(
    {
      chargeId: pedido.pagarmeChargeId,
      valor: reembolso.valorReembolsado,
      split: splitDoEstorno,
    },
    `estorno-${reembolso.id}`,
  );

  await prisma.$transaction([
    prisma.reembolso.update({
      where: { id: reembolso.id },
      data: {
        estado: 'CONCLUIDO',
        analisadoPor: adminId,
        respostaEquipe: resposta ?? null,
        pagarmeEstornoId: estorno.id,
        concluidoEm: new Date(),
      },
    }),
    prisma.pedido.update({
      where: { id: pedido.id },
      data: { estado: 'REEMBOLSADO' },
    }),
    prisma.eventoPedido.create({
      data: {
        pedidoId: pedido.id,
        tipo: 'reembolso_concluido',
        autorId: adminId,
        detalhe: {
          valorReembolsado: reembolso.valorReembolsado,
          valorRetido: reembolso.valorRetido,
          estornoId: estorno.id,
        },
      },
    }),
  ]);

  log.info({ pedido: pedido.codigo, estorno: estorno.id }, 'reembolso concluído');
  return prisma.reembolso.findUniqueOrThrow({ where: { id: reembolso.id } });
}

export async function recusar(reembolsoId: string, adminId: string, motivo: string) {
  const reembolso = await prisma.reembolso.findUnique({ where: { id: reembolsoId } });
  if (!reembolso) throw naoEncontrado('Solicitação não encontrada.');
  if (reembolso.estado === 'CONCLUIDO') throw conflito('Este reembolso já foi concluído.');

  await prisma.$transaction([
    prisma.reembolso.update({
      where: { id: reembolsoId },
      data: {
        estado: 'RECUSADO',
        analisadoPor: adminId,
        respostaEquipe: motivo,
        concluidoEm: new Date(),
      },
    }),
    // volta para ENTREGUE: o repasse ao vendedor volta a correr normalmente
    prisma.pedido.update({
      where: { id: reembolso.pedidoId },
      data: { estado: 'ENTREGUE' },
    }),
    prisma.eventoPedido.create({
      data: {
        pedidoId: reembolso.pedidoId,
        tipo: 'reembolso_recusado',
        autorId: adminId,
        detalhe: { motivo },
      },
    }),
  ]);

  return prisma.reembolso.findUniqueOrThrow({ where: { id: reembolsoId } });
}
