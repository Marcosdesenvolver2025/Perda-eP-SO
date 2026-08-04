'use strict';

const crypto = require('crypto');
const { z } = require('zod');
const prisma = require('../lib/prisma');
const env = require('../config/env');
const pagarme = require('../services/pagarme.service');
const { calculateSplit, buildPagarmeSplit, calculateTestEndDate, calculateReleaseDate } = require('../services/split.service');
const { quoteShipping, distanceKm } = require('../services/shipping.service');
const { HttpError } = require('../utils/httpError');

const checkoutSchema = z.object({
  addressId: z.string().min(1),
  paymentMethod: z.enum(['CREDIT_CARD', 'PIX']),
  cardToken: z.string().optional(),
  installments: z.number().int().min(1).max(12).default(1),
  notes: z.string().max(300).optional(),
});

const statusSchema = z.object({
  status: z.enum(['SHIPPED', 'DELIVERED', 'CANCELED']),
  trackingCode: z.string().max(60).optional(),
});

function orderPayload(order) {
  return {
    id: order.id,
    checkoutId: order.checkoutId,
    status: order.status,
    paymentStatus: order.paymentStatus,
    paymentMethod: order.paymentMethod,
    shippingMode: order.shippingMode,
    itemsTotalCents: order.itemsTotalCents,
    shippingCents: order.shippingCents,
    totalCents: order.totalCents,
    platformFeeCents: order.platformFeeCents,
    platformFeePercent: order.platformFeeBps / 100,
    sellerAmountCents: order.sellerAmountCents,
    trackingCode: order.trackingCode,
    pix: order.pixQrCode
      ? { qrCode: order.pixQrCode, qrCodeUrl: order.pixQrCodeUrl, expiresAt: order.pixExpiresAt }
      : null,
    testEndsAt: order.testEndsAt,
    deliveredAt: order.deliveredAt,
    createdAt: order.createdAt,
    items: (order.items || []).map((i) => ({
      id: i.id,
      productId: i.productId,
      title: i.titleSnapshot,
      image: i.imageSnapshot,
      priceCents: i.priceCents,
      quantity: i.quantity,
    })),
    buyer: order.buyer ? { id: order.buyer.id, name: order.buyer.name, avatarUrl: order.buyer.avatarUrl } : undefined,
    seller: order.seller ? { id: order.seller.id, name: order.seller.name, avatarUrl: order.seller.avatarUrl } : undefined,
    address: order.address || undefined,
    refund: order.refund || undefined,
  };
}

/**
 * POST /orders/checkout
 *
 * Fluxo:
 *  1. Carrega o carrinho e agrupa por vendedor.
 *  2. Para cada grupo calcula frete + split (20% frota / 15% entrega propria).
 *  3. Cria UM pedido no Pagar.me por vendedor, com as regras de split.
 *  4. Persiste os pedidos e limpa o carrinho.
 *
 * Cartao: cobranca imediata. PIX: devolve o QR Code e aguarda o webhook.
 */
