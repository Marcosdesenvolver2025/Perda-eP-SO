/**
 * Modalidade VENDEDOR: quando quem entrega é o próprio vendedor.
 *
 * Não há entregador, não há fila, não há máquina de estados da logística. A
 * prova de entrega vem de duas coisas:
 *
 *  1. **Código de confirmação** — o sistema gera um código no pedido, visível
 *     só para o comprador. Na entrega, o comprador informa o código e o
 *     vendedor digita no app. Código certo marca ENTREGUE na hora.
 *
 *  2. **Confirmação automática por prazo** — se o vendedor não conseguir o
 *     código (comprador sumiu, por exemplo), ele pode DECLARAR a entrega. O
 *     comprador então tem 3 dias para confirmar ou abrir devolução; passado o
 *     prazo sem nenhum dos dois, o sistema confirma sozinho.
 *
 * Em qualquer um dos caminhos, o efeito é o mesmo: `ENTREGUE` com data e hora,
 * o que abre a janela de 7 dias e libera o repasse quando ela vencer. Este
 * arquivo NÃO cria repasse: quem faz isso é `repasse.ts`, como já fazia.
 */

import { ambiente } from '../ambiente';
import { gerarCodigoConfirmacao } from '../dominio/logistica';
import { prazoParaTestar } from '../dominio/reembolso';
import { conflito, erroDeValidacao, naoEncontrado, semPermissao } from '../erros';
import { log } from '../log';
import { prisma } from '../prisma';
import { avisar, avisos } from './notificacoes';

const UM_DIA_MS = 24 * 60 * 60 * 1000;

/** Estados em que o vendedor ainda pode registrar a entrega. */
const ABERTO_PARA_ENTREGA = ['AGUARDANDO_ENTREGA_DO_VENDEDOR', 'ENTREGA_DECLARADA'] as const;

/**
 * Prepara o pedido da modalidade VENDEDOR assim que o pagamento confirma.
 * Gera o código e avisa as duas partes. Idempotente.
 */
export async function abrirEntregaDoVendedor(pedidoId: string) {
  const pedido = await prisma.pedido.findUnique({
    where: { id: pedidoId },
    include: { anuncio: { select: { titulo: true } } },
  });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');
  if (pedido.modalidade !== 'VENDEDOR') return pedido;
  if (pedido.codigoConfirmacao) return pedido; // já preparado

  const atualizado = await prisma.$transaction(async (tx) => {
    const p = await tx.pedido.update({
      where: { id: pedido.id },
      data: {
        estado: 'AGUARDANDO_ENTREGA_DO_VENDEDOR',
        codigoConfirmacao: gerarCodigoConfirmacao(),
      },
    });
    await tx.eventoPedido.create({
      data: { pedidoId: pedido.id, tipo: 'entrega_do_vendedor_aberta' },
    });
    return p;
  });

  await avisar(pedido.vendedorId, avisos.combineAEntrega(pedido.anuncio.titulo));
  await avisar(pedido.compradorId, avisos.combineComOVendedor(pedido.anuncio.titulo));

  log.info({ pedido: pedido.codigo }, 'entrega pelo vendedor aberta');
  return atualizado;
}

/**
 * O vendedor digita o código que o comprador mostrou.
 * Código certo -> ENTREGUE na hora, com data e hora registradas.
 */
export async function confirmarComCodigo(
  pedidoId: string,
  vendedorId: string,
  codigoInformado: string,
) {
  const pedido = await prisma.pedido.findUnique({ where: { id: pedidoId } });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');
  if (pedido.vendedorId !== vendedorId) throw semPermissao('Esta venda é de outra pessoa.');
  if (pedido.modalidade !== 'VENDEDOR') {
    throw conflito('Neste pedido quem confirma a entrega é o entregador.');
  }
  if (!ABERTO_PARA_ENTREGA.includes(pedido.estado as never)) {
    throw conflito('Este pedido não está aguardando entrega.');
  }
  if (!pedido.codigoConfirmacao) {
    throw conflito('Este pedido não tem código de confirmação.');
  }

  // comparação exata: código errado não marca entrega, e é isso que dá valor
  // à confirmação
  if (codigoInformado.trim() !== pedido.codigoConfirmacao) {
    await prisma.eventoPedido.create({
      data: { pedidoId: pedido.id, tipo: 'codigo_incorreto', autorId: vendedorId },
    });
    throw erroDeValidacao('Código incorreto. Confira com quem está recebendo.');
  }

  return marcarEntregue(pedido.id, 'codigo', vendedorId);
}

