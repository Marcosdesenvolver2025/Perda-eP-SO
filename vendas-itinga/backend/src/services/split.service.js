'use strict';

const env = require('../config/env');

/**
 * Regras comerciais do Vendas Itinga (definidas no Prompt Mestre):
 *
 *  - Entrega pela frota da plataforma (PLATFORM): a plataforma retem 20%.
 *  - Entrega propria do vendedor (SELLER):        a plataforma retem 15%.
 *
 * A comissao incide sobre o valor dos PRODUTOS. O frete cobrado do
 * comprador quando a entrega e feita pela frota pertence integralmente a
 * plataforma (ela custeia a operacao logistica). Na entrega propria, o
 * frete e repassado ao vendedor.
 */

const FEE_BPS = {
  PLATFORM: env.rules.platformFeeBpsFleet, // 2000 bps = 20%
  SELLER: env.rules.platformFeeBpsOwn, // 1500 bps = 15%
};

function feeBpsFor(shippingMode) {
  const bps = FEE_BPS[shippingMode];
  if (bps === undefined) {
    throw new Error(`Modalidade de entrega invalida: ${shippingMode}`);
  }
  return bps;
}

/**
 * Calcula a divisao de um pedido de um unico vendedor.
 *
 * @param {object} params
 * @param {number} params.itemsTotalCents  soma dos produtos, em centavos
 * @param {number} params.shippingCents    frete cobrado do comprador
 * @param {'PLATFORM'|'SELLER'} params.shippingMode
 * @returns {{
 *   platformFeeBps: number,
 *   platformFeeCents: number,
 *   sellerAmountCents: number,
 *   platformAmountCents: number,
 *   totalCents: number
 * }}
 */
function calculateSplit({ itemsTotalCents, shippingCents = 0, shippingMode }) {
  if (!Number.isInteger(itemsTotalCents) || itemsTotalCents <= 0) {
    throw new Error('itemsTotalCents deve ser um inteiro positivo (centavos).');
  }
  if (!Number.isInteger(shippingCents) || shippingCents < 0) {
    throw new Error('shippingCents deve ser um inteiro >= 0 (centavos).');
  }

  const platformFeeBps = feeBpsFor(shippingMode);

  // Arredonda a comissao para baixo: eventuais centavos residuais ficam com
  // o vendedor, evitando que a soma do split ultrapasse o total cobrado.
  const platformFeeCents = Math.floor((itemsTotalCents * platformFeeBps) / 10000);

  const totalCents = itemsTotalCents + shippingCents;

  // O frete da frota propria custeia a operacao logistica da plataforma.
  const shippingToPlatform = shippingMode === 'PLATFORM' ? shippingCents : 0;
  const shippingToSeller = shippingCents - shippingToPlatform;

  const platformAmountCents = platformFeeCents + shippingToPlatform;
  const sellerAmountCents = totalCents - platformAmountCents;

  return {
    platformFeeBps,
    platformFeeCents,
    sellerAmountCents,
    platformAmountCents,
    totalCents,
  };
}

/**
 * Monta o array de split no formato esperado pela API v5 do Pagar.me.
 * Usamos type "flat" (valores absolutos em centavos) para que o resultado
 * seja identico ao calculado e auditado no nosso banco.
 */
function buildPagarmeSplit({ sellerRecipientId, platformRecipientId, sellerAmountCents, platformAmountCents }) {
  if (!sellerRecipientId) {
    throw new Error('Vendedor sem recebedor Pagar.me cadastrado.');
  }
  if (!platformRecipientId) {
    throw new Error('PAGARME_PLATFORM_RECIPIENT_ID nao configurado.');
  }

  return [
    {
      amount: platformAmountCents,
      recipient_id: platformRecipientId,
      type: 'flat',
      options: {
        charge_processing_fee: true, // a plataforma absorve a taxa do gateway
        charge_remainder_fee: true,
        liable: true, // e a plataforma quem responde por chargebacks
      },
    },
    {
      amount: sellerAmountCents,
      recipient_id: sellerRecipientId,
      type: 'flat',
      options: {
        charge_processing_fee: false,
        charge_remainder_fee: false,
        liable: false,
      },
    },
  ];
}

/**
 * Data em que o valor fica disponivel para o vendedor:
 * fim do periodo de teste do comprador (4 dias apos a entrega)
 * + prazo padrao de liquidacao do gateway.
 */
function calculateReleaseDate(deliveredAt = new Date()) {
  const release = new Date(deliveredAt.getTime());
  release.setDate(release.getDate() + env.rules.testPeriodDays + env.rules.gatewaySettlementDays);
  return release;
}

/** Fim do periodo de teste de 4 dias do comprador. */
function calculateTestEndDate(deliveredAt = new Date()) {
  const end = new Date(deliveredAt.getTime());
  end.setDate(end.getDate() + env.rules.testPeriodDays);
  return end;
}

module.exports = {
  FEE_BPS,
  feeBpsFor,
  calculateSplit,
  buildPagarmeSplit,
  calculateReleaseDate,
  calculateTestEndDate,
};
