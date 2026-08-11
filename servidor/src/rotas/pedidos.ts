/**
 * Compra, acompanhamento do pedido e devolução pelo comprador.
 */

import { Router } from 'express';
import { z } from 'zod';

import { ambiente } from '../ambiente';
import { diasRestantesParaTestar, dentroDaJanelaDeTeste } from '../dominio/reembolso';
import { conflito, naoEncontrado, semPermissao } from '../erros';
import { exigirLogin } from '../middlewares/autenticacao';
import { prisma } from '../prisma';
import * as checkout from '../servicos/checkout';
import * as reembolsoServico from '../servicos/reembolso';
import { extratoDoVendedor } from '../servicos/repasse';

export const rotasPedidos = Router();

rotasPedidos.use(exigirLogin);

const modalidade = z.enum(['ENTREGADOR_PROPRIO', 'COMBINADO_ENTRE_PARTES']);

/** GET /pedidos/simular?anuncio=...&modalidade=... — prévia de valores. */
rotasPedidos.get('/simular', async (req, res, next) => {
  try {
    const dados = z.object({ anuncio: z.string(), modalidade }).parse(req.query);
    return res.json(await checkout.simular(dados.anuncio, dados.modalidade));
  } catch (erro) {
    return next(erro);
  }
});

const comprarEsquema = z.object({
  anuncioId: z.string(),
  modalidade,
  enderecoId: z.string().optional(),
  pagamento: z.discriminatedUnion('tipo', [
    z.object({
      tipo: z.literal('credit_card'),
      tokenCartao: z.string().min(5),
      parcelas: z.number().int().min(1).max(12).default(1),
    }),
    z.object({ tipo: z.literal('pix') }),
  ]),
});

/** POST /pedidos — fecha a compra e cobra com split. */
rotasPedidos.post('/', async (req, res, next) => {
  try {
    const dados = comprarEsquema.parse(req.body);
    const resultado = await checkout.comprar({
      ...dados,
      compradorId: req.sessao!.usuarioId,
    });
    return res.status(201).json(resultado);
  } catch (erro) {
    return next(erro);
  }
});

/** A entrega de ida é a que o comprador acompanha. */
const ENTREGA_DE_IDA = {
  where: { tipo: 'ENTREGA' as const },
  take: 1,
  select: {
    id: true,
    estado: true,
    codigoConfirmacao: true,
    entregador: { select: { nome: true, telefone: true } },
  },
};

/** GET /pedidos/compras — "minhas compras". */
rotasPedidos.get('/compras', async (req, res, next) => {
  try {
    const pedidos = await prisma.pedido.findMany({
      where: { compradorId: req.sessao!.usuarioId },
      orderBy: { criadoEm: 'desc' },
      include: {
        anuncio: { select: { titulo: true, fotos: { take: 1, orderBy: { ordem: 'asc' } } } },
        vendedor: { select: { nome: true, apelidoLoja: true } },
        entregas: ENTREGA_DE_IDA,
        reembolso: { select: { estado: true, valorReembolsado: true } },
      },
    });

    const agora = new Date();
    return res.json({
      itens: pedidos.map(({ entregas, ...p }) => ({
        ...p,
        entrega: entregas[0] ?? null,
        // o app mostra "faltam X dias pra testar" direto no card
        podePedirReembolso:
          p.estado === 'ENTREGUE' &&
          !p.reembolso &&
          dentroDaJanelaDeTeste(p.entregueEm, agora, ambiente.DIAS_PARA_TESTAR),
        diasRestantesParaTestar: p.entregueEm
          ? diasRestantesParaTestar(p.entregueEm, agora, ambiente.DIAS_PARA_TESTAR)
          : null,
      })),
    });
  } catch (erro) {
    return next(erro);
  }
});

/** GET /pedidos/vendas — "minhas vendas" com o extrato de repasses. */
rotasPedidos.get('/vendas', async (req, res, next) => {
  try {
    return res.json(await extratoDoVendedor(req.sessao!.usuarioId));
  } catch (erro) {
    return next(erro);
  }
});