/**
 * O vendedor declara a entrega sem ter o código.
 *
 * Não marca ENTREGUE: abre um prazo de 3 dias para o comprador confirmar ou
 * abrir devolução. É a saída para quando o comprador some.
 */
export async function declararEntrega(pedidoId: string, vendedorId: string) {
  const pedido = await prisma.pedido.findUnique({
    where: { id: pedidoId },
    include: { anuncio: { select: { titulo: true } } },
  });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');
  if (pedido.vendedorId !== vendedorId) throw semPermissao('Esta venda é de outra pessoa.');
  if (pedido.modalidade !== 'VENDEDOR') {
    throw conflito('Neste pedido quem confirma a entrega é o entregador.');
  }
  if (pedido.estado !== 'AGUARDANDO_ENTREGA_DO_VENDEDOR') {
    throw conflito('Este pedido não está aguardando entrega.');
  }

  const agora = new Date();
  const prazo = new Date(
    agora.getTime() + ambiente.DIAS_PARA_CONFIRMACAO_AUTOMATICA * UM_DIA_MS,
  );

  const atualizado = await prisma.$transaction(async (tx) => {
    const p = await tx.pedido.update({
      where: { id: pedido.id },
      data: {
        estado: 'ENTREGA_DECLARADA',
        entregaDeclaradaEm: agora,
        prazoConfirmacaoAte: prazo,
      },
    });
    await tx.eventoPedido.create({
      data: {
        pedidoId: pedido.id,
        tipo: 'entrega_declarada',
        autorId: vendedorId,
        detalhe: { prazoConfirmacaoAte: prazo.toISOString() },
      },
    });
    return p;
  });

  await avisar(
    pedido.compradorId,
    avisos.confirmeORecebimento(
      pedido.anuncio.titulo,
      ambiente.DIAS_PARA_CONFIRMACAO_AUTOMATICA,
    ),
  );

  log.info({ pedido: pedido.codigo, prazo }, 'entrega declarada pelo vendedor');
  return atualizado;
}

/** O comprador confirma que recebeu, sem esperar o prazo. */
export async function compradorConfirma(pedidoId: string, compradorId: string) {
  const pedido = await prisma.pedido.findUnique({ where: { id: pedidoId } });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');
  if (pedido.compradorId !== compradorId) throw semPermissao();
  if (pedido.modalidade !== 'VENDEDOR') {
    throw conflito('Neste pedido quem confirma a entrega é o entregador.');
  }
  if (!ABERTO_PARA_ENTREGA.includes(pedido.estado as never)) {
    throw conflito('Este pedido não está aguardando confirmação.');
  }

  return marcarEntregue(pedido.id, 'comprador', compradorId);
}

/**
 * Marca o pedido como entregue e abre a janela de 7 dias.
 * É o único caminho para ENTREGUE nesta modalidade — os três fluxos passam aqui.
 */
