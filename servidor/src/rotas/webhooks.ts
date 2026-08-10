/**
 * Webhooks da pagar.me.
 *
 * É por aqui que o Pix vira pedido pago: o app não fica sabendo do pagamento
 * antes da pagar.me avisar. Todo evento é registrado para não ser processado
 * duas vezes (a pagar.me reenvia quando não recebe 2xx).
 */

import { Router } from 'express';

import { log } from '../log';
import { prisma } from '../prisma';
import { webhookAutenticado } from '../integracoes/pagarme';
import { marcarComoPago } from '../servicos/checkout';

export const rotasWebhooks = Router();

interface EventoPagarme {
  id?: string;
  type?: string;
  data?: {
    id?: string;
    code?: string;
    status?: string;
    order?: { id?: string; code?: string };
    charges?: Array<{ id?: string; status?: string }>;
  };
}

/** POST /webhooks/pagarme */
rotasWebhooks.post('/pagarme', async (req, res) => {
  if (!webhookAutenticado(req.headers.authorization)) {
    log.warn('webhook da pagar.me recusado: autenticação inválida');
    return res.status(401).json({ erro: 'nao autorizado' });
  }

  const evento = req.body as EventoPagarme;
  const eventoId = evento.id ?? `${evento.type}-${evento.data?.id}-${Date.now()}`;

  // responde rápido: a pagar.me não deve esperar o nosso processamento
  res.status(200).json({ recebido: true });

  try {
    const jaProcessado = await prisma.webhookRecebido.findUnique({
      where: { eventoId },
    });
    if (jaProcessado) {
      log.debug({ eventoId }, 'webhook repetido, ignorado');
      return;
    }

    await prisma.webhookRecebido.create({
      data: {
        eventoId,
        tipo: evento.type ?? 'desconhecido',
        conteudo: evento as object,
      },
    });

    switch (evento.type) {
      case 'order.paid':
      case 'charge.paid': {
        const codigo = evento.data?.code ?? evento.data?.order?.code;
        const orderId = evento.data?.order?.id ?? evento.data?.id;

        const pedido = await prisma.pedido.findFirst({
          where: {
            OR: [
              ...(codigo ? [{ codigo }] : []),
              ...(orderId ? [{ pagarmeOrderId: orderId }] : []),
            ],
          },
        });

        if (!pedido) {
          log.warn({ codigo, orderId }, 'pagamento sem pedido correspondente');
          return;
        }
        await marcarComoPago(pedido.id);
        break;
      }

      case 'charge.payment_failed':
      case 'order.payment_failed': {
        const codigo = evento.data?.code ?? evento.data?.order?.code;
        if (!codigo) return;
        await prisma.pedido.updateMany({
          where: { codigo, estado: 'AGUARDANDO_PAGAMENTO' },
          data: { estado: 'CANCELADO', canceladoEm: new Date() },
        });
        log.info({ codigo }, 'pagamento recusado, pedido cancelado');
        break;
      }

      case 'charge.refunded':
      case 'order.canceled': {
        const codigo = evento.data?.code ?? evento.data?.order?.code;
        if (!codigo) return;
        const pedido = await prisma.pedido.findUnique({ where: { codigo } });
        if (!pedido) return;
        await prisma.eventoPedido.create({
          data: {
            pedidoId: pedido.id,
            tipo: 'estorno_confirmado_pagarme',
            detalhe: evento as object,
          },
        });
        break;
      }

      default:
        log.debug({ tipo: evento.type }, 'webhook sem tratamento específico');
    }
  } catch (erro) {
    // a resposta já foi enviada; aqui só registramos para investigar depois
    log.error({ erro, eventoId }, 'falha ao processar webhook');
  }
});
