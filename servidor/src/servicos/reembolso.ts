/**
 * Devolução dentro da janela de 7 dias.
 *
 * O caminho completo:
 *   comprador pede  ->  admin aprova  ->  entregador busca no comprador e
 *   devolve ao vendedor  ->  produto de volta  ->  estorno na pagar.me.
 *
 * O estorno só dispara DEPOIS que o vendedor recebe o produto de volta. Isso
 * evita o caso em que o comprador recebe o dinheiro e fica com a mercadoria.
 *
 * O cálculo e a chamada à pagar.me continuam iguais aos que já existiam: o
 * comprador recebe de volta tudo que pagou, a comissão e a tarifa saem do
 * caixa da plataforma e o entregador nunca é debitado.
 */

import { ambiente } from '../ambiente';
import type { ResultadoSplit } from '../dominio/comissao';
import type { ModalidadeEntrega } from '../dominio/regras';
import { calcularReembolso, dentroDaJanelaDeTeste } from '../dominio/reembolso';
import { conflito, naoEncontrado, semPermissao } from '../erros';
import * as pagarme from '../integracoes/pagarme';
import { log } from '../log';
import { prisma } from '../prisma';
import { marcarDevolucaoCombinada } from './entregaDoVendedor';
import { abrirDevolucao } from './logistica';

/** Remonta o split a partir dos valores congelados no pedido. */
function splitDoPedido(pedido: {
  valorProduto: number;
  valorTotal: number;
  valorComissao: number;
  valorTarifa: number;
  taxaComissao: number;
  valorVendedor: number;
  valorPlataforma: number;
  modalidade: string;
}): ResultadoSplit {
  return {
    valorProduto: pedido.valorProduto,
    total: pedido.valorTotal,
    comissao: pedido.valorComissao,
    taxaComissao: pedido.taxaComissao,
    tarifa: pedido.valorTarifa,
    modalidade: pedido.modalidade as ModalidadeEntrega,
    totalDescontado: pedido.valorComissao + pedido.valorTarifa,
    valorVendedor: pedido.valorVendedor,
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

  const calculo = calcularReembolso(splitDoPedido(pedido));
  return {
    valorPago: pedido.valorTotal,
    valorReembolsado: calculo.valorReembolsado,
    valorRetido: calculo.valorRetido,
    explicacao:
      calculo.valorRetido === 0
        ? 'Você recebe de volta tudo que pagou.'
        : 'Parte do valor fica retida conforme combinado para esta devolução.',
    comoFunciona:
      pedido.modalidade === 'PLATAFORMA'
        ? 'Depois que a gente aprovar, um entregador busca o produto no seu endereço. O dinheiro volta assim que o vendedor receber o produto de volta.'
        : 'Depois que a gente aprovar, combine a devolução com quem vendeu. O dinheiro volta assim que a gente confirmar que o produto chegou de volta.',
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

  const calculo = calcularReembolso(splitDoPedido(pedido));

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
      data: { estado: 'DEVOLUCAO_SOLICITADA' },
    });
    await tx.eventoPedido.create({
      data: {
        pedidoId: pedido.id,
        tipo: 'devolucao_solicitada',
        autorId: entrada.compradorId,
        detalhe: { motivo: entrada.motivo, valor: calculo.valorReembolsado },
      },
    });
    return criado;
  });

  log.info({ pedido: pedido.codigo, valor: calculo.valorReembolsado }, 'devolução solicitada');
  return reembolso;
}

/**
 * Aprovação pela equipe.
 *
 * NÃO estorna ainda: abre a coleta reversa. O dinheiro volta quando o produto
 * chegar no vendedor (ver `concluirAposDevolucao`).
 */
export async function aprovar(reembolsoId: string, adminId: string, resposta?: string) {
  const reembolso = await prisma.reembolso.findUnique({
    where: { id: reembolsoId },
    include: { pedido: true },
  });
  if (!reembolso) throw naoEncontrado('Solicitação não encontrada.');
  if (reembolso.estado === 'CONCLUIDO') throw conflito('Este reembolso já foi concluído.');
  if (reembolso.estado === 'APROVADO') throw conflito('Esta devolução já foi aprovada.');

  await prisma.reembolso.update({
    where: { id: reembolso.id },
    data: {
      estado: 'APROVADO',
      analisadoPor: adminId,
      respostaEquipe: resposta ?? null,
      aprovadoEm: new Date(),
    },
  });

  // como o produto volta depende de quem entregou
  let entregaId: string | null = null;
  if (reembolso.pedido.modalidade === 'PLATAFORMA') {
    // corrida de volta: comprador -> vendedor
    const entrega = await abrirDevolucao(reembolso.pedidoId, adminId);
    entregaId = entrega?.id ?? null;
  } else {
    // sem entregador: as partes combinam e o admin confirma o retorno
    await marcarDevolucaoCombinada(reembolso.pedidoId, adminId);
  }

  await prisma.eventoPedido.create({
    data: {
      pedidoId: reembolso.pedidoId,
      tipo: 'devolucao_aprovada',
      autorId: adminId,
      detalhe: { entregaId, modalidade: reembolso.pedido.modalidade },
    },
  });

  log.info(
    { pedido: reembolso.pedido.codigo, modalidade: reembolso.pedido.modalidade },
    'devolução aprovada',
  );
  return prisma.reembolso.findUniqueOrThrow({ where: { id: reembolso.id } });
}

/**
 * Estorna na pagar.me depois que o produto voltou para o vendedor.
 *
 * Chamado pela logística quando a corrida de DEVOLUCAO chega em ENTREGUE.
 * Idempotente: se já estornou, não estorna de novo.
 */
export async function concluirAposDevolucao(pedidoId: string, autorId?: string) {
  const reembolso = await prisma.reembolso.findUnique({
    where: { pedidoId },
    include: {
      pedido: { include: { vendedor: { include: { recebedor: true } } } },
    },
  });
  if (!reembolso) {
    log.warn({ pedidoId }, 'devolução concluída sem reembolso registrado');
    return null;
  }
  if (reembolso.estado === 'CONCLUIDO') return reembolso;

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
   * O entregador não entra aqui de propósito — o serviço dele foi prestado
   * nas duas pontas e ele é pago pela plataforma.
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
        autorId: autorId ?? null,
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
        tipo: 'devolucao_recusada',
        autorId: adminId,
        detalhe: { motivo },
      },
    }),
  ]);

  return prisma.reembolso.findUniqueOrThrow({ where: { id: reembolsoId } });
}
