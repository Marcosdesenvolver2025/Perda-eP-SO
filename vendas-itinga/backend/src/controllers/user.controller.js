'use strict';

const { z } = require('zod');
const prisma = require('../lib/prisma');
const pagarme = require('../services/pagarme.service');
const { HttpError } = require('../utils/httpError');

const updateProfileSchema = z.object({
  name: z.string().min(2).max(80).optional(),
  phone: z.string().min(10).max(20).optional(),
  bio: z.string().max(300).optional(),
  avatarUrl: z.string().url().optional(),
  pushToken: z.string().optional(),
  notifications: z
    .object({
      offers: z.boolean().optional(),
      sales: z.boolean().optional(),
      messages: z.boolean().optional(),
    })
    .optional(),
});

const sellerAccountSchema = z.object({
  document: z.string().regex(/^\d{11}$|^\d{14}$/, 'Informe CPF (11) ou CNPJ (14) digitos, apenas numeros.'),
  bankAccount: z.object({
    holderName: z.string().min(3),
    holderDocument: z.string().regex(/^\d{11}$|^\d{14}$/),
    bankCode: z.string().length(3),
    branchNumber: z.string().min(1).max(5),
    branchCheckDigit: z.string().max(2).optional(),
    accountNumber: z.string().min(1).max(13),
    accountCheckDigit: z.string().min(1).max(2),
    accountType: z.enum(['checking', 'savings']).default('checking'),
  }),
});

const addressSchema = z.object({
  label: z.string().max(30).default('Casa'),
  recipient: z.string().min(3),
  zipCode: z.string().regex(/^\d{8}$/, 'CEP deve ter 8 digitos.'),
  street: z.string().min(3),
  number: z.string().min(1),
  complement: z.string().optional(),
  district: z.string().min(2),
  city: z.string().min(2),
  state: z.string().length(2),
  latitude: z.number().optional(),
  longitude: z.number().optional(),
  isDefault: z.boolean().default(false),
});

async function updateProfile(req, res, next) {
  try {
    const { notifications, ...rest } = req.body;
    const user = await prisma.user.update({
      where: { id: req.user.id },
      data: {
        ...rest,
        ...(notifications
          ? {
              notifyOffers: notifications.offers ?? undefined,
              notifySales: notifications.sales ?? undefined,
              notifyMessages: notifications.messages ?? undefined,
            }
          : {}),
      },
    });
    return res.json({ id: user.id, name: user.name, avatarUrl: user.avatarUrl, bio: user.bio });
  } catch (error) {
    return next(error);
  }
}

/**
 * POST /users/me/seller-account
 * Registra o usuario como Recebedor (Recipient) no Pagar.me. Sem isso ele
 * nao pode vender - o split precisa de um recipient_id de destino.
 */
async function createSellerAccount(req, res, next) {
  try {
    if (req.user.pagarmeRecipientId) {
      return res.json({
        recipientId: req.user.pagarmeRecipientId,
        status: req.user.pagarmeRecipientStatus,
        message: 'Conta de vendedor ja cadastrada.',
      });
    }

    const { document, bankAccount } = req.body;
    const documentType = document.length === 14 ? 'CNPJ' : 'CPF';

    const recipient = await pagarme.createRecipient({
      name: req.user.name,
      email: req.user.email,
      document,
      documentType,
      bankAccount,
    });

    const user = await prisma.user.update({
      where: { id: req.user.id },
      data: {
        document,
        role: req.user.role === 'BUYER' ? 'SELLER' : req.user.role,
        pagarmeRecipientId: recipient.id,
        pagarmeRecipientStatus: recipient.status,
        bankAccount: {
          upsert: {
            create: bankAccount,
            update: bankAccount,
          },
        },
      },
    });

    return res.status(201).json({
      recipientId: user.pagarmeRecipientId,
      status: user.pagarmeRecipientStatus,
    });
  } catch (error) {
    return next(error);
  }
}

/** GET /users/me/balance - saldo a receber do vendedor. */
async function sellerBalance(req, res, next) {
  try {
    if (!req.user.pagarmeRecipientId) {
      throw new HttpError(400, 'Cadastre sua conta de vendedor para consultar o saldo.');
    }

    const [scheduled, released] = await Promise.all([
      prisma.payout.aggregate({
        where: { sellerId: req.user.id, status: 'SCHEDULED' },
        _sum: { amountCents: true },
      }),
      prisma.payout.aggregate({
        where: { sellerId: req.user.id, status: 'RELEASED' },
        _sum: { amountCents: true },
      }),
    ]);

    let gateway = null;
    try {
      gateway = await pagarme.getRecipientBalance(req.user.pagarmeRecipientId);
    } catch (err) {
      // O saldo do gateway e informativo: nao derruba a resposta.
      console.warn('[balance] falha ao consultar Pagar.me:', err.message);
    }

    return res.json({
      pendingCents: scheduled._sum.amountCents || 0,
      receivedCents: released._sum.amountCents || 0,
      gateway,
    });
  } catch (error) {
    return next(error);
  }
}

/** GET /users/:id - perfil publico do vendedor. */
async function publicProfile(req, res, next) {
  try {
    const user = await prisma.user.findUnique({
      where: { id: req.params.id },
      select: {
        id: true,
        name: true,
        avatarUrl: true,
        bio: true,
        ratingAvg: true,
        ratingCount: true,
        createdAt: true,
        _count: { select: { products: true, ordersAsSeller: true } },
      },
    });
    if (!user) throw new HttpError(404, 'Usuario nao encontrado.');
    return res.json(user);
  } catch (error) {
    return next(error);
  }
}

// ------------------------------ Enderecos ------------------------------

async function listAddresses(req, res, next) {
  try {
    const addresses = await prisma.address.findMany({
      where: { userId: req.user.id },
      orderBy: [{ isDefault: 'desc' }, { createdAt: 'desc' }],
    });
    return res.json(addresses);
  } catch (error) {
    return next(error);
  }
}

async function createAddress(req, res, next) {
  try {
    const data = req.body;
    if (data.isDefault) {
      await prisma.address.updateMany({
        where: { userId: req.user.id },
        data: { isDefault: false },
      });
    }
    const count = await prisma.address.count({ where: { userId: req.user.id } });
    const address = await prisma.address.create({
      data: { ...data, isDefault: data.isDefault || count === 0, userId: req.user.id },
    });
    return res.status(201).json(address);
  } catch (error) {
    return next(error);
  }
}

async function updateAddress(req, res, next) {
  try {
    const existing = await prisma.address.findFirst({
      where: { id: req.params.id, userId: req.user.id },
    });
    if (!existing) throw new HttpError(404, 'Endereco nao encontrado.');

    if (req.body.isDefault) {
      await prisma.address.updateMany({
        where: { userId: req.user.id },
        data: { isDefault: false },
      });
    }

    const address = await prisma.address.update({ where: { id: existing.id }, data: req.body });
    return res.json(address);
  } catch (error) {
    return next(error);
  }
}

async function deleteAddress(req, res, next) {
  try {
    const existing = await prisma.address.findFirst({
      where: { id: req.params.id, userId: req.user.id },
    });
    if (!existing) throw new HttpError(404, 'Endereco nao encontrado.');
    await prisma.address.delete({ where: { id: existing.id } });
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  updateProfileSchema,
  sellerAccountSchema,
  addressSchema,
  updateProfile,
  createSellerAccount,
  sellerBalance,
  publicProfile,
  listAddresses,
  createAddress,
  updateAddress,
  deleteAddress,
};
