'use strict';

const { Router } = require('express');
const { z } = require('zod');

const { requireAuth, optionalAuth } = require('../middlewares/auth');
const { validate } = require('../middlewares/validate');

const auth = require('../controllers/auth.controller');
const users = require('../controllers/user.controller');
const products = require('../controllers/product.controller');
const cart = require('../controllers/cart.controller');
const orders = require('../controllers/order.controller');
const refunds = require('../controllers/refund.controller');
const deliveries = require('../controllers/delivery.controller');
const chat = require('../controllers/chat.controller');
const uploads = require('../controllers/upload.controller');
const webhooks = require('../controllers/webhook.controller');
const env = require('../config/env');

const router = Router();

// ------------------------------- Saude --------------------------------
router.get('/health', (_req, res) =>
  res.json({ status: 'ok', service: 'vendas-itinga-api', timestamp: new Date().toISOString() })
);

/** Regras publicas: o app le daqui para validar antes de enviar. */
router.get('/config/rules', (_req, res) =>
  res.json({
    minProductPriceCents: env.rules.minProductPriceCents,
    maxProductWeightGrams: env.rules.maxProductWeightGrams,
    maxProductHeightCm: env.rules.maxProductHeightCm,
    platformFeePercentFleet: env.rules.platformFeeBpsFleet / 100,
    platformFeePercentOwn: env.rules.platformFeeBpsOwn / 100,
    testPeriodDays: env.rules.testPeriodDays,
  })
);

// ---------------------------- Autenticacao ----------------------------
router.post('/auth/google', validate(auth.googleSchema), auth.loginWithGoogle);
router.get('/auth/me', requireAuth, auth.me);

// ------------------------------ Usuarios ------------------------------
router.patch('/users/me', requireAuth, validate(users.updateProfileSchema), users.updateProfile);
router.post(
  '/users/me/seller-account',
  requireAuth,
  validate(users.sellerAccountSchema),
  users.createSellerAccount
);
router.get('/users/me/balance', requireAuth, users.sellerBalance);
router.get('/users/:id', users.publicProfile);

// ----------------------------- Enderecos ------------------------------
router.get('/addresses', requireAuth, users.listAddresses);
router.post('/addresses', requireAuth, validate(users.addressSchema), users.createAddress);
router.patch('/addresses/:id', requireAuth, validate(users.addressSchema.partial()), users.updateAddress);
router.delete('/addresses/:id', requireAuth, users.deleteAddress);

// ------------------------------- Uploads ------------------------------
router.post('/uploads', requireAuth, validate(uploads.uploadSchema), uploads.uploadImage);

// ------------------------------ Catalogo ------------------------------
router.get('/categories', products.listCategories);
router.get('/products', optionalAuth, validate(products.listQuerySchema, 'query'), products.list);
router.get('/products/:id', optionalAuth, products.detail);
router.post('/products', requireAuth, validate(products.productSchema), products.create);
router.patch('/products/:id', requireAuth, validate(products.productSchema.partial()), products.update);
router.patch(
  '/products/:id/status',
  requireAuth,
  validate(z.object({ status: z.enum(['ACTIVE', 'PAUSED']) })),
  products.changeStatus
);
router.delete('/products/:id', requireAuth, products.remove);

// ------------------------------ Favoritos -----------------------------
router.get('/favorites', requireAuth, products.listFavorites);
router.post('/products/:id/favorite', requireAuth, products.toggleFavorite);

// ------------------------------ Carrinho ------------------------------
router.get('/cart', requireAuth, cart.getCart);
router.post('/cart', requireAuth, validate(cart.addItemSchema), cart.addItem);
router.delete('/cart/:id', requireAuth, cart.removeItem);
router.delete('/cart', requireAuth, cart.clearCart);

// ------------------------------- Pedidos ------------------------------
router.post('/orders/checkout', requireAuth, validate(orders.checkoutSchema), orders.checkout);
router.get('/orders', requireAuth, orders.listPurchases);
router.get('/orders/sales', requireAuth, orders.listSales);
router.get('/orders/:id', requireAuth, orders.detail);
router.patch('/orders/:id/status', requireAuth, validate(orders.statusSchema), orders.updateStatus);
router.post('/orders/:id/confirm', requireAuth, orders.confirmReceipt);

// ------------------------------ Reembolsos ----------------------------
router.post('/orders/:id/refund', requireAuth, validate(refunds.refundSchema), refunds.requestRefund);
router.get('/orders/:id/refund', requireAuth, refunds.getRefund);
router.post(
  '/orders/:id/refund/review',
  requireAuth,
  validate(refunds.reviewSchema),
  refunds.reviewRefund
);

// ------------------------------ Logistica -----------------------------
router.get('/deliveries/available', requireAuth, deliveries.listAvailable);
router.get('/deliveries/mine', requireAuth, deliveries.listMine);
router.post('/deliveries/:id/accept', requireAuth, deliveries.accept);
router.patch(
  '/deliveries/:id',
  requireAuth,
  validate(deliveries.updateDeliverySchema),
  deliveries.updateStatus
);

// -------------------------------- Chat --------------------------------
router.get('/conversations', requireAuth, chat.listConversations);
router.post('/conversations', requireAuth, validate(chat.startSchema), chat.startConversation);
router.get('/conversations/:id/messages', requireAuth, chat.listMessages);
router.post(
  '/conversations/:id/messages',
  requireAuth,
  validate(chat.messageSchema),
  chat.sendMessage
);

// ------------------------------ Webhooks ------------------------------
router.post('/webhooks/pagarme', webhooks.handlePagarme);

module.exports = router;
