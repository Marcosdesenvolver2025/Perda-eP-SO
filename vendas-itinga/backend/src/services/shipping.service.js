'use strict';

const env = require('../config/env');

const EARTH_RADIUS_KM = 6371;

function toRad(deg) {
  return (deg * Math.PI) / 180;
}

/** Distancia em km entre duas coordenadas (formula de Haversine). */
function distanceKm(aLat, aLng, bLat, bLng) {
  if ([aLat, aLng, bLat, bLng].some((v) => typeof v !== 'number' || Number.isNaN(v))) {
    return null;
  }
  const dLat = toRad(bLat - aLat);
  const dLng = toRad(bLng - aLng);
  const lat1 = toRad(aLat);
  const lat2 = toRad(bLat);

  const h =
    Math.sin(dLat / 2) ** 2 + Math.sin(dLng / 2) ** 2 * Math.cos(lat1) * Math.cos(lat2);
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
}

/**
 * Cotacao do frete da frota propria do Vendas Itinga.
 * Modelo simples e transparente: taxa base + peso + faixa de distancia.
 * Na entrega propria do vendedor o frete e definido por ele (ou gratis).
 */
function quoteShipping({ shippingMode, weightGrams, distance }) {
  if (shippingMode === 'SELLER') {
    return { cents: 0, mode: 'SELLER', label: 'Combinado com o vendedor' };
  }

  const weightKg = Math.max(1, Math.ceil((weightGrams || 0) / 1000));
  let cents = env.shipping.baseCents + (weightKg - 1) * env.shipping.perKgCents;

  if (typeof distance === 'number' && distance > 10) {
    // Acrescimo de R$ 1,50 a cada 10 km acima dos 10 km iniciais.
    cents += Math.ceil((distance - 10) / 10) * 150;
  }

  return {
    cents,
    mode: 'PLATFORM',
    label: 'Entrega Vendas Itinga',
    distanceKm: typeof distance === 'number' ? Number(distance.toFixed(1)) : null,
  };
}

/** Valida as restricoes fisicas: max 20 kg e max 60 cm de altura. */
function validateDimensions({ weightGrams, heightCm }) {
  const errors = [];
  if (weightGrams > env.rules.maxProductWeightGrams) {
    errors.push(
      `Peso maximo permitido: ${env.rules.maxProductWeightGrams / 1000} kg (informado: ${(weightGrams / 1000).toFixed(2)} kg).`
    );
  }
  if (heightCm > env.rules.maxProductHeightCm) {
    errors.push(
      `Altura maxima permitida: ${env.rules.maxProductHeightCm} cm (informada: ${heightCm} cm).`
    );
  }
  return errors;
}

module.exports = { distanceKm, quoteShipping, validateDimensions };