async function checkout(req, res, next) {
  try {
    const { addressId, paymentMethod, cardToken, installments, notes } = req.body;

    if (paymentMethod === 'CREDIT_CARD' && !cardToken) {
      throw new HttpError(422, 'cardToken obrigatorio para pagamento com cartao.');
    }

    const address = await prisma.address.findFirst({
      where: { id: addressId, userId: req.user.id },
    });
    if (!address) throw new HttpError(404, 'Endereco de entrega nao encontrado.');

    const cartItems = await prisma.cartItem.findMany({
      where: { userId: req.user.id },
      include: {
        product: {
          include: {
            images: { orderBy: { position: 'asc' }, take: 1 },
            seller: true,
          },
        },
      },
    });

    if (!cartItems.length) throw new HttpError(400, 'Seu carrinho esta vazio.');

    // ---- Agrupamento por vendedor ----
    const groups = new Map();
    for (const item of cartItems) {
      const product = item.product;

      if (product.status !== 'ACTIVE') {
        throw new HttpError(409, `O produto "${product.title}" nao esta mais disponivel.`);
      }
      if (product.priceCents < env.rules.minProductPriceCents) {
        throw new HttpError(409, `O produto "${product.title}" esta abaixo do valor minimo permitido.`);
      }
      if (!product.seller.pagarmeRecipientId) {
        throw new HttpError(
          409,
          `O vendedor de "${product.title}" ainda nao concluiu o cadastro de recebimento.`
        );
      }

      if (!groups.has(product.sellerId)) {
        groups.set(product.sellerId, {
          seller: product.seller,
          shippingMode: product.shippingMode,
          items: [],
          itemsTotalCents: 0,
          weightGrams: 0,
        });
      }

      const group = groups.get(product.sellerId);
      group.items.push(item);
      group.itemsTotalCents += product.priceCents * item.quantity;
      group.weightGrams += product.weightGrams * item.quantity;
      if (product.shippingMode === 'PLATFORM') group.shippingMode = 'PLATFORM';
      group.distance =
        group.distance ??
        (address.latitude != null && product.latitude != null
          ? distanceKm(address.latitude, address.longitude, product.latitude, product.longitude)
          : null);
    }

    const checkoutId = crypto.randomUUID();
    const customer = {
      name: req.user.name,
      email: req.user.email,
      type: 'individual',
      ...(req.user.document ? { document: req.user.document, document_type: 'CPF' } : {}),
      ...(req.user.phone
        ? {
            phones: {
              mobile_phone: {
                country_code: '55',
                area_code: req.user.phone.replace(/\D/g, '').slice(0, 2),
                number: req.user.phone.replace(/\D/g, '').slice(2),
              },
            },
          }
        : {}),
    };

    const createdOrders = [];

    for (const group of groups.values()) {
      const quote = quoteShipping({
        shippingMode: group.shippingMode,
        weightGrams: group.weightGrams,
        distance: group.distance,
      });

      const split = calculateSplit({
        itemsTotalCents: group.itemsTotalCents,
        shippingCents: quote.cents,
        shippingMode: group.shippingMode,
      });

      // 1) Registra o pedido localmente ANTES de cobrar, para termos
      //    rastreabilidade caso o gateway falhe no meio do caminho.
      const order = await prisma.order.create({
        data: {
          checkoutId,
          buyerId: req.user.id,
          sellerId: group.seller.id,
          addressId: address.id,
          status: 'PENDING_PAYMENT',
          shippingMode: group.shippingMode,
          itemsTotalCents: group.itemsTotalCents,
          shippingCents: quote.cents,
          totalCents: split.totalCents,
          platformFeeBps: split.platformFeeBps,
          platformFeeCents: split.platformFeeCents,
          sellerAmountCents: split.sellerAmountCents,
          paymentMethod,
          paymentStatus: 'PENDING',
          notes,
          items: {
            create: group.items.map((item) => ({
              productId: item.productId,
              titleSnapshot: item.product.title,
              imageSnapshot: item.product.images[0]?.url || null,
              priceCents: item.product.priceCents,
              quantity: item.quantity,
            })),
          },
          statusEvents: { create: { status: 'PENDING_PAYMENT', note: 'Pedido criado.' } },
        },
        include: { items: true },
      });

      // 2) Cobranca no Pagar.me com as regras de split.
      let pagarmeOrder;
      try {
        pagarmeOrder = await pagarme.createOrder({
          code: order.id,
          customer,
          items: [
            ...group.items.map((item) => ({
              amount: item.product.priceCents,
              description: item.product.title.slice(0, 60),
              quantity: item.quantity,
              code: item.productId,
            })),
            ...(quote.cents > 0
              ? [{ amount: quote.cents, description: 'Frete Vendas Itinga', quantity: 1, code: 'FRETE' }]
              : []),
          ],
          paymentMethod,
          cardToken,
          installments,
          split: buildPagarmeSplit({
            sellerRecipientId: group.seller.pagarmeRecipientId,
            platformRecipientId: env.pagarme.platformRecipientId,
            sellerAmountCents: split.sellerAmountCents,
            platformAmountCents: split.platformAmountCents,
          }),
        });
      } catch (gatewayError) {
        await prisma.order.update({
          where: { id: order.id },
          data: {
            status: 'CANCELED',
            paymentStatus: 'FAILED',
            canceledAt: new Date(),
            statusEvents: { create: { status: 'CANCELED', note: gatewayError.message.slice(0, 400) } },
          },
        });
        throw gatewayError;
      }

      const charge = pagarmeOrder.charges?.[0];
      const transaction = charge?.last_transaction;
      const paid = charge?.status === 'paid';

      const updated = await prisma.order.update({
        where: { id: order.id },
        data: {
          pagarmeOrderId: pagarmeOrder.id,
          pagarmeChargeId: charge?.id,
          paymentStatus: paid ? 'PAID' : 'PENDING',
          status: paid ? 'PAID' : 'PENDING_PAYMENT',
          paidAt: paid ? new Date() : null,
          pixQrCode: transaction?.qr_code || null,
          pixQrCodeUrl: transaction?.qr_code_url || null,
          pixExpiresAt: transaction?.expires_at ? new Date(transaction.expires_at) : null,
          ...(paid
            ? { statusEvents: { create: { status: 'PAID', note: 'Pagamento aprovado.' } } }
            : {}),
        },
        include: { items: true },
      });

      if (paid) {
        await onOrderPaid(updated.id);
      }

      createdOrders.push(updated);
    }

    await prisma.cartItem.deleteMany({ where: { userId: req.user.id } });

    return res.status(201).json({
      checkoutId,
      orders: createdOrders.map(orderPayload),
      totalCents: createdOrders.reduce((sum, o) => sum + o.totalCents, 0),
    });
  } catch (error) {
    return next(error);
  }
}

