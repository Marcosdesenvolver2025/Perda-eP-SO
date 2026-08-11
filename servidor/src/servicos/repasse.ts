/**
 * Liberação do dinheiro depois da janela de teste.
 *
 * Enquanto os 7 dias correm, o valor fica retido no saldo da pagar.me (os
 * recebedores são criados com `transfer_enabled: false`). Quando o prazo
 * vence sem pedido de devolução, o saque é liberado para o vendedor.
 *
 * O entregador é pago assim que conclui a corrida: o serviço dele já acabou e
 * não depende de o comprador aprovar o produto.
 */

import { ambiente } from '../ambiente';
import * as pagarme from '../integracoes/pagarme';
import { log } from '../log';
import { prisma } from '../prisma';

/**
 * Libera o repasse de todos os pedidos cujo prazo de teste venceu.
 * Roda de hora em hora (ver `tarefas.ts`).
 */
export async function liberarRepassesVencidos(agora: Date = new Date()) {
  const pedidos = await prisma.pedido.findMany({
    where: {
      estado: 'ENTREGUE',
      repassadoEm: null,
      prazoTesteAte: { lte: agora },
      // pedido em devolução não repassa: o dinheiro precisa continuar retido
      reembolso: { is: null },
    },
    include: { vendedor: { include: { recebedor: true } } },
    take: 200,
  });

  const resultados = { liberados: 0, falhas: 0 };

  for (const pedido of pedidos) {
    const recebedor = pedido.vendedor.recebedor;
    if (!recebedor || recebedor.estado !== 'ATIVO') {
      log.warn(
        { pedido: pedido.codigo },
        'repasse adiado: vendedor sem recebedor ativo',
      );
      resultados.falhas++;
      continue;
    }

    try {
      const saque = await pagarme.transferir(
        recebedor.recipientId,
        pedido.valorVendedor,
        `repasse-${pedido.id}`, // idempotência: não paga duas vezes
      );

      await prisma.$transaction([
        prisma.pedido.update({
          where: { id: pedido.id },
          data: { estado: 'CONCLUIDO', repassadoEm: new Date() },
        }),
        prisma.eventoPedido.create({
          data: {
            pedidoId: pedido.id,
            tipo: 'repasse_liberado',
            detalhe: { valor: pedido.valorVendedor, saqueId: saque.id },
          },
        }),
      ]);

      resultados.liberados++;
      log.info(
        { pedido: pedido.codigo, valor: pedido.valorVendedor },
        'repasse liberado ao vendedor',
      );
    } catch (erro) {
      // não derruba o lote: o próximo ciclo tenta de novo
      resultados.falhas++;
      log.error({ erro, pedido: pedido.codigo }, 'falha ao liberar repasse');
    }
  }

  return resultados;
}

/**
 * Paga o entregador pela corrida concluída.
 *
 * O valor sai do caixa da plataforma (da tarifa fixa), não de uma cobrança
 * ao comprador — por isso é uma transferência avulsa e não parte do split.
 * Idempotente pela chave `entregador-{id da entrega}`.
 */
export async function pagarEntregador(entregaId: string) {
  const entrega = await prisma.entrega.findUnique({
    where: { id: entregaId },
    include: {
      entregador: { include: { recebedor: true } },
      pedido: { select: { codigo: true } },
    },
  });

  if (!entrega || entrega.estado !== 'ENTREGUE') return null;
  if (entrega.valorEntregador <= 0) return null;

  const recebedor = entrega.entregador?.recebedor;
  if (!recebedor || recebedor.estado !== 'ATIVO') {
    log.warn(
      { entrega: entregaId },
      'pagamento do entregador adiado: recebedor não está ativo',
    );
    return null;
  }

  const saque = await pagarme.transferir(
    recebedor.recipientId,
    entrega.valorEntregador,
    `entregador-${entrega.id}`,
  );

  await prisma.eventoPedido.create({
    data: {
      pedidoId: entrega.pedidoId,
      tipo: 'entregador_pago',
      autorId: entrega.entregadorId,
      detalhe: {
        valor: entrega.valorEntregador,
        saqueId: saque.id,
        tipoCorrida: entrega.tipo,
      },
    },
  });

  log.info(
    { entrega: entregaId, valor: entrega.valorEntregador },
    'entregador pago',
  );
  return saque;
}

/**
 * Quanto o vendedor tem a receber e quando cai — é o que aparece na tela
 * "minhas vendas" do app.
 */
export async function extratoDoVendedor(vendedorId: string) {
  const pedidos = await prisma.pedido.findMany({
    where: {
      vendedorId,
      estado: {
        in: [
          'PAGO',
          'AGUARDANDO_AGENDAMENTO_DE_COLETA',
          'A_CAMINHO_DA_COLETA',
          'PRODUTO_COLETADO',
          'EM_ROTA_PARA_ENTREGA',
          'ENTREGUE',
          'CONCLUIDO',
        ],
      },
    },
    orderBy: { criadoEm: 'desc' },
    select: {
      id: true,
      codigo: true,
      estado: true,
      valorProduto: true,
      valorComissao: true,
      valorTarifa: true,
      taxaComissao: true,
      valorVendedor: true,
      prazoTesteAte: true,
      repassadoEm: true,
      anuncio: { select: { titulo: true } },
    },
  });

  const aReceber = pedidos
    .filter((p) => !p.repassadoEm && p.estado !== 'CONCLUIDO')
    .reduce((acc, p) => acc + p.valorVendedor, 0);
  const recebido = pedidos
    .filter((p) => p.repassadoEm)
    .reduce((acc, p) => acc + p.valorVendedor, 0);

  return {
    aReceber,
    recebido,
    diasParaLiberar: ambiente.DIAS_PARA_TESTAR,
    pedidos,
  };
}

/** Quanto o entregador ganhou — tela da área dele. */
export async function extratoDoEntregador(entregadorId: string) {
  const entregas = await prisma.entrega.findMany({
    where: { entregadorId, estado: 'ENTREGUE' },
    orderBy: { entregueEm: 'desc' },
    select: { id: true, valorEntregador: true, entregueEm: true, tipo: true },
  });

  const hoje = new Date();
  hoje.setHours(0, 0, 0, 0);

  return {
    totalCorridas: entregas.length,
    ganhoTotal: entregas.reduce((acc, e) => acc + e.valorEntregador, 0),
    ganhoHoje: entregas
      .filter((e) => e.entregueEm && e.entregueEm >= hoje)
      .reduce((acc, e) => acc + e.valorEntregador, 0),
    valorPorCorrida: ambiente.PAGAMENTO_POR_ENTREGA,
  };
}
