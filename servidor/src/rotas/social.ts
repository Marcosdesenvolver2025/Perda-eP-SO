/**
 * O que liga as pessoas ao redor do anúncio: ofertas, curtidas, seguir e
 * avaliar. Nada aqui move dinheiro.
 */

import { Router } from 'express';
import { z } from 'zod';

import { erroDeValidacao, naoEncontrado } from '../erros';
import { exigirLogin, loginOpcional } from '../middlewares/autenticacao';
import { prisma } from '../prisma';
import * as ofertas from '../servicos/ofertas';

export const rotasOfertas = Router();
export const rotasVendedores = Router();

// ---------------------------------------------------------------------------
// Ofertas
// ---------------------------------------------------------------------------

/** GET /ofertas — as negociações em que eu sou comprador ou vendedor. */
rotasOfertas.get('/', exigirLogin, async (req, res, next) => {
  try {
    return res.json({ itens: await ofertas.listarMinhas(req.sessao!.usuarioId) });
  } catch (erro) {
    return next(erro);
  }
});

const acaoComValor = z.object({
  valor: z.number().int().positive(),
  recado: z.string().max(300).optional(),
});

rotasOfertas.post('/:id/aceitar', exigirLogin, async (req, res, next) => {
  try {
    return res.json(await ofertas.aceitar(req.params.id!, req.sessao!.usuarioId));
  } catch (erro) {
    return next(erro);
  }
});

rotasOfertas.post('/:id/recusar', exigirLogin, async (req, res, next) => {
  try {
    return res.json(await ofertas.recusar(req.params.id!, req.sessao!.usuarioId));
  } catch (erro) {
    return next(erro);
  }
});

rotasOfertas.post('/:id/cancelar', exigirLogin, async (req, res, next) => {
  try {
    return res.json(await ofertas.cancelar(req.params.id!, req.sessao!.usuarioId));
  } catch (erro) {
    return next(erro);
  }
});

rotasOfertas.post('/:id/contrapropor', exigirLogin, async (req, res, next) => {
  try {
    const { valor, recado } = acaoComValor.parse(req.body);
    return res.json(
      await ofertas.contrapropor(req.params.id!, req.sessao!.usuarioId, valor, recado),
    );
  } catch (erro) {
    return next(erro);
  }
});

// ---------------------------------------------------------------------------
// Vendedores: perfil público, seguir e avaliações
// ---------------------------------------------------------------------------

/** GET /vendedores/:id — o perfil que a lojinha mostra. */
rotasVendedores.get('/:id', loginOpcional, async (req, res, next) => {
  try {
    const id = req.params.id!;
    const usuario = await prisma.usuario.findFirst({
      where: { id, excluidoEm: null },
      select: {
        id: true,
        nome: true,
        apelidoLoja: true,
        bioLoja: true,
        fotoUrl: true,
        bairro: true,
        cidade: true,
      },
    });
    if (!usuario) throw naoEncontrado('Vendedor não encontrado.');

    const [seguidores, sigo, notas] = await Promise.all([
      prisma.seguidor.count({ where: { seguidoId: id } }),
      req.sessao
        ? prisma.seguidor.findUnique({
            where: {
              seguidorId_seguidoId: { seguidorId: req.sessao.usuarioId, seguidoId: id },
            },
          })
        : Promise.resolve(null),
      prisma.avaliacao.aggregate({
        where: { avaliadoId: id },
        _avg: { nota: true },
        _count: true,
      }),
    ]);

    return res.json({
      ...usuario,
      seguidores,
      seguindo: sigo !== null,
      notaMedia: notas._avg.nota ? Math.round(notas._avg.nota * 10) / 10 : null,
      totalAvaliacoes: notas._count,
    });
  } catch (erro) {
    return next(erro);
  }
});

/** POST /vendedores/:id/seguir — alterna: segue se não seguia, e vice-versa. */
rotasVendedores.post('/:id/seguir', exigirLogin, async (req, res, next) => {
  try {
    const seguidorId = req.sessao!.usuarioId;
    const seguidoId = req.params.id!;
    if (seguidorId === seguidoId) {
      throw erroDeValidacao('Você não pode seguir a si mesmo.');
    }

    const chave = { seguidorId_seguidoId: { seguidorId, seguidoId } };
    const existente = await prisma.seguidor.findUnique({ where: chave });

    if (existente) {
      await prisma.seguidor.delete({ where: chave });
    } else {
      await prisma.seguidor.create({ data: { seguidorId, seguidoId } });
    }

    return res.json({
      seguindo: !existente,
      seguidores: await prisma.seguidor.count({ where: { seguidoId } }),
    });
  } catch (erro) {
    return next(erro);
  }
});

/** GET /vendedores/:id/avaliacoes — o que disseram sobre esta lojinha. */
rotasVendedores.get('/:id/avaliacoes', async (req, res, next) => {
  try {
    const itens = await prisma.avaliacao.findMany({
      where: { avaliadoId: req.params.id! },
      include: {
        autor: { select: { nome: true, fotoUrl: true } },
        pedido: { select: { codigo: true, anuncio: { select: { titulo: true } } } },
      },
      orderBy: { criadoEm: 'desc' },
      take: 50,
    });
    return res.json({ itens });
  } catch (erro) {
    return next(erro);
  }
});