async function pedidoDaPessoa(pedidoId: string, usuarioId: string) {
  const pedido = await prisma.pedido.findUnique({
    where: { id: pedidoId },
    include: {
      anuncio: { include: { fotos: { orderBy: { ordem: 'asc' } } } },
      comprador: { select: { id: true, nome: true, fotoUrl: true } },
      vendedor: { select: { id: true, nome: true, apelidoLoja: true, fotoUrl: true } },
      endereco: true,
      entregas: {
        orderBy: { criadoEm: 'asc' },
        select: {
          id: true,
          tipo: true,
          estado: true,
          codigoConfirmacao: true,
          volumes: true,
          entregador: { select: { nome: true, telefone: true } },
        },
      },
      reembolso: true,
      eventos: { orderBy: { criadoEm: 'asc' } },
    },
  });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');
  if (pedido.compradorId !== usuarioId && pedido.vendedorId !== usuarioId) {
    throw semPermissao();
  }
  return pedido;
}

/** GET /pedidos/:id — detalhe com a linha do tempo. */
rotasPedidos.get('/:id', async (req, res, next) => {
  try {
    const pedido = await pedidoDaPessoa(req.params.id, req.sessao!.usuarioId);
    const agora = new Date();
    const souComprador = pedido.compradorId === req.sessao!.usuarioId;

    return res.json({
      ...pedido,
      entrega: pedido.entregas.find((e) => e.tipo === 'ENTREGA') ?? null,
      entregaDevolucao: pedido.entregas.find((e) => e.tipo === 'DEVOLUCAO') ?? null,
      podePedirReembolso:
        souComprador &&
        pedido.estado === 'ENTREGUE' &&
        !pedido.reembolso &&
        dentroDaJanelaDeTeste(pedido.entregueEm, agora, ambiente.DIAS_PARA_TESTAR),
      diasRestantesParaTestar: pedido.entregueEm
        ? diasRestantesParaTestar(pedido.entregueEm, agora, ambiente.DIAS_PARA_TESTAR)
        : null,
    });
  } catch (erro) {
    return next(erro);
  }
});

/**
 * POST /pedidos/:id/recebi
 * Comprador confirma a entrega quando ela foi combinada entre as partes —
 * nesse caso não há entregador para confirmar por ele.
 */
rotasPedidos.post('/:id/recebi', async (req, res, next) => {
  try {
    const pedido = await prisma.pedido.findUnique({ where: { id: req.params.id } });
    if (!pedido) throw naoEncontrado('Pedido não encontrado.');
    if (pedido.compradorId !== req.sessao!.usuarioId) throw semPermissao();
    if (pedido.modalidade !== 'COMBINADO_ENTRE_PARTES') {
      throw conflito('Neste pedido quem confirma a entrega é o entregador.');
    }
    if (pedido.estado !== 'PAGO') {
      throw conflito('Este pedido não está aguardando confirmação de recebimento.');
    }

    const { prazoParaTestar } = await import('../dominio/reembolso');
    const agora = new Date();
    const prazo = prazoParaTestar(agora, ambiente.DIAS_PARA_TESTAR);

    const [atualizado] = await prisma.$transaction([
      prisma.pedido.update({
        where: { id: pedido.id },
        data: { estado: 'ENTREGUE', entregueEm: agora, prazoTesteAte: prazo },
      }),
      prisma.eventoPedido.create({
        data: {
          pedidoId: pedido.id,
          tipo: 'recebimento_confirmado',
          autorId: req.sessao!.usuarioId,
          detalhe: { prazoTesteAte: prazo.toISOString() },
        },
      }),
    ]);

    return res.json(atualizado);
  } catch (erro) {
    return next(erro);
  }
});

/** GET /pedidos/:id/reembolso/previa — mostra quanto volta antes de confirmar. */
rotasPedidos.get('/:id/reembolso/previa', async (req, res, next) => {
  try {
    return res.json(await reembolsoServico.previa(req.params.id, req.sessao!.usuarioId));
  } catch (erro) {
    return next(erro);
  }
});

const reembolsoEsquema = z.object({
  motivo: z.string().min(3).max(120),
  descricao: z.string().max(2000).optional(),
  fotosUrls: z.array(z.string().url()).max(6).optional(),
});

/** POST /pedidos/:id/reembolso — abre a devolução dentro dos 7 dias. */
rotasPedidos.post('/:id/reembolso', async (req, res, next) => {
  try {
    const dados = reembolsoEsquema.parse(req.body);
    const reembolso = await reembolsoServico.solicitar({
      ...dados,
      pedidoId: req.params.id,
      compradorId: req.sessao!.usuarioId,
    });
    return res.status(201).json(reembolso);
  } catch (erro) {
    return next(erro);
  }
});
