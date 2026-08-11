/**
 * Painel interno: operação das entregas, análise de devoluções e repasses.
 */

import { Router } from 'express';
import { z } from 'zod';

import { naoEncontrado } from '../erros';
import { exigirLogin, exigirPapel } from '../middlewares/autenticacao';
import { prisma } from '../prisma';
import { candidatosPara, estrategiaAtiva } from '../servicos/atribuicao';
import * as entregaDoVendedor from '../servicos/entregaDoVendedor';
import * as logistica from '../servicos/logistica';
import * as reembolsoServico from '../servicos/reembolso';
import { liberarRepassesVencidos } from '../servicos/repasse';

export const rotasAdmin = Router();

rotasAdmin.use(exigirLogin, exigirPapel('ADMIN'));

// ---------------------------------------------------------------------------
// Operação das entregas
// ---------------------------------------------------------------------------

/** GET /admin/entregas/fila — corridas esperando entregador ou aceite. */
rotasAdmin.get('/entregas/fila', async (_req, res, next) => {
  try {
    return res.json({
      itens: await logistica.filaDeAtribuicao(),
      estrategia: estrategiaAtiva().nome,
    });
  } catch (erro) {
    return next(erro);
  }
});

/**
 * GET /admin/entregas/:id/candidatos
 * Entregadores que podem pegar a corrida, já ordenados pela estratégia ativa.
 * Quem recusou antes não aparece.
 */
rotasAdmin.get('/entregas/:id/candidatos', async (req, res, next) => {
  try {
    return res.json({ itens: await candidatosPara(req.params.id) });
  } catch (erro) {
    return next(erro);
  }
});

/** GET /admin/entregas/:id — detalhe completo, com telefones liberados. */
rotasAdmin.get('/entregas/:id', async (req, res, next) => {
  try {
    return res.json(await logistica.detalheDaCorrida(req.params.id, 'ADMIN'));
  } catch (erro) {
    return next(erro);
  }
});

/** POST /admin/entregas/:id/atribuir — escolhe o entregador (manual, v1). */
rotasAdmin.post('/entregas/:id/atribuir', async (req, res, next) => {
  try {
    const { entregadorId } = z.object({ entregadorId: z.string().min(1) }).parse(req.body);
    return res.json(
      await logistica.transitar({
        entregaId: req.params.id,
        para: 'ATRIBUIDA',
        ator: 'ADMIN',
        autorId: req.sessao!.usuarioId,
        entregadorId,
      }),
    );
  } catch (erro) {
    return next(erro);
  }
});

/** POST /admin/entregas/:id/devolver-para-fila — tira de quem travou. */
rotasAdmin.post('/entregas/:id/devolver-para-fila', async (req, res, next) => {
  try {
    return res.json(
      await logistica.transitar({
        entregaId: req.params.id,
        para: 'AGUARDANDO_ATRIBUICAO',
        ator: 'ADMIN',
        autorId: req.sessao!.usuarioId,
      }),
    );
  } catch (erro) {
    return next(erro);
  }
});

/** POST /admin/entregas/:id/cancelar */
rotasAdmin.post('/entregas/:id/cancelar', async (req, res, next) => {
  try {
    const { motivo } = z.object({ motivo: z.string().min(3).max(300) }).parse(req.body);
    return res.json(
      await logistica.transitar({
        entregaId: req.params.id,
        para: 'CANCELADA',
        ator: 'ADMIN',
        autorId: req.sessao!.usuarioId,
        motivo,
      }),
    );
  } catch (erro) {
    return next(erro);
  }
});

/** GET /admin/entregadores — quem está na operação e quantas corridas tem. */
rotasAdmin.get('/entregadores', async (_req, res, next) => {
  try {
    const itens = await prisma.usuario.findMany({
      where: { papel: 'ENTREGADOR', excluidoEm: null },
      orderBy: { nome: 'asc' },
      select: {
        id: true,
        nome: true,
        telefone: true,
        entregadorDisponivel: true,
        entregadorCapacidade: true,
        entregadorPosicaoEm: true,
        _count: {
          select: {
            entregas: {
              where: {
                estado: {
                  in: [
                    'ATRIBUIDA',
                    'ACEITA',
                    'A_CAMINHO_DA_COLETA',
                    'CHEGOU_NA_COLETA',
                    'PRODUTO_COLETADO',
                    'EM_ROTA_PARA_ENTREGA',
                    'CHEGOU_NA_ENTREGA',
                  ],
                },
              },
            },
          },
        },
      },
    });
    return res.json({ itens });
  } catch (erro) {
    return next(erro);
  }
});

