/**
 * Regras de negócio do Vendas Itinga.
 *
 * Todo valor monetário neste projeto é inteiro, em CENTAVOS. Nunca use float
 * para dinheiro: R$ 129,90 é `12990`.
 */

/**
 * Quem faz a entrega. A escolha é do vendedor, no anúncio, e fica GRAVADA NO
 * PEDIDO — se a política mudar depois, o pedido antigo mantém a regra com que
 * foi vendido.
 */
export type ModalidadeEntrega =
  /** Entregador da plataforma coleta e entrega. Cobra 12% + tarifa da faixa. */
  | 'PLATAFORMA'
  /** O próprio vendedor entrega. Cobra só os 12%, sem tarifa. */
  | 'VENDEDOR';

/** Comissão da plataforma sobre o valor do produto. Vale nas duas modalidades. */
export const COMISSAO = 0.12;

/**
 * Valor mínimo de um anúncio, em centavos.
 * Abaixo disso a tarifa fixa comeria quase tudo do vendedor.
 */
export const VALOR_MINIMO_VENDA = 1_000; // R$ 10,00

/**
 * Tarifa fixa por venda, por faixa de preço do produto.
 * **Só é cobrada na modalidade PLATAFORMA** — ela paga a operação da entrega.
 *
 * `ateInclusive` é o teto da faixa em centavos; a última faixa é aberta.
 */
export interface FaixaDeTarifa {
  ateInclusive: number;
  tarifa: number;
}

export const TABELA_DE_TARIFAS: readonly FaixaDeTarifa[] = [
  { ateInclusive: 2_499, tarifa: 250 }, //  até R$ 24,99  -> R$ 2,50
  { ateInclusive: 4_999, tarifa: 450 }, //  até R$ 49,99  -> R$ 4,50
  { ateInclusive: 9_999, tarifa: 650 }, //  até R$ 99,99  -> R$ 6,50
  { ateInclusive: 19_999, tarifa: 850 }, // até R$ 199,99 -> R$ 8,50
  { ateInclusive: 49_999, tarifa: 1_050 }, // até R$ 499,99 -> R$ 10,50
  { ateInclusive: Number.POSITIVE_INFINITY, tarifa: 1_450 }, // R$ 500,00+ -> R$ 14,50
] as const;

/**
 * Tarifa fixa aplicável, em centavos.
 * Na modalidade VENDEDOR não há tarifa: a plataforma não entrega nada.
 */
export function tarifaFixa(
  valorProduto: number,
  modalidade: ModalidadeEntrega,
): number {
  if (modalidade === 'VENDEDOR') return 0;
  return TABELA_DE_TARIFAS.find((f) => valorProduto <= f.ateInclusive)!.tarifa;
}

// ---------------------------------------------------------------------------
// Limites do pacote — valem SÓ para a modalidade PLATAFORMA
// ---------------------------------------------------------------------------

/** Peso máximo que os nossos entregadores levam, em gramas. */
export const PESO_MAXIMO_G = 20_000; // 20 kg

/** Largura máxima do pacote, em centímetros. */
export const LARGURA_MAXIMA_CM = 100;

/** Altura máxima do pacote, em centímetros. */
export const ALTURA_MAXIMA_CM = 100;

/**
 * Comprimento máximo, em centímetros.
 *
 * A regra combinada limita só peso, largura e altura. Deixamos o comprimento
 * livre por padrão para não inventar restrição, mas o campo existe: para
 * limitar (ex.: barrar um cano de 3 m numa moto), troque por um número.
 */
export const COMPRIMENTO_MAXIMO_CM = Number.POSITIVE_INFINITY;

// ---------------------------------------------------------------------------
// Prazos
// ---------------------------------------------------------------------------

/**
 * Prazo que o comprador tem para testar o produto e pedir devolução.
 * Conta a partir da DATA DE ENTREGA, nas duas modalidades.
 *
 * São 7 dias porque é o que manda o artigo 49 do Código de Defesa do
 * Consumidor para compra feita fora do estabelecimento comercial.
 */
export const DIAS_PARA_TESTAR = 7;

/**
 * Modalidade VENDEDOR: prazo para o comprador confirmar o recebimento depois
 * que o vendedor declara a entrega sem o código.
 *
 * Passado o prazo sem confirmação e sem devolução aberta, o sistema confirma
 * sozinho. É o que impede o pedido de travar quando o comprador some.
 */
export const DIAS_PARA_CONFIRMACAO_AUTOMATICA = 3;

/**
 * Quanto o entregador recebe por corrida concluída, em centavos.
 * Sai da tarifa fixa, não de uma cobrança separada ao comprador.
 */
export const PAGAMENTO_POR_ENTREGA = 500; // R$ 5,00

/**
 * Retenção de taxa no reembolso: DESLIGADA.
 *
 * O artigo 49 do CDC manda devolver todos os valores pagos durante o prazo de
 * arrependimento. Quem absorve esse custo é a plataforma.
 *
 * As chaves seguem disponíveis para devolução negociada FORA do prazo legal.
 * Não ligue por padrão sem falar com um advogado.
 */
export const RETER_COMISSAO_NO_REEMBOLSO = false;
export const RETER_TARIFA_NO_REEMBOLSO = false;

/** Cidade atendida. Anúncios e entregas ficam restritos a ela. */
export const CIDADE = 'Itinga';
export const UF = 'MG';
