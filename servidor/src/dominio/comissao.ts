/**
 * Cálculo da comissão e da divisão do pagamento (split) entre
 * plataforma, vendedor e entregador.
 *
 * Regra de ouro: a soma das partes tem que bater EXATAMENTE com o total pago
 * pelo comprador. A pagar.me rejeita a transação se o split não fechar, então
 * a sobra do arredondamento sempre vai para a plataforma.
 */

import {
  REPASSE_ENTREGADOR,
  taxaDeComissao,
  type ModalidadeEntrega,
} from './regras';

export interface EntradaSplit {
  /** Preço do produto, em centavos. */
  valorProduto: number;
  /** Frete cobrado do comprador, em centavos. Zero quando não há entregador. */
  valorFrete: number;
  modalidade: ModalidadeEntrega;
  /** Percentual do frete repassado ao entregador (0 a 1). */
  repasseEntregador?: number;
}

export interface ResultadoSplit {
  /** Preço do produto, em centavos (repetido para auditoria e estorno). */
  valorProduto: number;
  /** Frete cobrado do comprador, em centavos. */
  valorFrete: number;
  /** Quanto o comprador paga no total (produto + frete). */
  total: number;
  /** Comissão da plataforma sobre o produto. */
  comissao: number;
  /** Alíquota aplicada (0.16 ou 0.18), guardada para auditoria. */
  taxaComissao: number;
  /** Quanto o vendedor recebe. */
  valorVendedor: number;
  /** Quanto o entregador recebe (0 quando não há entregador). */
  valorEntregador: number;
  /** Quanto fica com a plataforma (comissão + margem do frete + sobras). */
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
  const { valorProduto, modalidade } = entrada;
  const valorFrete = entrada.valorFrete ?? 0;
  const repasse = entrada.repasseEntregador ?? REPASSE_ENTREGADOR;

  if (!Number.isInteger(valorProduto) || valorProduto <= 0) {
    throw new Error('valorProduto precisa ser um inteiro positivo em centavos');
  }
  if (!Number.isInteger(valorFrete) || valorFrete < 0) {
    throw new Error('valorFrete precisa ser um inteiro >= 0 em centavos');
  }
  if (modalidade !== 'ENTREGADOR_PROPRIO' && valorFrete > 0) {
    throw new Error('só há frete quando a entrega é feita pelo nosso entregador');
  }

  const taxaComissao = taxaDeComissao(modalidade);
  const comissao = centavos(valorProduto * taxaComissao);
  const valorVendedor = valorProduto - comissao;

  const valorEntregador =
    modalidade === 'ENTREGADOR_PROPRIO' ? centavos(valorFrete * repasse) : 0;

  const total = valorProduto + valorFrete;
  // a plataforma recebe o que sobra: fecha o split no centavo.
  const valorPlataforma = total - valorVendedor - valorEntregador;

  return {
    valorProduto,
    valorFrete,
    total,
    comissao,
    taxaComissao,
    valorVendedor,
    valorEntregador,
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
  entregador?: string | null;
}

/**
 * Converte o resultado do split no formato de `split rules` da pagar.me (API v5).
 *
 * Quem responde pelas taxas e pelos estornos:
 *  - a PLATAFORMA paga a taxa de processamento e a sobra de arredondamento;
 *  - a PLATAFORMA e o VENDEDOR são `liable`, ou seja, respondem por chargeback
 *    na proporção do que receberam. O entregador nunca é `liable`: ele prestou
 *    o serviço e não pode ser penalizado por um problema do produto.
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

  if (split.valorEntregador > 0) {
    if (!recebedores.entregador) {
      throw new Error(
        'pedido com entregador precisa do recipient_id do entregador',
      );
    }
    regras.push({
      amount: split.valorEntregador,
      recipient_id: recebedores.entregador,
      type: 'flat',
      options: {
        charge_processing_fee: false,
        charge_remainder_fee: false,
        liable: false,
      },
    });
  }

  const soma = regras.reduce((acc, r) => acc + r.amount, 0);
  if (soma !== split.total) {
    throw new Error(
      `split não fecha: soma ${soma} != total ${split.total}. A pagar.me recusaria a transação.`,
    );
  }
  return regras;
}
