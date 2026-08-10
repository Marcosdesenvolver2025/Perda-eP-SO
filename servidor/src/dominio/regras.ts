/**
 * Regras de negócio do Vendas Itinga.
 *
 * Todo valor monetário neste projeto é inteiro, em CENTAVOS. Nunca use float
 * para dinheiro: R$ 129,90 é `12990`.
 *
 * Os valores abaixo são os padrões acertados para o lançamento. Cada um pode
 * ser sobrescrito por variável de ambiente (ver `src/ambiente.ts`) para que
 * uma mudança de política não exija novo deploy do app.
 */

/** Comissão da plataforma quando o vendedor entrega por conta própria. */
export const COMISSAO_SEM_ENTREGADOR = 0.16;

/** Comissão da plataforma quando a entrega é feita pelos nossos entregadores. */
export const COMISSAO_COM_ENTREGADOR = 0.18;

/** Peso máximo aceito por anúncio, em gramas (20 kg). */
export const PESO_MAXIMO_G = 20_000;

/** Maior dimensão aceita por anúncio, em centímetros (60 cm). */
export const DIMENSAO_MAXIMA_CM = 60;

/**
 * Prazo que o comprador tem para testar o produto e pedir reembolso.
 * A contagem começa na DATA DE ENTREGA (não na data da compra).
 *
 * São 7 dias porque é o que manda o artigo 49 do Código de Defesa do
 * Consumidor: em compra feita fora do estabelecimento comercial (o nosso caso),
 * o consumidor pode desistir em até 7 dias contados do recebimento.
 */
export const DIAS_PARA_TESTAR = 7;

/**
 * Percentual do frete que vai para o entregador. O restante fica com a
 * plataforma (cobre seguro, suporte e operação da entrega).
 */
export const REPASSE_ENTREGADOR = 0.8;

/**
 * Retenção de taxa no reembolso: DESLIGADA.
 *
 * O artigo 49 do Código de Defesa do Consumidor manda devolver "imediatamente"
 * e "monetariamente atualizados" TODOS os valores pagos durante o prazo de
 * arrependimento — e o parágrafo único inclui o frete. Reter comissão ou frete
 * dentro dos 7 dias contraria a lei, então quem absorve esse custo é a
 * plataforma.
 *
 * As chaves continuam existindo porque `calcularReembolso` aceita sobrescrever
 * caso a caso (devolução fora do prazo legal, por exemplo, negociada com o
 * comprador). Não ligue por padrão sem falar com um advogado.
 */
export const RETER_COMISSAO_NO_REEMBOLSO = false;
export const RETER_FRETE_NO_REEMBOLSO = false;

/** Cidade atendida. Anúncios e entregas ficam restritos a ela. */
export const CIDADE = 'Itinga';
export const UF = 'MG';

export type ModalidadeEntrega =
  /** Entregador do Vendas Itinga faz a coleta e a entrega. */
  | 'ENTREGADOR_PROPRIO'
  /** Comprador e vendedor combinam a entrega/retirada entre si. */
  | 'COMBINADO_ENTRE_PARTES';

/** A comissão aplicada depende de quem faz a entrega. */
export function taxaDeComissao(modalidade: ModalidadeEntrega): number {
  return modalidade === 'ENTREGADOR_PROPRIO'
    ? COMISSAO_COM_ENTREGADOR
    : COMISSAO_SEM_ENTREGADOR;
}
