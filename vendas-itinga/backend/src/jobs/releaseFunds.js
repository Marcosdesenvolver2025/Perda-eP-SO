'use strict';

const prisma = require('../lib/prisma');
const env = require('../config/env');

/**
 * Encerra o periodo de teste de 4 dias e libera o repasse ao vendedor.
 *
 * Passo 1: pedidos IN_TEST cujo testEndsAt ja passou e que NAO possuem
 *          solicitacao de reembolso aberta viram COMPLETED.
 * Passo 2: repasses agendados cuja data de liberacao chegou (fim do
 *          teste + prazo de liquidacao do gateway) viram RELEASED.
 *
 * O split ja foi executado pelo Pagar.me no momento da cobranca; este job
 * e o controle interno do que esta efetivamente liberado para o vendedor.
 */
async function releaseFunds() {
  const now = new Date();

  const expired = await prisma.order.findMany({
    where: {
      status: 'IN_TEST',
      testEndsAt: { lte: now },
      refund: { is: null },
    },
    select: { id: true },
  });

  for (const order of expired) {
    await prisma.order.update({
      where: { id: order.id },
      data: {
        status: 'COMPLETED',
        completedAt: now,
        statusEvents: {
          create: {
            status: 'COMPLETED',
            note: `Periodo de teste de ${env.rules.testPeriodDays} dias encerrado sem contestacao.`,
          },
        },
      },
    });
  }

  const released = await prisma.payout.updateMany({
    where: {
      status: 'SCHEDULED',
      releaseAt: { lte: now },
      order: { status: 'COMPLETED' },
    },
    data: { status: 'RELEASED', releasedAt: now },
  });

  const summary = { completedOrders: expired.length, releasedPayouts: released.count };
  console.log('[job:releaseFunds]', new Date().toISOString(), summary);
  return summary;
}

module.exports = { releaseFunds };

if (require.main === module) {
  releaseFunds()
    .then(() => process.exit(0))
    .catch((error) => {
      console.error('[job:releaseFunds] falhou:', error);
      process.exit(1);
    });
}
