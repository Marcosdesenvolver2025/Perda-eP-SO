/**
 * Área do entregador: lista de corridas, aceite, coleta e confirmação.
 * Todas as rotas exigem papel ENTREGADOR (ou ADMIN, para suporte).
 */

import { Router } from 'express';
import { z } from 'zod';

import { exigirLogin, exigirPapel } from '../middlewares/autenticacao';
import { log } from '../log';
import * as entregaServico from '../servicos/entrega';
import { pagarEntregador } from '../servicos/repasse';

export const rotasEntregas = Router();

rotasEntregas.use(exigirLogin, exigirPapel('ENTREGADOR', 'ADMIN'));

/** GET /entregas/disponiveis — corridas abertas para aceitar. */
rotasEntregas.get('/disponiveis', async (_req, res, next) => {
  try {
    return res.json({ itens: await entregaServico.entregasDisponiveis() });
  } catch (erro) {
    return next(erro);
  }
});

/** GET /entregas/minhas — corridas do entregador logado. */
rotasEntregas.get('/minhas', async (req, res, next) => {
  try {
    return res.json({ itens: await entregaServico.minhasEntregas(req.sessao!.usuarioId) });
  } catch (erro) {
    return next(erro);
  }
});

/** POST /entregas/:id/aceitar */
rotasEntregas.post('/:id/aceitar', async (req, res, next) => {
  try {
    return res.json(await entregaServico.aceitar(req.params.id, req.sessao!.usuarioId));
  } catch (erro) {
    return next(erro);
  }
});

/** POST /entregas/:id/coletei */
rotasEntregas.post('/:id/coletei', async (req, res, next) => {
  try {
    return res.json(
      await entregaServico.confirmarColeta(req.params.id, req.sessao!.usuarioId),
    );
  } catch (erro) {
    return next(erro);
  }
});

const confirmarEsquema = z.object({
  codigo: z.string().min(4).max(6),
  fotoComprovanteUrl: z.string().url().optional(),
});

/**
 * POST /entregas/:id/entreguei
 * Confirma a entrega e dispara o pagamento do entregador. Este é o marco que
 * abre a janela de 7 dias do comprador para testar o produto.
 */
rotasEntregas.post('/:id/entreguei', async (req, res, next) => {
  try {
    const dados = confirmarEsquema.parse(req.body);
    const entrega = await entregaServico.confirmarEntrega(
      req.params.id,
      req.sessao!.usuarioId,
      dados.codigo,
      dados.fotoComprovanteUrl,
    );

    // o pagamento não pode derrubar a confirmação: se falhar, o ciclo de
    // repasses tenta de novo e a equipe vê o alerta no log.
    pagarEntregador(entrega.id).catch((erro) =>
      log.error({ erro, entrega: entrega.id }, 'falha ao pagar entregador'),
    );

    return res.json(entrega);
  } catch (erro) {
    return next(erro);
  }
});