/**
 * Efeitos colaterais de um pedido pago: marca o produto como vendido,
 * agenda o repasse ao vendedor e cria a tarefa de coleta para a frota
 * quando a entrega e da plataforma.
 */
async function onOrderPaid(orderId) {
  const order = await prisma.order.findUnique({
    where: { id: orderId },
    include: {
      items: { include: { product: true } },
      address: true,
      seller: { include: { addresses: { where: { isDefault: true }, take: 1 } } },
      payout: true,
    },
  });
  if (!order) return;

  await prisma.product.updateMany({
    where: { id: { in: order.items.map((i) => i.productId) } },
    data: { status: 'SOLD' },
  });

  await prisma.cartItem.deleteMany({
    where: { productId: { in: order.items.map((i) => i.productId) } },
  });

  if (!order.payout) {
    await prisma.payout.create({
      data: {
        orderId: order.id,
        sellerId: order.sellerId,
        amountCents: order.sellerAmountCents,
        platformFeeCents: order.platformFeeCents,
        status: 'SCHEDULED',
        // Sem data de entrega ainda: estimamos a partir de hoje e
        // reajustamos quando o pedido for marcado como entregue.
        releaseAt: calculateReleaseDate(new Date()),
      },
    });
  }

  if (order.shippingMode === 'PLATFORM') {
    const pickup = order.seller.addresses[0];
    await prisma.delivery.create({
      data: {
        orderId: order.id,
        type: 'DELIVERY',
        status: 'PENDING',
        pickupLabel: `Coleta com ${order.seller.name}`,
        pickupAddress: pickup
          ? `${pickup.street}, ${pickup.number} - ${pickup.district}, ${pickup.city}/${pickup.state}`
          : 'Endereco de coleta a confirmar com o vendedor',
        pickupLatitude: pickup?.latitude,
        pickupLongitude: pickup?.longitude,
        dropoffLabel: `Entrega para ${order.address.recipient}`,
        dropoffAddress: `${order.address.street}, ${order.address.number} - ${order.address.district}, ${order.address.city}/${order.address.state}`,
        dropoffLatitude: order.address.latitude,
        dropoffLongitude: order.address.longitude,
      },
    });
  }
}

/** GET /orders - compras do usuario. */
async function listPurchases(req, res, next) {
  try {
    const orders = await prisma.order.findMany({
      where: { buyerId: req.user.id },
      orderBy: { createdAt: 'desc' },
      include: {
        items: true,
        seller: { select: { id: true, name: true, avatarUrl: true } },
        refund: true,
      },
    });
    return res.json(orders.map(orderPayload));
  } catch (error) {
    return next(error);
  }
}

/** GET /orders/sales - painel de vendas. */
async function listSales(req, res, next) {
  try {
    const orders = await prisma.order.findMany({
      where: { sellerId: req.user.id, status: { not: 'PENDING_PAYMENT' } },
      orderBy: { createdAt: 'desc' },
      include: {
        items: true,
        buyer: { select: { id: true, name: true, avatarUrl: true } },
        address: true,
        refund: true,
      },
    });
    return res.json(orders.map(orderPayload));
  } catch (error) {
    return next(error);
  }
}

/** GET /orders/:id */
async function detail(req, res, next) {
  try {
    const order = await prisma.order.findFirst({
      where: {
        id: req.params.id,
        OR: [{ buyerId: req.user.id }, { sellerId: req.user.id }],
      },
      include: {
        items: true,
        buyer: { select: { id: true, name: true, avatarUrl: true } },
        seller: { select: { id: true, name: true, avatarUrl: true } },
        address: true,
        refund: true,
        deliveries: true,
        statusEvents: { orderBy: { createdAt: 'asc' } },
      },
    });
    if (!order) throw new HttpError(404, 'Pedido nao encontrado.');

    return res.json({ ...orderPayload(order), timeline: order.statusEvents, deliveries: order.deliveries });
  } catch (error) {
    return next(error);
  }
}

