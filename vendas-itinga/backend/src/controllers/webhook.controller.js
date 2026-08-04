'use strict';

const prisma = require('../lib/prisma');
const env = require('../config/env');
const { onOrderPaid } = require('./order.controller');

/**
 * Valida o Basic Auth configurado no painel de webhooks do Pagar.me.
 * Sem credenciais configuradas o endpoint segue aberto apenas fora de
 * producao (facilita o desenvolvimento local).
 */
function verifyWebhookAuth(req) {
  if (!env.pagarme.webhookUser) return !env.isProduction;

  const header = req.headers.authorization || '';
  if (!header.startsWith('Basic ')) return false;

  const decoded = Buffer.from(header.slice(6), 'base64').toString('utf8');
  const separator = decoded.indexOf(':');
  const user = decoded.slice(0, separator);
  const password = decoded.slice(separator + 1);

  return user === env.pagarme.webhookUser && password === env.pagarme.webhookPassword;
}

/**
 * POST /webhooks/pagarme
 *
 * Eventos tratados:
 *  - order.paid / charge.paid          -> confirma o pagamento (essencial para PIX)
 *  - charge.payment_failed             -> marca falha
 *  - charge.refunded                   -> marca estorno
 *  - charge.chargedback                -> marca chargeback
 */
async function handlePagarme(req, res) {
  if (!verifyWebhookAuth(req)) {
    return res.status(401).json({ error: 'Webhook nao autorizado.' });
  }

  const event = req.body || {};
  const type = event.type || 'unknown';

  const record = await prisma.webhookEvent.create({
    data: { provider: 'pagarme', externalId: event.id || null, type, payload: event },
  });

  // Respondemos 200 imediatamente: o Pagar.me reenvia em caso de erro, e
  // um retry por falha de processamento interno so geraria ruido.
  res.status(200).json({ received: true });

  try {
    const data = event.data || {};
    const charge = data.charges?.[0] || data;
    const orderCode = data.code || charge?.order?.code || charge?.metadata?.order_code;
    const pagarmeOrderId = data.id?.startsWith?.('or_') ? data.id : charge?.order?.id;

    const order = await prisma.order.findFirst({
      where: {
        OR: [
          ...(orderCode ? [{ id: orderCode }] : []),
          ...(pagarmeOrderId ? [{ pagarmeOrderId }] : []),
          ...(charge?.id ? [{ pagarmeChargeId: charge.id }] : []),
        ],
      },
    });

    if (!order) {
      await prisma.webhookEvent.update({
        where: { id: record.id },
        data: { processed: true, error: 'Pedido correspondente nao encontrado.' },
      });
      return undefined;
    }

    switch (type) {
      case 'order.paid':
      case 'charge.paid': {
        if (order.paymentStatus !== 'PAID') {
          await prisma.order.update({
            where: { id: order.id },
            data: {
              status: 'PAID',
              paymentStatus: 'PAID',
              paidAt: new Date(),
              pagarmeChargeId: charge?.id || order.pagarmeChargeId,
              statusEvents: { create: { status: 'PAID', note: `Pagamento confirmado (${type}).` } },
            },
          });
          await onOrderPaid(order.id);
        }
        break;
      }

      case 'charge.payment_failed':
      case 'order.payment_failed': {
        await prisma.order.update({
          where: { id: order.id },
          data: {
            status: 'CANCELED',
            paymentStatus: 'FAILED',
            canceledAt: new Date(),
            statusEvents: { create: { status: 'CANCELED', note: 'Pagamento recusado pelo gateway.' } },
          },
        });
        break;
      }

      case 'charge.refunded': {
        await prisma.order.update({
          where: { id: order.id },
          data: {
            paymentStatus: 'REFUNDED',
            statusEvents: { create: { status: 'REFUNDED', note: 'Estorno confirmado pelo gateway.' } },
          },
        });
        break;
      }

      case 'charge.chargedback': {
        await prisma.order.update({
          where: { id: order.id },
          data: {
            paymentStatus: 'CHARGEDBACK',
            statusEvents: { create: { status: 'REFUNDED', note: 'Chargeback registrado pelo gateway.' } },
          },
        });
        await prisma.payout.updateMany({
          where: { orderId: order.id, status: 'SCHEDULED' },
          data: { status: 'CANCELED' },
        });
        break;
      }

      default:
        break;
    }

    await prisma.webhookEvent.update({ where: { id: record.id }, data: { processed: true } });
  } catch (error) {
    console.error('[webhook pagarme]', error);
    await prisma.webhookEvent.update({
      where: { id: record.id },
      data: { processed: false, error: error.message.slice(0, 500) },
    });
  }

  return undefined;
}

module.exports = { handlePagarme };
