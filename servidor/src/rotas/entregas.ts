/**
 * Área do entregador.
 *
 * Todas as rotas exigem papel ENTREGADOR (ou ADMIN, para suporte). Cada ação
 * do entregador é uma transição na máquina de estados — nenhuma delas mexe em
 * dinheiro diretamente.
 */

import { Router } from 'express';
import { z } from 'zod';

import { exigirLogin, exigirPapel } from '../middlewares/autenticacao';
import * as logistica from '../servicos/logistica';
import { extratoDoEntregador } from '../servicos/repasse';

export const rotasEntregas = Router();

rotasEntregas.use(exigirLogin, exigirPapel('ENTREGADOR', 'ADMIN'));

/** Ator desta rota. ADMIN entra como suporte e enxerga tudo. */
function atorDe(papel: string) {
  return papel === 'ADMIN' ? ('ADMIN' as const) : ('ENTREGADOR' as const);
}

/** GET /entregas/oferecidas — corridas atribuídas a mim, esperando resposta. */
rotasEntregas.get('/oferecidas', async (req, res, next) => {
  try {
    return res.json({
      itens: await logistica.corridasOferecidas(req.sessao!.usuarioId),
    });
  } catch (erro) {
    return next(erro);
  }
});

/** GET /entregas/minhas — corridas que estou tocando agora. */
rotasEntregas.get('/minhas', async (req, res, next) => {
  try {
    return res.json({ itens: await logistica.minhasCorridas(req.sessao!.usuarioId) });
  } catch (erro) {
    return next(erro);
  }
});

/** GET /entregas/historico — o que já concluí. */
rotasEntregas.get('/historico', async (req, res, next) => {
  try {
    const [itens, extrato] = await Promise.all([
      logistica.corridasConcluidas(req.sessao!.usuarioId),
      extratoDoEntregador(req.sessao!.usuarioId),
    ]);
    return res.json({ itens, extrato });
  } catch (erro) {
    return next(erro);
  }
});

/** GET /entregas/:id — detalhe da corrida com a linha do tempo. */
rotasEntregas.get('/:id', async (req, res, next) => {
  try {
    return res.json(
      await logistica.detalheDaCorrida(req.params.id, atorDe(req.sessao!.papel)),
    );
  } catch (erro) {
    return next(erro);
  }
});

/** POST /entregas/disponibilidade — ligar/desligar recebimento de corridas. */
rotasEntregas.post('/disponibilidade', async (req, res, next) => {
  try {
    const { disponivel } = z.object({ disponivel: z.boolean() }).parse(req.body);
    return res.json(
      await logistica.definirDisponibilidade(req.sessao!.usuarioId, disponivel),
    );
  } catch (erro) {
    return next(erro);
  }
});

/**
 * POST /entregas/posicao — última posição do entregador.
 * Hoje é só registro; alimenta a futura atribuição automática por distância.
 */
rotasEntregas.post('/posicao', async (req, res, next) => {
  try {
    const { latitude, longitude } = z
      .object({ latitude: z.number().min(-90).max(90), longitude: z.number().min(-180).max(180) })
      .parse(req.body);
    await logistica.registrarPosicao(req.sessao!.usuarioId, latitude, longitude);
    return res.status(204).end();
  } catch (erro) {
    return next(erro);
  }
});

// ---------------------------------------------------------------------------
// Passos da corrida
// ---------------------------------------------------------------------------

/** Cada rota abaixo é um passo do fluxo, na ordem em que acontece. */
function passo(
  caminho: string,
  para: Parameters<typeof logistica.transitar>[0]['para'],
  corpo?: z.ZodTypeAny,
) {
  rotasEntregas.post(caminho, async (req, res, next) => {
    try {
      const dados = corpo ? corpo.parse(req.body) : {};
      return res.json(
        await logistica.transitar({
          entregaId: req.params.id!,
          para,
          ator: atorDe(req.sessao!.papel),
          autorId: req.sessao!.usuarioId,
          ...dados,
        }),
      );
    } catch (erro) {
      return next(erro);
    }
  });
}

/** POST /entregas/:id/aceitar */
passo('/:id/aceitar', 'ACEITA');

/** POST /entregas/:id/recusar — precisa de motivo; volta para a fila. */
passo(
  '/:id/recusar',
  'RECUSADA',
  z.object({ motivo: z.string().min(3).max(300) }),
);

/** POST /entregas/:id/a-caminho — saí para buscar o produto. */
passo('/:id/a-caminho', 'A_CAMINHO_DA_COLETA');

/** POST /entregas/:id/cheguei-na-coleta */
passo('/:id/cheguei-na-coleta', 'CHEGOU_NA_COLETA');

/** POST /entregas/:id/coletei — exige foto do pacote e nº de volumes. */
passo(
  '/:id/coletei',
  'PRODUTO_COLETADO',
  z.object({
    fotoPacote: z.string().url('envie a foto do pacote'),
    volumes: z.number().int().min(1).max(20),
  }),
);

/** POST /entregas/:id/sair-para-entrega */
passo('/:id/sair-para-entrega', 'EM_ROTA_PARA_ENTREGA');

/** POST /entregas/:id/cheguei-na-entrega — dispara o aviso com o código. */
passo('/:id/cheguei-na-entrega', 'CHEGOU_NA_ENTREGA');

/**
 * POST /entregas/:id/entreguei
 * Exige o código de confirmação. Na ida, abre a janela de 7 dias; na volta,
 * dispara o estorno já integrado à pagar.me.
 */
passo(
  '/:id/entreguei',
  'ENTREGUE',
  z.object({
    codigoConfirmacao: z.string().min(4).max(6),
    fotoEntrega: z.string().url().optional(),
  }),
);