/**
 * PATCH /orders/:id/status
 * Usado pelo vendedor (enviado) e por vendedor/frota (entregue).
 * Ao marcar como entregue inicia o periodo de teste de 4 dias.
 */
async function updateStatus(req, res, next) {
  try {
    const { status, trackingCode } = req.body;

    const order = await prisma.order.findFirst({
      where: { id: req.params.id, sellerId: req.user.id },
    });
    if (!order) throw new HttpError(404, 'Pedido nao encontrado.');

    if (status === 'SHIPPED') {
      if (order.status !== 'PAID') throw new HttpError(409, 'So e possivel enviar um pedido pago.');

      const updated = await prisma.order.update({
        where: { id: order.id },
        data: {
          status: 'SHIPPED',
          shippedAt: new Date(),
          trackingCode,
          statusEvents: { create: { status: 'SHIPPED', note: trackingCode ? `Rastreio: ${trackingCode}` : null } },
        },
        include: { items: true },
      });
      return res.json(orderPayload(updated));
    }

    if (status === 'DELIVERED') {
      const updated = await markDelivered(order.id);
      return res.json(orderPayload(updated));
    }

    if (status === 'CANCELED') {
      if (!['PENDING_PAYMENT', 'PAID'].includes(order.status)) {
        throw new HttpError(409, 'Este pedido nao pode mais ser cancelado.');
      }
      if (order.pagarmeChargeId && order.paymentStatus === 'PAID') {
        await pagarme.refundCharge(order.pagarmeChargeId);
      }

      const updated = await prisma.order.update({
        where: { id: order.id },
        data: {
          status: 'CANCELED',
          canceledAt: new Date(),
          paymentStatus: order.paymentStatus === 'PAID' ? 'REFUNDED' : order.paymentStatus,
          statusEvents: { create: { status: 'CANCELED', note: 'Cancelado pelo vendedor.' } },
        },
        include: { items: true },
      });

      // updateMany (e nao update aninhado): um pedido ainda nao pago nao
      // tem payout, e o update aninhado falharia nesse caso.
      await prisma.payout.updateMany({
        where: { orderId: order.id },
        data: { status: 'CANCELED' },
      });

      await prisma.product.updateMany({
        where: { id: { in: updated.items.map((i) => i.productId) } },
        data: { status: 'ACTIVE' },
      });

      return res.json(orderPayload(updated));
    }

    throw new HttpError(422, 'Status invalido.');
  } catch (error) {
    return next(error);
  }
}

/** Marca a entrega e dispara o periodo de teste de 4 dias. */
async function markDelivered(orderId) {
  const now = new Date();
  const testEndsAt = calculateTestEndDate(now);
  const releaseAt = calculateReleaseDate(now);

  const updated = await prisma.order.update({
    where: { id: orderId },
    data: {
      status: 'IN_TEST',
      deliveredAt: now,
      testEndsAt,
      statusEvents: {
        create: {
          status: 'IN_TEST',
          note: `Entregue. Periodo de teste de ${env.rules.testPeriodDays} dias iniciado.`,
        },
      },
    },
    include: { items: true },
  });

  await prisma.payout.updateMany({
    where: { orderId, status: 'SCHEDULED' },
    data: { releaseAt },
  });

  return updated;
}

/** POST /orders/:id/confirm - comprador aprova antes de vencer o teste. */
async function confirmReceipt(req, res, next) {
  try {
    const order = await prisma.order.findFirst({
      where: { id: req.params.id, buyerId: req.user.id },
    });
    if (!order) throw new HttpError(404, 'Pedido nao encontrado.');
    if (!['IN_TEST', 'DELIVERED', 'SHIPPED'].includes(order.status)) {
      throw new HttpError(409, 'Este pedido nao esta aguardando confirmacao.');
    }

    const updated = await prisma.order.update({
      where: { id: order.id },
      data: {
        status: 'COMPLETED',
        completedAt: new Date(),
        statusEvents: { create: { status: 'COMPLETED', note: 'Compra confirmada pelo comprador.' } },
      },
      include: { items: true },
    });

    await prisma.payout.updateMany({
      where: { orderId: order.id, status: 'SCHEDULED' },
      data: { releaseAt: new Date() },
    });

    return res.json(orderPayload(updated));
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  checkoutSchema,
  statusSchema,
  checkout,
  listPurchases,
  listSales,
  detail,
  updateStatus,
  confirmReceipt,
  markDelivered,
  onOrderPaid,
  orderPayload,
};
