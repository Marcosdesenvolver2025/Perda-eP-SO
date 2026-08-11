/**
 * Cálculo da receita da plataforma e da divisão do pagamento (split).
 *
 * O comprador paga UM valor: o preço do produto. Dele saem duas coisas para a
 * plataforma — a comissão de 12% e a tarifa fixa da faixa — e o resto é do
 * vendedor. Não há cobrança separada de frete: a entrega está coberta pela
 * tarifa fixa.
 *
 * Regra de ouro: a soma das partes tem que bater EXATAMENTE com o total pago
 * pelo comprador. A pagar.me rejeita a transação se o split não fechar, então
 * a sobra do arredondamento sempre vai para a plataforma.
 */

import {
  COMISSAO,
  VALOR_MINIMO_VENDA,
  tarifaFixa,
  type ModalidadeEntrega,
} from './regras';

export interface EntradaSplit {
  /** Preço do produto, em centavos. É o total pago pelo comprador. */
  valorProduto: number;
  modalidade: ModalidadeEntrega;
  /** Alíquota da comissão (0 a 1). Só informe para simular outra política. */
  taxaComissao?: number;
}

export interface ResultadoSplit {
  /** Preço do produto, em centavos (repetido para auditoria e estorno). */
  valorProduto: number;
  /** Quanto o comprador paga no total. Igual ao preço do produto. */
  total: number;
  /** Comissão percentual da plataforma. */
  comissao: number;
  /** Alíquota aplicada, guardada para auditoria. */
  taxaComissao: number;
  /** Tarifa fixa da faixa de preço. */
  tarifa: number;
  /** Comissão + tarifa: o que a plataforma cobra do vendedor nesta venda. */
  totalDescontado: number;
  /** Quanto o vendedor recebe. */
  valorVendedor: number;
  /** Quanto fica com a plataforma (comissão + tarifa + sobras). */
  valorPlataforma: number;
}

/**
 * Arredonda para centavo inteiro. A comissão sempre arredonda PARA BAIXO,
 * de forma que a diferença fique a favor do vendedor e nunca contra ele.
 */
function centavos(valor: number): number {
  return Math.floor(valor);
}

export function calcularSplit(entrada: EntradaSplit): ResultadoSplit {
  const { valorProduto } = entrada;
  const taxaComissao = entrada.taxaComissao ?? COMISSAO;

  if (!Number.isInteger(valorProduto) || valorProduto <= 0) {
    throw new Error('valorProduto precisa ser um inteiro positivo em centavos');
  }
  if (valorProduto < VALOR_MINIMO_VENDA) {
    throw new Error(
      `o valor mínimo de venda é R$ ${(VALOR_MINIMO_VENDA / 100).toFixed(2)}`,
    );
  }

  const comissao = centavos(valorProduto * taxaComissao);
  const tarifa = tarifaFixa(valorProduto);
  const totalDescontado = comissao + tarifa;

  if (totalDescontado >= valorProduto) {
    // não pode acontecer com a tabela atual e o mínimo de R$ 10,00, mas se
    // alguém mexer nos números sem refazer as contas, é melhor estourar aqui
    // do que deixar o vendedor receber zero ou negativo.
    throw new Error(
      `comissão + tarifa (${totalDescontado}) não pode ser maior ou igual ao valor do produto (${valorProduto})`,
    );
  }

  const valorVendedor = valorProduto - totalDescontado;
  const total = valorProduto;
  // a plataforma recebe o que sobra: fecha o split no centavo.
  const valorPlataforma = total - valorVendedor;

  return {
    valorProduto,
    total,
    comissao,
    taxaComissao,
    tarifa,
    totalDescontado,
    valorVendedor,
    valorPlataforma,
  };
}

export interface RegraSplitPagarme {
  amount: number;
  recipient_id: string;
  type: 'flat';
  options: {
    charge_processing_fee: boolean;
    charge_remainder_fee: boolean;
    liable: boolean;
  };
}

export interface RecebedoresDoPedido {
  /** Recebedor da plataforma (a sua conta principal na pagar.me). */
  plataforma: string;
  vendedor: string;
}

/**
 * Converte o resultado do split no formato de `split rules` da pagar.me (API v5).
 *
 * Quem responde pelas taxas e pelos estornos:
 *  - a PLATAFORMA paga a taxa de processamento e a sobra de arredondamento;
 *  - a PLATAFORMA e o VENDEDOR são `liable`, ou seja, respondem por chargeback
 *    na proporção do que receberam.
 *
 * O entregador NÃO entra no split da cobrança: ele é pago pela plataforma
 * quando conclui a corrida (ver `servicos/repasse.ts#pagarEntregador`).
 */
export function montarRegrasSplit(
  split: ResultadoSplit,
  recebedores: RecebedoresDoPedido,
): RegraSplitPagarme[] {
  const regras: RegraSplitPagarme[] = [
    {
      amount: split.valorPlataforma,
      recipient_id: recebedores.plataforma,
      type: 'flat',
      options: {
        charge_processing_fee: true,
        charge_remainder_fee: true,
        liable: true,
      },
    },
    {
      amount: split.valorVendedor,
      recipient_id: recebedores.vendedor,
      type: 'flat',
      options: {
        charge_processing_fee: false,
        charge_remainder_fee: false,
        liable: true,
      },
    },
  ];

  const soma = regras.reduce((acc, r) => acc + r.amount, 0);
  if (soma !== split.total) {
    throw new Error(
      `split não fecha: soma ${soma} != total ${split.total}. A pagar.me recusaria a transação.`,
    );
  }
  return regras;
}
