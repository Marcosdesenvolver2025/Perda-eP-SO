'use strict';

const { z } = require('zod');
const prisma = require('../lib/prisma');
const { HttpError } = require('../utils/httpError');
const { markDelivered } = require('./order.controller');
const { completeRefund } = require('./refund.controller');

const updateDeliverySchema = z.object({
  status: z.enum(['ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'FAILED']),
  failureNote: z.string().max(300).optional(),
});

/**
 * GET /deliveries/available
 * Rotas abertas para a frota: entregas normais e coletas de reembolso
 * (logistica reversa, com a localizacao do comprador).
 */
async function listAvailable(req, res, next) {
  try {
    const deliveries = await prisma.delivery.findMany({
      where: { status: 'PENDING', courierId: null },
      orderBy: { createdAt: 'asc' },
      take: 50,
      include: {
        order: {
          select: {
            id: true,
            shippingMode: true,
            items: { select: { titleSnapshot: true, quantity: true } },
          },
        },
      },
    });
    return res.json(deliveries);
  } catch (error) {
    return next(error);
  }
}

/** GET /deliveries/mine - rotas atribuidas ao entregador logado. */
async function listMine(req, res, next) {
  try {
    const deliveries = await prisma.delivery.findMany({
      where: {
        courierId: req.user.id,
        status: { in: ['ASSIGNED', 'PICKED_UP', 'IN_TRANSIT'] },
      },
      orderBy: { assignedAt: 'asc' },
      include: {
        order: {
          select: {
            id: true,
            items: { select: { titleSnapshot: true, quantity: true } },
          },
        },
      },
    });
    return res.json(deliveries);
  } catch (error) {
    return next(error);
  }
}

/** POST /deliveries/:id/accept - entregador assume a rota. */
async function accept(req, res, next) {
  try {
    const delivery = await prisma.delivery.findUnique({ where: { id: req.params.id } });
    if (!delivery) throw new HttpError(404, 'Rota nao encontrada.');
    if (delivery.courierId) throw new HttpError(409, 'Esta rota ja foi aceita por outro entregador.');

    const updated = await prisma.delivery.update({
      where: { id: delivery.id },
      data: { courierId: req.user.id, status: 'ASSIGNED', assignedAt: new Date() },
    });
    return res.json(updated);
  } catch (error) {
    return next(error);
  }
}

/**
 * PATCH /deliveries/:id
 * Ao concluir uma rota:
 *  - DELIVERY -> marca o pedido como entregue e inicia o teste de 4 dias.
 *  - RETURN   -> conclui a logistica reversa e processa o estorno.
 */
async function updateStatus(req, res, next) {
  try {
    const { status, failureNote } = req.body;

    const delivery = await prisma.delivery.findFirst({
      where: { id: req.params.id, courierId: req.user.id },
    });
    if (!delivery) throw new HttpError(404, 'Rota nao encontrada ou nao atribuida a voce.');

    const updated = await prisma.delivery.update({
      where: { id: delivery.id },
      data: {
        status,
        failureNote,
        ...(status === 'PICKED_UP' ? { pickedUpAt: new Date() } : {}),
        ...(status === 'DELIVERED' ? { completedAt: new Date() } : {}),
      },
    });

    if (status === 'DELIVERED') {
      if (delivery.type === 'DELIVERY') {
        await markDelivered(delivery.orderId);
      } else {
        await prisma.order.update({
          where: { id: delivery.orderId },
          data: {
            status: 'RETURNED',
            statusEvents: { create: { status: 'RETURNED', note: 'Coleta reversa concluida.' } },
          },
        });
        await completeRefund(delivery.orderId, { note: 'Produto devolvido ao vendedor pela frota Vendas Itinga.' });
      }
    }

    return res.json(updated);
  } catch (error) {
    return next(error);
  }
}

module.exports = { updateDeliverySchema, listAvailable, listMine, accept, updateStatus };