async function marcarEntregue(
  pedidoId: string,
  por: 'codigo' | 'comprador' | 'automatica',
  autorId?: string,
) {
  const agora = new Date();
  const prazoTeste = prazoParaTestar(agora, ambiente.DIAS_PARA_TESTAR);

  const [pedido] = await prisma.$transaction([
    prisma.pedido.update({
      where: { id: pedidoId },
      data: {
        estado: 'ENTREGUE',
        entregueEm: agora,
        prazoTesteAte: prazoTeste,
        confirmadaPor: por,
      },
      include: { anuncio: { select: { titulo: true } } },
    }),
    prisma.eventoPedido.create({
      data: {
        pedidoId,
        tipo: 'entrega_confirmada',
        autorId: autorId ?? null,
        detalhe: { por, prazoTesteAte: prazoTeste.toISOString() },
      },
    }),
  ]);

  await avisar(pedido.compradorId, avisos.entregue(ambiente.DIAS_PARA_TESTAR));
  await avisar(
    pedido.vendedorId,
    avisos.vendaConcluida(
      (pedido.valorVendedor / 100).toLocaleString('pt-BR', {
        style: 'currency',
        currency: 'BRL',
      }),
    ),
  );

  log.info({ pedido: pedido.codigo, por }, 'entrega confirmada (modalidade vendedor)');
  return pedido;
}

/**
 * Confirma automaticamente as entregas declaradas cujo prazo venceu.
 *
 * Roda no ciclo periódico (ver `tarefas.ts`). Só pega pedidos que continuam em
 * ENTREGA_DECLARADA — se o comprador confirmou ou abriu devolução, o estado já
 * mudou e o pedido nem aparece aqui.
 */
export async function confirmarEntregasVencidas(agora: Date = new Date()) {
  const pendentes = await prisma.pedido.findMany({
    where: {
      modalidade: 'VENDEDOR',
      estado: 'ENTREGA_DECLARADA',
      prazoConfirmacaoAte: { lte: agora },
      reembolso: { is: null },
    },
    select: { id: true, codigo: true },
    take: 200,
  });

  const resultado = { confirmados: 0, falhas: 0 };

  for (const pedido of pendentes) {
    try {
      await marcarEntregue(pedido.id, 'automatica');
      resultado.confirmados++;
    } catch (erro) {
      resultado.falhas++;
      log.error({ erro, pedido: pedido.codigo }, 'falha na confirmação automática');
    }
  }

  if (resultado.confirmados) {
    log.info(resultado, 'entregas confirmadas automaticamente');
  }
  return resultado;
}

/**
 * Devolução na modalidade VENDEDOR: não há entregador para coletar.
 *
 * Aprovada pelo admin, comprador e vendedor combinam a devolução entre si. O
 * admin confirma o retorno do produto e só então o estorno dispara — mesmo
 * princípio da coleta reversa: o dinheiro não volta antes do produto.
 */
export async function marcarDevolucaoCombinada(pedidoId: string, adminId: string) {
  const pedido = await prisma.pedido.findUnique({ where: { id: pedidoId } });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');

  await prisma.$transaction([
    prisma.pedido.update({
      where: { id: pedidoId },
      data: { estado: 'DEVOLUCAO_COMBINADA' },
    }),
    prisma.eventoPedido.create({
      data: { pedidoId, tipo: 'devolucao_combinada', autorId: adminId },
    }),
  ]);

  await avisar(pedido.compradorId, avisos.devolucaoCombinada());
  await avisar(pedido.vendedorId, avisos.devolucaoCombinada());

  return prisma.pedido.findUniqueOrThrow({ where: { id: pedidoId } });
}

/**
 * O admin confirma que o vendedor recebeu o produto de volta.
 * Dispara o estorno já existente — não cria fluxo financeiro novo.
 */
export async function confirmarRetornoDoProduto(pedidoId: string, adminId: string) {
  const pedido = await prisma.pedido.findUnique({
    where: { id: pedidoId },
    include: { reembolso: true },
  });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');
  if (!pedido.reembolso) throw conflito('Este pedido não tem devolução aberta.');
  if (pedido.reembolso.estado === 'CONCLUIDO') {
    throw conflito('Esta devolução já foi concluída.');
  }

  await prisma.$transaction([
    prisma.pedido.update({
      where: { id: pedidoId },
      data: { estado: 'DEVOLVIDO_AO_VENDEDOR' },
    }),
    prisma.eventoPedido.create({
      data: { pedidoId, tipo: 'retorno_confirmado', autorId: adminId },
    }),
  ]);

  const { concluirAposDevolucao } = await import('./reembolso');
  return concluirAposDevolucao(pedidoId, adminId);
}
