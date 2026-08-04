'use strict';

const { z } = require('zod');
const prisma = require('../lib/prisma');
const { HttpError } = require('../utils/httpError');

const startSchema = z.object({
  productId: z.string().min(1),
});

const messageSchema = z.object({
  body: z.string().min(1).max(1000),
});

/** GET /conversations - caixa de entrada (compras e vendas). */
async function listConversations(req, res, next) {
  try {
    const conversations = await prisma.conversation.findMany({
      where: { OR: [{ buyerId: req.user.id }, { sellerId: req.user.id }] },
      orderBy: { lastMessageAt: 'desc' },
      include: {
        product: { include: { images: { orderBy: { position: 'asc' }, take: 1 } } },
        buyer: { select: { id: true, name: true, avatarUrl: true } },
        seller: { select: { id: true, name: true, avatarUrl: true } },
        messages: { orderBy: { createdAt: 'desc' }, take: 1 },
      },
    });

    const unreadCounts = await prisma.message.groupBy({
      by: ['conversationId'],
      where: {
        readAt: null,
        senderId: { not: req.user.id },
        conversation: { OR: [{ buyerId: req.user.id }, { sellerId: req.user.id }] },
      },
      _count: { _all: true },
    });
    const unreadMap = Object.fromEntries(unreadCounts.map((u) => [u.conversationId, u._count._all]));

    return res.json(
      conversations.map((c) => {
        const other = c.buyerId === req.user.id ? c.seller : c.buyer;
        return {
          id: c.id,
          other,
          product: c.product
            ? {
                id: c.product.id,
                title: c.product.title,
                priceCents: c.product.priceCents,
                image: c.product.images[0]?.url || null,
              }
            : null,
          lastMessage: c.messages[0]?.body || null,
          lastMessageAt: c.lastMessageAt,
          unreadCount: unreadMap[c.id] || 0,
        };
      })
    );
  } catch (error) {
    return next(error);
  }
}

/** POST /conversations - abre (ou reabre) a negociacao de um produto. */
async function startConversation(req, res, next) {
  try {
    const { productId } = req.body;

    const product = await prisma.product.findUnique({ where: { id: productId } });
    if (!product) throw new HttpError(404, 'Produto nao encontrado.');
    if (product.sellerId === req.user.id) {
      throw new HttpError(400, 'Voce nao pode negociar com voce mesmo.');
    }

    const conversation = await prisma.conversation.upsert({
      where: {
        productId_buyerId_sellerId: {
          productId,
          buyerId: req.user.id,
          sellerId: product.sellerId,
        },
      },
      create: { productId, buyerId: req.user.id, sellerId: product.sellerId },
      update: {},
    });

    return res.status(201).json({ id: conversation.id });
  } catch (error) {
    return next(error);
  }
}

/** GET /conversations/:id/messages */
async function listMessages(req, res, next) {
  try {
    const conversation = await prisma.conversation.findFirst({
      where: {
        id: req.params.id,
        OR: [{ buyerId: req.user.id }, { sellerId: req.user.id }],
      },
      include: {
        product: { include: { images: { orderBy: { position: 'asc' }, take: 1 } } },
        buyer: { select: { id: true, name: true, avatarUrl: true } },
        seller: { select: { id: true, name: true, avatarUrl: true } },
      },
    });
    if (!conversation) throw new HttpError(404, 'Conversa nao encontrada.');

    const messages = await prisma.message.findMany({
      where: { conversationId: conversation.id },
      orderBy: { createdAt: 'asc' },
      take: 200,
    });

    await prisma.message.updateMany({
      where: { conversationId: conversation.id, senderId: { not: req.user.id }, readAt: null },
      data: { readAt: new Date() },
    });

    const other = conversation.buyerId === req.user.id ? conversation.seller : conversation.buyer;

    return res.json({
      id: conversation.id,
      other,
      product: conversation.product
        ? {
            id: conversation.product.id,
            title: conversation.product.title,
            priceCents: conversation.product.priceCents,
            image: conversation.product.images[0]?.url || null,
          }
        : null,
      messages: messages.map((m) => ({
        id: m.id,
        body: m.body,
        mine: m.senderId === req.user.id,
        createdAt: m.createdAt,
      })),
    });
  } catch (error) {
    return next(error);
  }
}

/** POST /conversations/:id/messages */
async function sendMessage(req, res, next) {
  try {
    const conversation = await prisma.conversation.findFirst({
      where: {
        id: req.params.id,
        OR: [{ buyerId: req.user.id }, { sellerId: req.user.id }],
      },
    });
    if (!conversation) throw new HttpError(404, 'Conversa nao encontrada.');

    const message = await prisma.message.create({
      data: {
        conversationId: conversation.id,
        senderId: req.user.id,
        body: req.body.body,
      },
    });

    await prisma.conversation.update({
      where: { id: conversation.id },
      data: { lastMessageAt: message.createdAt },
    });

    return res.status(201).json({
      id: message.id,
      body: message.body,
      mine: true,
      createdAt: message.createdAt,
    });
  } catch (error) {
    return next(error);
  }
}

module.exports = { startSchema, messageSchema, listConversations, startConversation, listMessages, sendMessage };
