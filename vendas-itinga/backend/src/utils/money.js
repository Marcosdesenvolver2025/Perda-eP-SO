'use strict';

/** Formata centavos como moeda brasileira: 1990 -> "R$ 19,90". */
function formatBRL(cents) {
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

/** Converte "19,90" ou 19.9 para 1990 centavos. */
function toCents(value) {
  if (typeof value === 'number') return Math.round(value * 100);
  const normalized = String(value).replace(/\./g, '').replace(',', '.');
  return Math.round(Number.parseFloat(normalized) * 100);
}

module.exports = { formatBRL, toCents };
