/**
 * Negociação de preço: o comprador propõe, o vendedor responde.
 *
 * É a peça que falta para o app parecer um brechó de verdade em vez de uma
 * loja: em marketplace de usados quase ninguém compra pelo preço do anúncio
 * sem antes perguntar "aceita menos?".
 *
 * ESTE MÓDULO NÃO MEXE EM DINHEIRO. Ele só decide quem pode propor o quê e
 * qual valor fica valendo. Quando uma oferta é aceita, o valor combinado entra
 * no `calcularSplit` existente como se fosse o preço do anúncio — comissão,
 * tarifa e repasse continuam sendo calculados pelo mesmo código de sempre.
 *
 * Consequência importante e desejada: como a tarifa é por faixa de preço, uma
 * oferta aceita que derruba o valor para outra faixa também derruba a tarifa.
 * Ex.: anúncio de R$ 105,00 (tarifa R$ 8,50) fechado em R$ 95,00 passa para a
 * faixa de R$ 6,50. Isso não é caso especial, é a tabela funcionando.
 */

import { VALOR_MINIMO_VENDA } from './regras';

/** Quanto tempo uma proposta fica de pé esperando resposta. */
export const DIAS_PARA_RESPONDER_OFERTA = 3;

/**
 * Piso da proposta, como fração do preço pedido.
 *
 * Sem piso, o vendedor recebe "R$ 1,00 no seu sofá de R$ 550" e desiste do
 * app. 50% é folgado o bastante para negociar de verdade e apertado o bastante
 * para filtrar deboche.
 */
export const FRACAO_MINIMA_DA_OFERTA = 0.5;

export type EstadoOferta =
  /** Comprador propôs, esperando o vendedor. */
  | 'ABERTA'
  /** Vendedor devolveu com outro valor, esperando o comprador. */
  | 'CONTRAPROPOSTA'
  /** Valor combinado. Vale para o checkout. */
  | 'ACEITA'
  | 'RECUSADA'
  /** Passou do prazo sem resposta. */
  | 'EXPIRADA'
  /** Quem propôs desistiu antes da resposta. */
  | 'CANCELADA';

export type AtorDaOferta = 'COMPRADOR' | 'VENDEDOR';

/** Estados em que ainda se espera alguém responder. */
export function estaEmAberto(estado: EstadoOferta): boolean {
  return estado === 'ABERTA' || estado === 'CONTRAPROPOSTA';
}

/** De quem é a vez de responder. `null` quando a negociação acabou. */
export function deQuemEAVez(estado: EstadoOferta): AtorDaOferta | null {
  if (estado === 'ABERTA') return 'VENDEDOR';
  if (estado === 'CONTRAPROPOSTA') return 'COMPRADOR';
  return null;
}

export interface ResultadoValidacao {
  valido: boolean;
  /** Mensagem pronta para a tela. Vazia quando `valido`. */
  motivo: string;
}

const ok: ResultadoValidacao = { valido: true, motivo: '' };
const nao = (motivo: string): ResultadoValidacao => ({ valido: false, motivo });

function reais(centavos: number): string {
  return `R$ ${(centavos / 100).toFixed(2).replace('.', ',')}`;
}

/**
 * O valor mínimo aceitável para uma proposta neste anúncio.
 *
 * É o maior entre a fração do preço pedido e o mínimo de venda da plataforma:
 * metade de um anúncio de R$ 12,00 daria R$ 6,00, abaixo do mínimo de R$ 10,00
 * com que o pedido nem pode ser criado.
 */
export function valorMinimoDaOferta(precoAnunciado: number): number {
  return Math.max(
    Math.ceil(precoAnunciado * FRACAO_MINIMA_DA_OFERTA),
    VALOR_MINIMO_VENDA,
  );
}

/**
 * A proposta do comprador faz sentido?
 *
 * Recusa oferta acima do preço pedido de propósito: se a pessoa quer pagar o
 * valor cheio, o caminho é o botão de comprar, não uma negociação que ainda
 * depende de o vendedor responder.
 */
