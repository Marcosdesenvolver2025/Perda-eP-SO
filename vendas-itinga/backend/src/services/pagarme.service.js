'use strict';

const axios = require('axios');
const env = require('../config/env');

/**
 * Cliente da API v5 do Pagar.me.
 *
 * SEGURANCA: a chave secreta vive apenas em process.env, no servidor.
 * O aplicativo nunca ve a chave - ele envia apenas o token do cartao
 * (gerado no dispositivo com a public/encryption key) ou pede um PIX.
 */
const http = axios.create({
  baseURL: env.pagarme.apiUrl,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
  auth: { username: env.pagarme.apiKey, password: '' },
});

function unwrapError(error, context) {
  const data = error.response?.data;
  const detail = data?.errors
    ? JSON.stringify(data.errors)
    : data?.message || error.message;
  const err = new Error(`Pagar.me (${context}): ${detail}`);
  err.status = error.response?.status || 502;
  err.provider = 'pagarme';
  err.providerPayload = data;
  return err;
}

/**
 * Cria um Recebedor (Recipient) para o vendedor.
 * Cada vendedor do Vendas Itinga precisa de um recipient para receber
 * automaticamente a sua parte do split.
 */
async function createRecipient({ name, email, document, documentType, bankAccount }) {
  try {
    const payload = {
      name,
      email,
      description: `Vendedor Vendas Itinga - ${name}`,
      document,
      type: documentType === 'CNPJ' ? 'company' : 'individual',
      default_bank_account: {
        holder_name: bankAccount.holderName,
        holder_type: documentType === 'CNPJ' ? 'company' : 'individual',
        holder_document: bankAccount.holderDocument,
        bank: bankAccount.bankCode,
        branch_number: bankAccount.branchNumber,
        branch_check_digit: bankAccount.branchCheckDigit || undefined,
        account_number: bankAccount.accountNumber,
        account_check_digit: bankAccount.accountCheckDigit,
        type: bankAccount.accountType || 'checking',
      },
      transfer_settings: {
        transfer_enabled: true,
        transfer_interval: 'Daily',
        transfer_day: 0,
      },
      automatic_anticipation_settings: { enabled: false },
    };

    const { data } = await http.post('/recipients', payload);
    return data;
  } catch (error) {
    throw unwrapError(error, 'createRecipient');
  }
}

async function getRecipient(recipientId) {
  try {
    const { data } = await http.get(`/recipients/${recipientId}`);
    return data;
  } catch (error) {
    throw unwrapError(error, 'getRecipient');
  }
}

async function getRecipientBalance(recipientId) {
  try {
    const { data } = await http.get(`/recipients/${recipientId}/balance`);
    return data;
  } catch (error) {
    throw unwrapError(error, 'getRecipientBalance');
  }
}

/**
 * Cria um pedido com split.
 *
 * @param {object} params
 * @param {string} params.code            id interno do pedido
 * @param {object} params.customer        dados do comprador
 * @param {Array}  params.items           [{ amount, description, quantity, code }]
 * @param {'CREDIT_CARD'|'PIX'} params.paymentMethod
 * @param {Array}  params.split           regras de split (split.service.js)
 * @param {string} [params.cardToken]     token do cartao gerado no app
 * @param {number} [params.installments]
 * @param {object} [params.billingAddress]
 */
async function createOrder({
  code,
  customer,
  items,
  paymentMethod,
  split,
  cardToken,
  installments = 1,
  billingAddress,
  pixExpiresIn = 3600,
}) {
  try {
    const payment =
      paymentMethod === 'PIX'
        ? {
            payment_method: 'pix',
            pix: { expires_in: pixExpiresIn },
            split,
          }
        : {
            payment_method: 'credit_card',
            credit_card: {
              installments,
              statement_descriptor: 'VENDASITINGA',
              card_token: cardToken,
              ...(billingAddress ? { card: { billing_address: billingAddress } } : {}),
            },
            split,
          };

    const payload = {
      code,
      closed: true,
      customer,
      items,
      payments: [payment],
      metadata: { platform: 'vendas-itinga', order_code: code },
    };

    const { data } = await http.post('/orders', payload);
    return data;
  } catch (error) {
    throw unwrapError(error, 'createOrder');
  }
}

async function getOrder(pagarmeOrderId) {
  try {
    const { data } = await http.get(`/orders/${pagarmeOrderId}`);
    return data;
  } catch (error) {
    throw unwrapError(error, 'getOrder');
  }
}

/**
 * Estorno (total ou parcial) de uma cobranca.
 * Em reembolsos por desconformidade, estornamos ao comprador o valor do
 * produto e do frete, mantendo a comissao da plataforma retida - o custo
 * operacional do processamento permanece a cargo do vendedor.
 */
async function refundCharge(chargeId, amountCents) {
  try {
    const { data } = await http.delete(`/charges/${chargeId}`, {
      data: amountCents ? { amount: amountCents } : {},
    });
    return data;
  } catch (error) {
    throw unwrapError(error, 'refundCharge');
  }
}

async function getCharge(chargeId) {
  try {
    const { data } = await http.get(`/charges/${chargeId}`);
    return data;
  } catch (error) {
    throw unwrapError(error, 'getCharge');
  }
}

module.exports = {
  http,
  createRecipient,
  getRecipient,
  getRecipientBalance,
  createOrder,
  getOrder,
  refundCharge,
  getCharge,
};
