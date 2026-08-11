/**
 * Regras de negócio do Vendas Itinga.
 *
 * Todo valor monetário neste projeto é inteiro, em CENTAVOS. Nunca use float
 * para dinheiro: R$ 129,90 é `12990`.
 *
 * Os valores abaixo são os padrões acertados para o lançamento. Vários podem
 * ser sobrescritos por variável de ambiente (ver `src/ambiente.ts`) para que
 * uma mudança de política não exija novo deploy do app.
 */

/**
 * Comissão da plataforma sobre o valor do produto.
 *
 * É a mesma para todo pedido, com ou sem entregador nosso: o custo da entrega
 * está coberto pela tarifa fixa da faixa (ver `TABELA_DE_TARIFAS`).
 */
export const COMISSAO = 0.12;

/**
 * Valor mínimo de um anúncio, em centavos.
 * Abaixo disso a tarifa fixa comeria quase tudo do vendedor.
 */
export const VALOR_MINIMO_VENDA = 1_000; // R$ 10,00

/**
 * Tarifa fixa cobrada por venda, por faixa de preço do produto.
 *
 * `ateInclusive` é o teto da faixa em centavos; a última faixa é aberta
 * (`Infinity`) e vale para tudo a partir de R$ 200,00.
 *
 * A tarifa cobre o custo operacional do pedido: a entrega feita pelos nossos
 * entregadores, o processamento do pagamento e o suporte.
 */
export interface FaixaDeTarifa {
  ateInclusive: number;
  tarifa: number;
}

export const TABELA_DE_TARIFAS: readonly FaixaDeTarifa[] = [
  { ateInclusive: 2_499, tarifa: 250 }, // até R$ 24,99  -> R$ 2,50
  { ateInclusive: 4_999, tarifa: 450 }, // até R$ 49,99  -> R$ 4,50
  { ateInclusive: 9_999, tarifa: 650 }, // até R$ 99,99  -> R$ 6,50
  { ateInclusive: 19_999, tarifa: 850 }, // até R$ 199,99 -> R$ 8,50
  { ateInclusive: Number.POSITIVE_INFINITY, tarifa: 1_850 }, // R$ 200,00+ -> R$ 18,50
] as const;

/** Tarifa fixa aplicável ao valor de um produto, em centavos. */
export function tarifaFixa(valorProduto: number): number {
  const faixa = TABELA_DE_TARIFAS.find((f) => valorProduto <= f.ateInclusive);
  // a última faixa é aberta, então sempre há uma correspondência
  return faixa!.tarifa;
}

/** Peso máximo aceito por anúncio, em gramas (20 kg). */
export const PESO_MAXIMO_G = 20_000;

/** Maior dimensão aceita por anúncio, em centímetros (60 cm). */
export const DIMENSAO_MAXIMA_CM = 60;

/**
 * Prazo que o comprador tem para testar o produto e pedir devolução.
 * A contagem começa na DATA DE ENTREGA (não na data da compra).
 *
 * São 7 dias porque é o que manda o artigo 49 do Código de Defesa do
 * Consumidor: em compra feita fora do estabelecimento comercial (o nosso caso),
 * o consumidor pode desistir em até 7 dias contados do recebimento.
 */
export const DIAS_PARA_TESTAR = 7;

/**
 * Quanto o entregador recebe por corrida concluída, em centavos.
 *
 * Sai da parte da plataforma (da tarifa fixa), não de uma cobrança separada
 * ao comprador. O comprador paga só o preço do produto.
 */
export const PAGAMENTO_POR_ENTREGA = 500; // R$ 5,00

/**
 * Retenção de taxa no reembolso: DESLIGADA.
 *
 * O artigo 49 do Código de Defesa do Consumidor manda devolver "imediatamente"
 * e "monetariamente atualizados" TODOS os valores pagos durante o prazo de
 * arrependimento. Reter comissão ou tarifa dentro dos 7 dias contraria a lei,
 * então quem absorve esse custo é a plataforma.
 *
 * As chaves continuam existindo porque `calcularReembolso` aceita sobrescrever
 * caso a caso (devolução negociada fora do prazo legal, por exemplo). Não
 * ligue por padrão sem falar com um advogado.
 */
export const RETER_COMISSAO_NO_REEMBOLSO = false;
export const RETER_TARIFA_NO_REEMBOLSO = false;

/** Cidade atendida. Anúncios e entregas ficam restritos a ela. */
export const CIDADE = 'Itinga';
export const UF = 'MG';

export type ModalidadeEntrega =
  /** Entregador do Vendas Itinga faz a coleta e a entrega. */
  | 'ENTREGADOR_PROPRIO'
  /** Comprador e vendedor combinam a entrega/retirada entre si. */
  | 'COMBINADO_ENTRE_PARTES';