export function validarProposta(
  valorProposto: number,
  precoAnunciado: number,
): ResultadoValidacao {
  if (!Number.isInteger(valorProposto) || valorProposto <= 0) {
    return nao('Informe um valor válido.');
  }
  if (valorProposto >= precoAnunciado) {
    return nao(
      `Esse valor é igual ou maior que o preço pedido. Para levar por ${reais(precoAnunciado)}, é só comprar direto.`,
    );
  }

  const minimo = valorMinimoDaOferta(precoAnunciado);
  if (valorProposto < minimo) {
    return nao(
      `A menor oferta possível neste anúncio é ${reais(minimo)}. Proposta muito baixa costuma ser recusada na hora.`,
    );
  }

  return ok;
}

/**
 * A contraproposta do vendedor faz sentido?
 *
 * Tem que ficar ENTRE o que o comprador ofereceu e o preço do anúncio: abaixo
 * da oferta seria o vendedor pedindo menos do que já lhe ofereceram, e acima
 * do anúncio seria aumentar o preço no meio da conversa.
 */
export function validarContraproposta(
  valorContraproposto: number,
  valorOferecido: number,
  precoAnunciado: number,
): ResultadoValidacao {
  if (!Number.isInteger(valorContraproposto) || valorContraproposto <= 0) {
    return nao('Informe um valor válido.');
  }
  if (valorContraproposto <= valorOferecido) {
    return nao(
      `A oferta já é de ${reais(valorOferecido)}. Para fechar por esse valor, aceite a oferta.`,
    );
  }
  if (valorContraproposto >= precoAnunciado) {
    return nao(
      `A contraproposta precisa ser menor que o preço do anúncio (${reais(precoAnunciado)}).`,
    );
  }
  if (valorContraproposto < VALOR_MINIMO_VENDA) {
    return nao(`O valor mínimo de venda é ${reais(VALOR_MINIMO_VENDA)}.`);
  }
  return ok;
}

export type AcaoNaOferta = 'ACEITAR' | 'RECUSAR' | 'CONTRAPROPOR' | 'CANCELAR';

/**
 * Este ator pode fazer esta ação, neste estado?
 *
 * A regra é uma só: responde quem NÃO fez o último lance. Cancelar é o
 * contrário — só cancela quem está esperando resposta, porque é a proposta
 * dele que está de pé.
 */
export function podeAgir(
  estado: EstadoOferta,
  ator: AtorDaOferta,
  acao: AcaoNaOferta,
): boolean {
  if (!estaEmAberto(estado)) return false;

  const vez = deQuemEAVez(estado);

  switch (acao) {
    case 'CANCELAR':
      // quem propôs é quem não tem a vez
      return vez !== null && vez !== ator;

    case 'CONTRAPROPOR':
      // só o vendedor contrapropõe; o comprador aceita, recusa ou refaz a oferta
      return ator === 'VENDEDOR' && estado === 'ABERTA';

    case 'ACEITAR':
    case 'RECUSAR':
      return vez === ator;

    default:
      // Ação desconhecida é recusada, como na máquina de estados da entrega:
      // um app desatualizado não consegue inventar um movimento novo.
      return false;
  }
}

/** Prazo de resposta a partir de quando a proposta foi feita. */
export function prazoDeResposta(feitaEm: Date): Date {
  return new Date(feitaEm.getTime() + DIAS_PARA_RESPONDER_OFERTA * 86_400_000);
}

export function estaVencida(feitaEm: Date, agora: Date = new Date()): boolean {
  return agora.getTime() > prazoDeResposta(feitaEm).getTime();
}

/**
 * Qual valor vale agora nesta negociação — o que o checkout deve cobrar.
 *
 * Só devolve valor quando a oferta foi ACEITA. Em qualquer outro estado o
 * checkout tem que usar o preço do anúncio; devolver um valor "provável" aqui
 * seria o caminho mais curto para vender mais barato do que o combinado.
 */
export function valorParaCobrar(
  estado: EstadoOferta,
  valorAtual: number,
): number | null {
  return estado === 'ACEITA' ? valorAtual : null;
}
