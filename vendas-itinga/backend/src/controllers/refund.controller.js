'use strict';

const { z } = require('zod');
const prisma = require('../lib/prisma');
const pagarme = require('../services/pagarme.service');
const { HttpError } = require('../utils/httpError');

const refundSchema = z.object({
  reason: z.enum(['NOT_AS_DESCRIBED', 'DAMAGED', 'WRONG_ITEM', 'NOT_RECEIVED', 'OTHER']),
  description: z.string().min(10).max(1000),
  photos: z.array(z.string().url()).max(6).default([]),
});

const reviewSchema = z.object({
  action: z.enum(['APPROVE', 'REJECT']),
  note: z.string().max(500).optional(),
});

/**
 * POST /orders/:id/refund
 *
 * Solicitacao de reembolso por desconformidade, dentro do periodo de
 * teste de 4 dias. Ao abrir a solicitacao o sistema ja aciona a
 * LOGISTICA REVERSA: cria uma tarefa de coleta com a localizacao do
 * comprador para um entregador, com destino no endereco do vendedor.
 *
 * IMPORTANTE (regra do negocio): a comissao da plataforma permanece
 * devida pelo vendedor mesmo com o reembolso, pois cobre os custos
 * operacionais do processamento e da coleta.
 */
async function requestRefund(req, res, next) {
  try {
    const { reason, description, photos } = req.body;

    const order = await prisma.order.findFirst({
      where: { id: req.params.id, buyerId: req.user.id },
      include: {
        address: true,
        refund: true,
        seller: { include: { addresses: { where: { isDefault: true }, take: 1 } } },
      },
    });

    if (!order) throw new HttpError(404, 'Pedido nao encontrado.');
    if (order.refund) throw new HttpError(409, 'Ja existe uma solicitacao para este pedido.');
    if (order.paymentStatus !== 'PAID') throw new HttpError(409, 'Pedido sem pagamento confirmado.');

    if (!['IN_TEST', 'DELIVERED', 'SHIPPED'].includes(order.status)) {
      throw new HttpError(409, 'Este pedido nao esta elegivel para reembolso.');
    }
    if (order.testEndsAt && order.testEndsAt < new Date()) {
      throw new HttpError(409, 'O periodo de teste de 4 dias ja se encerrou.');
    }

    const sellerAddress = order.seller.addresses[0];

    const [refund] = await prisma.$transaction([
      prisma.refundRequest.create({
        data: {
          orderId: order.id,
          reason,
          description,
          photos,
          status: 'COLLECTING',
          // A comissao continua devida pelo vendedor.
          platformFeeRetainedCents: order.platformFeeCents,
          refundedAmountCents: 0,
        },
      }),
      prisma.order.update({
        where: { id: order.id },
        data: {
          status: 'RETURN_REQUESTED',
          statusEvents: {
            create: { status: 'RETURN_REQUESTED', note: `Reembolso solicitado: ${reason}` },
          },
        },
      }),
      // Logistica reversa: coleta NO COMPRADOR -> devolucao ao vendedor.
      prisma.delivery.create({
        data: {
          orderId: order.id,
          type: 'RETURN',
          status: 'PENDING',
          pickupLabel: `Coleta reversa com ${order.address.recipient}`,
          pickupAddress: `${order.address.street}, ${order.address.number} - ${order.address.district}, ${order.address.city}/${order.address.state}`,
          pickupLatitude: order.address.latitude,
          pickupLongitude: order.address.longitude,
          dropoffLabel: `Devolucao para ${order.seller.name}`,
          dropoffAddress: sellerAddress
            ? `${sellerAddress.street}, ${sellerAddress.number} - ${sellerAddress.district}, ${sellerAddress.city}/${sellerAddress.state}`
            : 'Endereco de devolucao a confirmar com o vendedor',
          dropoffLatitude: sellerAddress?.latitude,
          dropoffLongitude: sellerAddress?.longitude,
        },
      }),
      // O repasse fica suspenso ate a conclusao da analise.
      prisma.payout.updateMany({
        where: { orderId: order.id, status: 'SCHEDULED' },
        data: { releaseAt: new Date('2999-12-31') },
      }),
    ]);

    return res.status(201).json({
      refund,
      message:
        'Solicitacao registrada. Um entregador ira ate o seu endereco recolher o produto. ' +
        'Acompanhe o status pelo pedido.',
    });
  } catch (error) {
    return next(error);
  }
}

