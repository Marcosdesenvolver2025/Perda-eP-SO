/** Formatação de dinheiro, peso e datas. Dinheiro sempre chega em centavos. */

export function reais(centavos: number | null | undefined): string {
  if (centavos == null) return '—';
  return (centavos / 100).toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  });
}

/** Preço sem os centavos quando são zero: "R$ 80" em vez de "R$ 80,00". */
export function precoCurto(centavos: number): string {
  return centavos % 100 === 0
    ? `R$ ${Math.round(centavos / 100)}`
    : reais(centavos);
}

export function peso(gramas: number): string {
  return gramas >= 1000
    ? `${(gramas / 1000).toFixed(gramas % 1000 === 0 ? 0 : 1)} kg`
    : `${gramas} g`;
}

export function medidas(c: number, l: number, a: number): string {
  return `${c} × ${l} × ${a} cm`;
}

/** "hoje", "ontem", "há 3 dias" — como o app de referência mostra. */
export function quando(data: string | Date): string {
  const d = typeof data === 'string' ? new Date(data) : data;
  const dias = Math.floor((Date.now() - d.getTime()) / 86_400_000);
  if (dias <= 0) return 'hoje';
  if (dias === 1) return 'ontem';
  if (dias < 30) return `há ${dias} dias`;
  if (dias < 365) return `há ${Math.floor(dias / 30)} meses`;
  return `há ${Math.floor(dias / 365)} anos`;
}

export function dataCurta(data: string | Date): string {
  const d = typeof data === 'string' ? new Date(data) : data;
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
}

/** "faltam 3 dias pra testar" / "último dia pra testar". */
export function prazoDeTeste(dias: number | null): string | null {
  if (dias == null) return null;
  if (dias <= 0) return 'prazo de teste encerrado';
  if (dias === 1) return 'último dia pra testar';
  return `faltam ${dias} dias pra testar`;
}

/** Parcelamento sugerido, como aparece na página do produto. */
export function parcelamento(centavos: number, maxParcelas = 12): string | null {
  const minimoPorParcela = 500; // R$ 5,00
  const parcelas = Math.min(maxParcelas, Math.floor(centavos / minimoPorParcela));
  if (parcelas < 2) return null;
  return `até ${parcelas}x de ${reais(Math.floor(centavos / parcelas))}`;
}
