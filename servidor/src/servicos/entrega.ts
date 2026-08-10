/**
 * Fluxo da entrega feita pelos entregadores do Vendas Itinga.
 *
 * Estados: AGUARDANDO_ENTREGADOR -> ACEITA -> COLETADA -> ENTREGUE.
 * A confirmação de entrega é o gatilho da janela de 4 dias para testar.
 */

import { ambiente } from '../ambiente';
import { prazoParaTestar } from '../dominio/reembolso';
import { conflito, naoEncontrado, semPermissao } from '../erros';
import { log } from '../log';
import { prisma } from '../prisma';

/** Entregas ainda sem dono, para a lista da área do entregador. */
export function entregasDisponiveis() {
  return prisma.entrega.findMany({
    where: { estado: 'AGUARDANDO_ENTREGADOR' },
    orderBy: { criadoEm: 'asc' },
    include: {
      pedido: {
        select: {
          codigo: true,
          valorProduto: true,
          anuncio: {
            select: {
              titulo: true,
              pesoG: true,
              comprimentoCm: true,
              larguraCm: true,
              alturaCm: true,
              fotos: { take: 1, orderBy: { ordem: 'asc' } },
            },
          },
        },
      },
    },
  });
}

/** Entregas do entregador logado (as que ele aceitou e ainda não fechou). */
export function minhasEntregas(entregadorId: string) {
  return prisma.entrega.findMany({
    where: { entregadorId },
    orderBy: { atualizadoEm: 'desc' },
    include: {
      pedido: {
        select: {
          codigo: true,
          comprador: { select: { nome: true, telefone: true } },
          vendedor: { select: { nome: true, telefone: true } },
          anuncio: { select: { titulo: true } },
        },
      },
    },
  });
}

export async function aceitar(entregaId: string, entregadorId: string) {
  // updateMany com filtro de estado evita dois entregadores pegarem a mesma
  // entrega ao mesmo tempo: quem chegar primeiro leva.
  const resultado = await prisma.entrega.updateMany({
    where: { id: entregaId, estado: 'AGUARDANDO_ENTREGADOR', entregadorId: null },
    data: { entregadorId, estado: 'ACEITA', aceitaEm: new Date() },
  });

  if (resultado.count === 0) {
    throw conflito('Esta entrega já foi aceita por outro entregador.');
  }

  const entrega = await prisma.entrega.findUniqueOrThrow({ where: { id: entregaId } });
  await prisma.eventoPedido.create({
    data: { pedidoId: entrega.pedidoId, tipo: 'entrega_aceita', autorId: entregadorId },
  });
  return entrega;
}

async function entregaDoEntregador(entregaId: string, entregadorId: string) {
  const entrega = await prisma.entrega.findUnique({ where: { id: entregaId } });
  if (!entrega) throw naoEncontrado('Entrega não encontrada.');
  if (entrega.entregadorId !== entregadorId) {
    throw semPermissao('Esta entrega é de outro entregador.');
  }
  return entrega;
}

export async function confirmarColeta(entregaId: string, entregadorId: string) {
  const entrega = await entregaDoEntregador(entregaId, entregadorId);
  if (entrega.estado !== 'ACEITA') {
    throw conflito('A coleta só pode ser confirmada em uma entrega aceita.');
  }

  const [atualizada] = await prisma.$transaction([
    prisma.entrega.update({
      where: { id: entregaId },
      data: { estado: 'COLETADA', coletadaEm: new Date() },
    }),
    prisma.pedido.update({
      where: { id: entrega.pedidoId },
      data: { estado: 'A_CAMINHO' },
    }),
    prisma.eventoPedido.create({
      data: { pedidoId: entrega.pedidoId, tipo: 'coletado', autorId: entregadorId },
    }),
  ]);
  return atualizada;
}

/**
 * Confirma a entrega ao comprador.
 *
 * O entregador digita o código de 4 dígitos que o comprador mostra no app.
 * É esse passo que inicia a contagem dos 4 dias para testar o produto.
 */
export async function confirmarEntrega(
  entregaId: string,
  entregadorId: string,
  codigoInformado: string,
  fotoComprovanteUrl?: string,
) {
  const entrega = await entregaDoEntregador(entregaId, entregadorId);
  if (entrega.estado !== 'COLETADA') {
    throw conflito('Confirme a coleta antes de confirmar a entrega.');
  }
  if (entrega.codigoConfirmacao && entrega.codigoConfirmacao !== codigoInformado.trim()) {
    throw conflito('Código de confirmação incorreto. Confira com o comprador.');
  }

  const agora = new Date();
  const prazo = prazoParaTestar(agora, ambiente.DIAS_PARA_TESTAR);

  await prisma.$transaction([
    prisma.entrega.update({
      where: { id: entregaId },
      data: {
        estado: 'ENTREGUE',
        entregueEm: agora,
        fotoComprovanteUrl: fotoComprovanteUrl ?? null,
      },
    }),
    prisma.pedido.update({
      where: { id: entrega.pedidoId },
      data: { estado: 'ENTREGUE', entregueEm: agora, prazoTesteAte: prazo },
    }),
    prisma.eventoPedido.create({
      data: {
        pedidoId: entrega.pedidoId,
        tipo: 'entregue',
        autorId: entregadorId,
        detalhe: { prazoTesteAte: prazo.toISOString() },
      },
    }),
  ]);

  log.info(
    { entrega: entregaId, prazoTesteAte: prazo },
    'entrega confirmada, janela de teste aberta',
  );
  return prisma.entrega.findUniqueOrThrow({ where: { id: entregaId } });
}

/**
 * Entrega combinada entre comprador e vendedor: quem confirma o recebimento
 * é o próprio comprador, pelo app. A janela de 4 dias começa igual.
 */
export async function compradorConfirmaRecebimento(
  pedidoId: string,
  compradorId: string,
) {
  const pedido = await prisma.pedido.findUnique({ where: { id: pedidoId } });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');
  if (pedido.compradorId !== compradorId) throw semPermissao();
  if (pedido.modalidade !== 'COMBINADO_ENTRE_PARTES') {
    throw conflito('Neste pedido quem confirma a entrega é o entregador.');
  }
  if (pedido.estado !== 'PAGO' && pedido.estado !== 'EM_SEPARACAO') {
    throw conflito('Este pedido não está aguardando confirmação de recebimento.');
  }

  const agora = new Date();
  const prazo = prazoParaTestar(agora, ambiente.DIAS_PARA_TESTAR);

  await prisma.$transaction([
    prisma.pedido.update({
      where: { id: pedidoId },
      data: { estado: 'ENTREGUE', entregueEm: agora, prazoTesteAte: prazo },
    }),
    prisma.eventoPedido.create({
      data: {
        pedidoId,
        tipo: 'recebimento_confirmado',
        autorId: compradorId,
        detalhe: { prazoTesteAte: prazo.toISOString() },
      },
    }),
  ]);

  return prisma.pedido.findUniqueOrThrow({ where: { id: pedidoId } });
}