/**
 * POST /orders/:id/refund/complete
 *
 * Chamado quando a coleta reversa e concluida (entregador finaliza a
 * tarefa RETURN) ou por um administrador. Estorna ao comprador o valor
 * do produto + frete, retendo a comissao da plataforma - que permanece
 * devida pelo vendedor.
 */
async function completeRefund(orderId, { note } = {}) {
  const order = await prisma.order.findUnique({
    where: { id: orderId },
    include: { refund: true, items: true },
  });

  if (!order?.refund) throw new HttpError(404, 'Solicitacao de reembolso nao encontrada.');
  if (order.refund.status === 'COMPLETED') return order;

  // Devolvemos ao comprador tudo o que ele pagou; a comissao ja retida
  // pela plataforma e debitada do vendedor no acerto do repasse.
  const refundAmountCents = order.totalCents;

  if (order.pagarmeChargeId) {
    await pagarme.refundCharge(order.pagarmeChargeId, refundAmountCents);
  }

  const updated = await prisma.order.update({
    where: { id: order.id },
    data: {
      status: 'REFUNDED',
      paymentStatus: 'REFUNDED',
      statusEvents: {
        create: {
          status: 'REFUNDED',
          note:
            note ||
            `Reembolso concluido. Comissao de ${order.platformFeeBps / 100}% retida do vendedor (custo operacional).`,
        },
      },
      refund: {
        update: {
          status: 'COMPLETED',
          refundedAmountCents: refundAmountCents,
          completedAt: new Date(),
        },
      },
    },
    include: { refund: true, items: true },
  });

  // O repasse e cancelado: o vendedor nao recebe o valor do produto, mas a
  // comissao ja retida pela plataforma permanece devida (custo operacional).
  await prisma.payout.updateMany({
    where: { orderId: order.id },
    data: { status: 'CANCELED', amountCents: 0 },
  });

  // O anuncio volta para o catalogo do vendedor.
  await prisma.product.updateMany({
    where: { id: { in: updated.items.map((i) => i.productId) } },
    data: { status: 'ACTIVE' },
  });

  return updated;
}

/** POST /orders/:id/refund/review - vendedor aceita ou contesta. */
async function reviewRefund(req, res, next) {
  try {
    const { action, note } = req.body;

    const order = await prisma.order.findFirst({
      where: { id: req.params.id, sellerId: req.user.id },
      include: { refund: true },
    });
    if (!order?.refund) throw new HttpError(404, 'Solicitacao nao encontrada.');

    if (action === 'REJECT') {
      const refund = await prisma.refundRequest.update({
        where: { id: order.refund.id },
        data: { status: 'REJECTED', reviewedAt: new Date() },
      });
      await prisma.order.update({
        where: { id: order.id },
        data: {
          statusEvents: {
            create: { status: 'RETURN_REQUESTED', note: `Contestado pelo vendedor: ${note || 'sem observacoes'}` },
          },
        },
      });
      return res.json({ refund, message: 'Contestacao registrada para analise da equipe Vendas Itinga.' });
    }

    const updated = await completeRefund(order.id, { note: note && `Aprovado pelo vendedor: ${note}` });
    return res.json({ refund: updated.refund, message: 'Reembolso aprovado e processado.' });
  } catch (error) {
    return next(error);
  }
}

/** GET /orders/:id/refund */
async function getRefund(req, res, next) {
  try {
    const order = await prisma.order.findFirst({
      where: {
        id: req.params.id,
        OR: [{ buyerId: req.user.id }, { sellerId: req.user.id }],
      },
      include: { refund: true, deliveries: { where: { type: 'RETURN' } } },
    });
    if (!order?.refund) throw new HttpError(404, 'Nenhuma solicitacao para este pedido.');

    return res.json({ ...order.refund, reverseLogistics: order.deliveries[0] || null });
  } catch (error) {
    return next(error);
  }
}

module.exports = { refundSchema, reviewSchema, requestRefund, reviewRefund, getRefund, completeRefund };
