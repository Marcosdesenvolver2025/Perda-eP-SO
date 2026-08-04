/** Formata centavos como moeda: 1990 -> "R$ 19,90". */
export function formatBRL(cents = 0) {
  return (cents / 100).toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
  });
}

/** Converte texto digitado ("19,90" / "1990") para centavos. */
export function parseToCents(text = '') {
  const digits = String(text).replace(/\D/g, '');
  return digits ? Number.parseInt(digits, 10) : 0;
}

/** Mascara de moeda enquanto o usuario digita. */
export function maskCurrency(text = '') {
  const cents = parseToCents(text);
  return formatBRL(cents);
}

export function formatDate(value) {
  if (!value) return '';
  return new Date(value).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

export function formatDateTime(value) {
  if (!value) return '';
  return new Date(value).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** "há 3 dias", "agora" - usado no chat e no feed. */
export function timeAgo(value) {
  if (!value) return '';
  const diff = Date.now() - new Date(value).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return 'agora';
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} d`;
  return formatDate(value);
}

/** Dias restantes do periodo de teste de 4 dias. */
export function daysLeft(dateValue) {
  if (!dateValue) return 0;
  const diff = new Date(dateValue).getTime() - Date.now();
  return Math.max(0, Math.ceil(diff / (24 * 60 * 60 * 1000)));
}

export const CONDITION_LABELS = {
  NEW: 'Novo',
  LIKE_NEW: 'Seminovo',
  USED: 'Usado',
};

export const ORDER_STATUS_LABELS = {
  PENDING_PAYMENT: 'Aguardando pagamento',
  PAID: 'Pagamento aprovado',
  SHIPPED: 'A caminho',
  DELIVERED: 'Entregue',
  IN_TEST: 'Em período de teste',
  COMPLETED: 'Concluído',
  RETURN_REQUESTED: 'Devolução solicitada',
  RETURN_IN_TRANSIT: 'Coleta a caminho',
  RETURNED: 'Devolvido',
  REFUNDED: 'Reembolsado',
  CANCELED: 'Cancelado',
};

export const ORDER_STATUS_TONE = {
  PENDING_PAYMENT: 'warning',
  PAID: 'info',
  SHIPPED: 'info',
  DELIVERED: 'success',
  IN_TEST: 'warning',
  COMPLETED: 'success',
  RETURN_REQUESTED: 'danger',
  RETURN_IN_TRANSIT: 'danger',
  RETURNED: 'danger',
  REFUNDED: 'danger',
  CANCELED: 'danger',
};
