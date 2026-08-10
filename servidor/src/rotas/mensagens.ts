/**
 * Conversa entre comprador e vendedor (a aba "notificações" do app).
 */

import { Router } from 'express';
import { z } from 'zod';

import { naoEncontrado, semPermissao } from '../erros';
import { exigirLogin } from '../middlewares/autenticacao';
import { prisma } from '../prisma';

export const rotasMensagens = Router();

rotasMensagens.use(exigirLogin);

/** GET /mensagens — caixa de entrada agrupada por pedido. */
rotasMensagens.get('/', async (req, res, next) => {
  try {
    const usuarioId = req.sessao!.usuarioId;
    const mensagens = await prisma.mensagem.findMany({
      where: { OR: [{ autorId: usuarioId }, { destinatarioId: usuarioId }] },
      orderBy: { criadoEm: 'desc' },
      take: 200,
      include: {
        autor: { select: { id: true, nome: true, fotoUrl: true } },
        pedido: {
          select: {
            id: true,
            codigo: true,
            anuncio: { select: { titulo: true, fotos: { take: 1, orderBy: { ordem: 'asc' } } } },
          },
        },
      },
    });

    // uma linha por conversa, com a última mensagem — igual à tela do app
    const conversas = new Map<string, (typeof mensagens)[number]>();
    for (const m of mensagens) {
      const chave = m.pedidoId ?? m.anuncioId ?? `${m.autorId}-${m.destinatarioId}`;
      if (!conversas.has(chave)) conversas.set(chave, m);
    }

    return res.json({
      itens: [...conversas.values()].map((m) => ({
        ...m,
        souOAutor: m.autorId === usuarioId,
        naoLida: m.destinatarioId === usuarioId && !m.lidaEm,
      })),
      naoLidas: mensagens.filter((m) => m.destinatarioId === usuarioId && !m.lidaEm).length,
    });
  } catch (erro) {
    return next(erro);
  }
});

/** GET /mensagens/pedido/:pedidoId — histórico da conversa. */
rotasMensagens.get('/pedido/:pedidoId', async (req, res, next) => {
  try {
    const usuarioId = req.sessao!.usuarioId;
    const pedido = await prisma.pedido.findUnique({ where: { id: req.params.pedidoId } });
    if (!pedido) throw naoEncontrado('Pedido não encontrado.');
    if (pedido.compradorId !== usuarioId && pedido.vendedorId !== usuarioId) {
      throw semPermissao();
    }

    const mensagens = await prisma.mensagem.findMany({
      where: { pedidoId: pedido.id },
      orderBy: { criadoEm: 'asc' },
      include: { autor: { select: { id: true, nome: true, fotoUrl: true } } },
    });

    await prisma.mensagem.updateMany({
      where: { pedidoId: pedido.id, destinatarioId: usuarioId, lidaEm: null },
      data: { lidaEm: new Date() },
    });

    return res.json({ itens: mensagens });
  } catch (erro) {
    return next(erro);
  }
});

/** POST /mensagens — manda mensagem dentro de um pedido. */
rotasMensagens.post('/', async (req, res, next) => {
  try {
    const dados = z
      .object({ pedidoId: z.string(), texto: z.string().min(1).max(2000) })
      .parse(req.body);

    const usuarioId = req.sessao!.usuarioId;
    const pedido = await prisma.pedido.findUnique({ where: { id: dados.pedidoId } });
    if (!pedido) throw naoEncontrado('Pedido não encontrado.');
    if (pedido.compradorId !== usuarioId && pedido.vendedorId !== usuarioId) {
      throw semPermissao();
    }

    const destinatarioId =
      pedido.compradorId === usuarioId ? pedido.vendedorId : pedido.compradorId;

    const mensagem = await prisma.mensagem.create({
      data: {
        pedidoId: pedido.id,
        autorId: usuarioId,
        destinatarioId,
        texto: dados.texto,
      },
    });
    return res.status(201).json(mensagem);
  } catch (erro) {
    return next(erro);
  }
});
