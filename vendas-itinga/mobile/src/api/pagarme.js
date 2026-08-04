import Constants from 'expo-constants';

const extra = Constants.expoConfig?.extra || {};
const PUBLIC_KEY = extra.pagarmePublicKey || '';
const TOKENS_URL = 'https://api.pagar.me/core/v5/tokens';

/**
 * Tokeniza o cartao NO DISPOSITIVO usando a chave PUBLICA do Pagar.me.
 *
 * O numero do cartao nunca passa pelo nosso backend: o app troca os dados
 * por um `card_token` de uso unico e envia apenas esse token ao servidor,
 * que cria a cobranca com a chave secreta.
 */
export async function tokenizeCard({ number, holderName, expMonth, expYear, cvv }) {
  if (!PUBLIC_KEY) {
    throw new Error('Chave pública do Pagar.me não configurada (EXPO_PUBLIC_PAGARME_PUBLIC_KEY).');
  }

  const response = await fetch(`${TOKENS_URL}?appId=${encodeURIComponent(PUBLIC_KEY)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      type: 'card',
      card: {
        number: number.replace(/\s/g, ''),
        holder_name: holderName,
        exp_month: Number(expMonth),
        exp_year: Number(expYear),
        cvv,
      },
    }),
  });

  const data = await response.json();

  if (!response.ok) {
    const message =
      data?.message ||
      Object.values(data?.errors || {})?.[0]?.[0] ||
      'Não foi possível validar o cartão.';
    throw new Error(message);
  }

  return data.id;
}

/** Mascara 4444 5555 6666 7777 enquanto o usuario digita. */
export function maskCardNumber(value = '') {
  return value
    .replace(/\D/g, '')
    .slice(0, 16)
    .replace(/(\d{4})(?=\d)/g, '$1 ')
    .trim();
}

/** Mascara MM/AA. */
export function maskExpiry(value = '') {
  const digits = value.replace(/\D/g, '').slice(0, 4);
  if (digits.length <= 2) return digits;
  return `${digits.slice(0, 2)}/${digits.slice(2)}`;
}

/** Validacao basica (algoritmo de Luhn) antes de chamar o gateway. */
export function isValidCardNumber(value = '') {
  const digits = value.replace(/\D/g, '');
  if (digits.length < 13) return false;

  let sum = 0;
  let double = false;
  for (let i = digits.length - 1; i >= 0; i -= 1) {
    let digit = Number(digits[i]);
    if (double) {
      digit *= 2;
      if (digit > 9) digit -= 9;
    }
    sum += digit;
    double = !double;
  }
  return sum % 10 === 0;
}
