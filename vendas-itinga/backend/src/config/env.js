'use strict';

require('dotenv').config();

function required(name, fallback) {
  const value = process.env[name] ?? fallback;
  if (value === undefined || value === '') {
    if (process.env.NODE_ENV === 'production') {
      throw new Error(`Variavel de ambiente obrigatoria ausente: ${name}`);
    }
    console.warn(`[env] ${name} nao definida - usando valor vazio (apenas dev).`);
    return '';
  }
  return value;
}

function int(name, fallback) {
  const raw = process.env[name];
  const parsed = Number.parseInt(raw ?? '', 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

const env = {
  nodeEnv: process.env.NODE_ENV || 'development',
  isProduction: process.env.NODE_ENV === 'production',
  port: int('PORT', 3333),
  apiPublicUrl: process.env.API_PUBLIC_URL || 'http://localhost:3333',

  databaseUrl: required('DATABASE_URL'),

  jwtSecret: required('JWT_SECRET', 'dev-secret-nao-use-em-producao'),
  jwtExpiresIn: process.env.JWT_EXPIRES_IN || '30d',

  googleClientIds: (process.env.GOOGLE_CLIENT_IDS || '')
    .split(',')
    .map((id) => id.trim())
    .filter(Boolean),

  firebase: {
    projectId: process.env.FIREBASE_PROJECT_ID || '',
    clientEmail: process.env.FIREBASE_CLIENT_EMAIL || '',
    privateKey: (process.env.FIREBASE_PRIVATE_KEY || '').replace(/\\n/g, '\n'),
  },

  pagarme: {
    apiKey: required('PAGARME_API_KEY'),
    encryptionKey: process.env.PAGARME_ENCRYPTION_KEY || '',
    apiUrl: process.env.PAGARME_API_URL || 'https://api.pagar.me/core/v5',
    platformRecipientId: process.env.PAGARME_PLATFORM_RECIPIENT_ID || '',
    webhookUser: process.env.PAGARME_WEBHOOK_USER || '',
    webhookPassword: process.env.PAGARME_WEBHOOK_PASSWORD || '',
  },

  rules: {
    minProductPriceCents: int('MIN_PRODUCT_PRICE_CENTS', 1000),
    maxProductWeightGrams: int('MAX_PRODUCT_WEIGHT_GRAMS', 20000),
    maxProductHeightCm: int('MAX_PRODUCT_HEIGHT_CM', 60),
    platformFeeBpsFleet: int('PLATFORM_FEE_BPS_FLEET', 2000),
    platformFeeBpsOwn: int('PLATFORM_FEE_BPS_OWN', 1500),
    testPeriodDays: int('TEST_PERIOD_DAYS', 4),
    gatewaySettlementDays: int('GATEWAY_SETTLEMENT_DAYS', 2),
  },

  shipping: {
    baseCents: int('SHIPPING_BASE_CENTS', 990),
    perKgCents: int('SHIPPING_PER_KG_CENTS', 200),
    maxRadiusKm: int('SHIPPING_MAX_RADIUS_KM', 30),
  },
};

module.exports = env;
