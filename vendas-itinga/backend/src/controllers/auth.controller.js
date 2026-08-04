'use strict';

const { z } = require('zod');
const prisma = require('../lib/prisma');
const authService = require('../services/auth.service');

const googleSchema = z.object({
  idToken: z.string().min(10, 'idToken obrigatorio.'),
  provider: z.enum(['google', 'firebase']).optional(),
  pushToken: z.string().optional(),
});

/**
 * POST /auth/google
 * Recebe o ID token do Google Sign-In (direto ou via Firebase),
 * valida no servidor e devolve o JWT de sessao do Vendas Itinga.
 */
async function loginWithGoogle(req, res, next) {
  try {
    const { idToken, provider, pushToken } = req.body;

    const profile = await authService.verifyIdToken(idToken, provider);
    let user = await authService.upsertUserFromProfile(profile);

    if (pushToken && pushToken !== user.pushToken) {
      user = await prisma.user.update({ where: { id: user.id }, data: { pushToken } });
    }

    return res.json({
      token: authService.signAppToken(user),
      user: {
        id: user.id,
        name: user.name,
        email: user.email,
        avatarUrl: user.avatarUrl,
        role: user.role,
        isSeller: Boolean(user.pagarmeRecipientId),
      },
    });
  } catch (error) {
    return next(error);
  }
}

/** GET /auth/me - devolve a sessao atual. */
async function me(req, res) {
  const user = req.user;
  return res.json({
    id: user.id,
    name: user.name,
    email: user.email,
    avatarUrl: user.avatarUrl,
    phone: user.phone,
    bio: user.bio,
    role: user.role,
    ratingAvg: user.ratingAvg,
    ratingCount: user.ratingCount,
    isSeller: Boolean(user.pagarmeRecipientId),
    notifications: {
      offers: user.notifyOffers,
      sales: user.notifySales,
      messages: user.notifyMessages,
    },
  });
}

module.exports = { googleSchema, loginWithGoogle, me };
