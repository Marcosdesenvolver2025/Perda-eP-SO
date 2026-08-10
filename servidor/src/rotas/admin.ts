/**
 * Painel interno: análise de devoluções e operação dos repasses.
 */

import { Router } from 'express';
import { z } from 'zod';

import { exigirLogin, exigirPapel } from '../middlewares/autenticacao';
import { prisma } from '../prisma';
import * as reembolsoServico from '../servicos/reembolso';
import { liberarRepassesVencidos } from '../servicos/repasse';

export const rotasAdmin = Router();

rotasAdmin.use(exigirLogin, exigirPapel('ADMIN'));

/** GET /admin/reembolsos — fila de devoluções a analisar. */
rotasAdmin.get('/reembolsos', async (req, res, next) => {
  try {
    const { estado } = z
      .object({
        estado: z.enum(['SOLICITADO', 'EM_ANALISE', 'APROVADO', 'RECUSADO', 'CONCLUIDO']).optional(),
      })
      .parse(req.query);

    const itens = await prisma.reembolso.findMany({
      where: estado ? { estado } : { estado: { in: ['SOLICITADO', 'EM_ANALISE'] } },
      orderBy: { solicitadoEm: 'asc' },
      include: {
        pedido: {
          select: {
            codigo: true,
            valorTotal: true,
            modalidade: true,
            entregueEm: true,
            comprador: { select: { nome: true, email: true } },
            vendedor: { select: { nome: true, email: true } },
            anuncio: { select: { titulo: true } },
          },
        },
      },
    });
    return res.json({ itens });
  } catch (erro) {
    return next(erro);
  }
});

/** POST /admin/reembolsos/:id/aprovar — estorna na pagar.me. */
rotasAdmin.post('/reembolsos/:id/aprovar', async (req, res, next) => {
  try {
    const { resposta } = z.object({ resposta: z.string().max(500).optional() }).parse(req.body);
    return res.json(
      await reembolsoServico.aprovar(req.params.id, req.sessao!.usuarioId, resposta),
    );
  } catch (erro) {
    return next(erro);
  }
});

/** POST /admin/reembolsos/:id/recusar */
rotasAdmin.post('/reembolsos/:id/recusar', async (req, res, next) => {
  try {
    const { motivo } = z.object({ motivo: z.string().min(5).max(500) }).parse(req.body);
    return res.json(
      await reembolsoServico.recusar(req.params.id, req.sessao!.usuarioId, motivo),
    );
  } catch (erro) {
    return next(erro);
  }
});

/**
 * POST /admin/tarefas/repasses
 * Roda o ciclo de liberação na hora. Use isto como alvo do cron do provedor
 * quando houver mais de uma instância do servidor.
 */
rotasAdmin.post('/tarefas/repasses', async (_req, res, next) => {
  try {
    return res.json(await liberarRepassesVencidos());
  } catch (erro) {
    return next(erro);
  }
});

/** PATCH /admin/usuarios/:id/papel — promove alguém a entregador ou admin. */
rotasAdmin.patch('/usuarios/:id/papel', async (req, res, next) => {
  try {
    const { papel } = z
      .object({ papel: z.enum(['CLIENTE', 'ENTREGADOR', 'ADMIN']) })
      .parse(req.body);

    const usuario = await prisma.usuario.update({
      where: { id: req.params.id },
      data: { papel },
      select: { id: true, nome: true, papel: true },
    });
    return res.json(usuario);
  } catch (erro) {
    return next(erro);
  }
});

/** GET /admin/resumo — números do dia. */
rotasAdmin.get('/resumo', async (_req, res, next) => {
  try {
    const [anunciosAtivos, pedidosPagos, aguardandoEntregador, devolucoesAbertas, comissao] =
      await Promise.all([
        prisma.anuncio.count({ where: { estado: 'ATIVO' } }),
        prisma.pedido.count({ where: { estado: { in: ['PAGO', 'A_CAMINHO', 'ENTREGUE'] } } }),
        prisma.entrega.count({ where: { estado: 'AGUARDANDO_ENTREGADOR' } }),
        prisma.reembolso.count({ where: { estado: { in: ['SOLICITADO', 'EM_ANALISE'] } } }),
        prisma.pedido.aggregate({
          _sum: { valorComissao: true },
          where: { estado: { in: ['ENTREGUE', 'CONCLUIDO'] } },
        }),
      ]);

    return res.json({
      anunciosAtivos,
      pedidosPagos,
      aguardandoEntregador,
      devolucoesAbertas,
      comissaoAcumulada: comissao._sum.valorComissao ?? 0,
    });
  } catch (erro) {
    return next(erro);
  }
});