// ---------------------------------------------------------------------------
// Devoluções
// ---------------------------------------------------------------------------

/** GET /admin/reembolsos — fila de devoluções a analisar. */
rotasAdmin.get('/reembolsos', async (req, res, next) => {
  try {
    const { estado } = z
      .object({
        estado: z
          .enum(['SOLICITADO', 'EM_ANALISE', 'APROVADO', 'RECUSADO', 'CONCLUIDO'])
          .optional(),
      })
      .parse(req.query);

    const itens = await prisma.reembolso.findMany({
      where: estado ? { estado } : { estado: { in: ['SOLICITADO', 'EM_ANALISE'] } },
      orderBy: { solicitadoEm: 'asc' },
      include: {
        pedido: {
          select: {
            id: true,
            codigo: true,
            valorTotal: true,
            modalidade: true,
            entregueEm: true,
            comprador: { select: { nome: true, email: true, telefone: true } },
            vendedor: { select: { nome: true, email: true, telefone: true } },
            anuncio: { select: { titulo: true } },
            // a modalidade diz ao admin se há coleta reversa ou se as partes
            // combinam a devolução entre si
            entregas: {
              where: { tipo: 'DEVOLUCAO' },
              select: { id: true, estado: true },
            },
          },
        },
      },
    });
    return res.json({ itens });
  } catch (erro) {
    return next(erro);
  }
});

/**
 * POST /admin/reembolsos/:id/aprovar
 * Aprova e abre a coleta reversa. O estorno só sai quando o produto chegar
 * de volta no vendedor.
 */
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

/**
 * POST /admin/reembolsos/:id/confirmar-retorno
 * Modalidade VENDEDOR: não há entregador para trazer o produto de volta. O
 * admin confirma que o vendedor recebeu e isso dispara o estorno.
 */
rotasAdmin.post('/reembolsos/:id/confirmar-retorno', async (req, res, next) => {
  try {
    const reembolso = await prisma.reembolso.findUnique({
      where: { id: req.params.id },
      select: { pedidoId: true },
    });
    if (!reembolso) throw naoEncontrado('Solicitação não encontrada.');

    return res.json(
      await entregaDoVendedor.confirmarRetornoDoProduto(
        reembolso.pedidoId,
        req.sessao!.usuarioId,
      ),
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

// ---------------------------------------------------------------------------
// Operação financeira e visão geral
// ---------------------------------------------------------------------------

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
    const [
      anunciosAtivos,
      pedidosEmAndamento,
      aguardandoAtribuicao,
      corridasEmRota,
      devolucoesAbertas,
      receita,
    ] = await Promise.all([
      prisma.anuncio.count({ where: { estado: 'ATIVO' } }),
      prisma.pedido.count({
        where: {
          estado: {
            in: [
              'PAGO',
              'AGUARDANDO_AGENDAMENTO_DE_COLETA',
              'A_CAMINHO_DA_COLETA',
              'PRODUTO_COLETADO',
              'EM_ROTA_PARA_ENTREGA',
              'ENTREGUE',
            ],
          },
        },
      }),
      prisma.entrega.count({ where: { estado: 'AGUARDANDO_ATRIBUICAO' } }),
      prisma.entrega.count({
        where: {
          estado: {
            in: ['A_CAMINHO_DA_COLETA', 'PRODUTO_COLETADO', 'EM_ROTA_PARA_ENTREGA'],
          },
        },
      }),
      prisma.reembolso.count({
        where: { estado: { in: ['SOLICITADO', 'EM_ANALISE', 'APROVADO'] } },
      }),
      prisma.pedido.aggregate({
        _sum: { valorComissao: true, valorTarifa: true, custoEntregador: true },
        where: { estado: { in: ['ENTREGUE', 'CONCLUIDO'] } },
      }),
    ]);

    const comissao = receita._sum.valorComissao ?? 0;
    const tarifas = receita._sum.valorTarifa ?? 0;
    const custoEntregas = receita._sum.custoEntregador ?? 0;

    return res.json({
      anunciosAtivos,
      pedidosEmAndamento,
      aguardandoAtribuicao,
      corridasEmRota,
      devolucoesAbertas,
      comissaoAcumulada: comissao,
      tarifasAcumuladas: tarifas,
      custoComEntregas: custoEntregas,
      receitaLiquida: comissao + tarifas - custoEntregas,
    });
  } catch (erro) {
    return next(erro);
  }
});
