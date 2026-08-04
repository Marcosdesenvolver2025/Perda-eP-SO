'use strict';

const { z } = require('zod');
const prisma = require('../lib/prisma');
const { HttpError } = require('../utils/httpError');
const { quoteShipping, distanceKm } = require('../services/shipping.service');

const addItemSchema = z.object({
  productId: z.string().min(1),
  quantity: z.number().int().min(1).max(10).default(1),
});

/**
 * GET /cart
 * O carrinho aceita itens de MULTIPLOS vendedores; agrupamos por vendedor
 * porque cada grupo vira um pedido independente (com seu proprio split e
 * seu proprio frete).
 */
async function getCart(req, res, next) {
  try {
    const items = await prisma.cartItem.findMany({
      where: { userId: req.user.id },
      orderBy: { createdAt: 'desc' },
      include: {
        product: {
          include: {
            images: { orderBy: { position: 'asc' }, take: 1 },
            seller: { select: { id: true, name: true, avatarUrl: true } },
          },
        },
      },
    });

    const address = await prisma.address.findFirst({
      where: { userId: req.user.id, isDefault: true },
    });

    const groups = new Map();

    for (const item of items) {
      if (item.product.status !== 'ACTIVE') continue;

      const sellerId = item.product.sellerId;
      if (!groups.has(sellerId)) {
        groups.set(sellerId, {
          seller: item.product.seller,
          shippingMode: item.product.shippingMode,
          items: [],
          itemsTotalCents: 0,
          weightGrams: 0,
          shippingCents: 0,
        });
      }

      const group = groups.get(sellerId);
      group.items.push({
        id: item.id,
        productId: item.product.id,
        title: item.product.title,
        image: item.product.images[0]?.url || null,
        priceCents: item.product.priceCents,
        quantity: item.quantity,
        shippingMode: item.product.shippingMode,
      });
      group.itemsTotalCents += item.product.priceCents * item.quantity;
      group.weightGrams += item.product.weightGrams * item.quantity;

      // Se qualquer item do vendedor exige a frota, o pedido inteiro vai
      // pela frota (nao ha como dividir uma coleta no mesmo endereco).
      if (item.product.shippingMode === 'PLATFORM') group.shippingMode = 'PLATFORM';

      group.distance =
        group.distance ??
        (address?.latitude != null && item.product.latitude != null
          ? distanceKm(address.latitude, address.longitude, item.product.latitude, item.product.longitude)
          : null);
    }

    let itemsTotalCents = 0;
    let shippingTotalCents = 0;

    const sellerGroups = [...groups.values()].map((group) => {
      const quote = quoteShipping({
        shippingMode: group.shippingMode,
        weightGrams: group.weightGrams,
        distance: group.distance,
      });
      group.shippingCents = quote.cents;
      itemsTotalCents += group.itemsTotalCents;
      shippingTotalCents += quote.cents;

      return {
        seller: group.seller,
        shippingMode: group.shippingMode,
        shippingLabel: quote.label,
        shippingCents: quote.cents,
        itemsTotalCents: group.itemsTotalCents,
        totalCents: group.itemsTotalCents + quote.cents,
        items: group.items,
      };
    });

    return res.json({
      groups: sellerGroups,
      itemsTotalCents,
      shippingTotalCents,
      totalCents: itemsTotalCents + shippingTotalCents,
      itemCount: sellerGroups.reduce((sum, g) => sum + g.items.length, 0),
    });
  } catch (error) {
    return next(error);
  }
}

async function addItem(req, res, next) {
  try {
    const { productId, quantity } = req.body;

    const product = await prisma.product.findUnique({ where: { id: productId } });
    if (!product || product.status !== 'ACTIVE') {
      throw new HttpError(404, 'Produto indisponivel.');
    }
    if (product.sellerId === req.user.id) {
      throw new HttpError(400, 'Voce nao pode comprar o proprio anuncio.');
    }

    const item = await prisma.cartItem.upsert({
      where: { userId_productId: { userId: req.user.id, productId } },
      create: { userId: req.user.id, productId, quantity },
      update: { quantity },
    });

    return res.status(201).json(item);
  } catch (error) {
    return next(error);
  }
}

async function removeItem(req, res, next) {
  try {
    const item = await prisma.cartItem.findFirst({
      where: { id: req.params.id, userId: req.user.id },
    });
    if (!item) throw new HttpError(404, 'Item nao encontrado no carrinho.');

    await prisma.cartItem.delete({ where: { id: item.id } });
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
}

async function clearCart(req, res, next) {
  try {
    await prisma.cartItem.deleteMany({ where: { userId: req.user.id } });
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
}

module.exports = { addItemSchema, getCart, addItem, removeItem, clearCart };
