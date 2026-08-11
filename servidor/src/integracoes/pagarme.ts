/**
 * Cliente da API v5 da pagar.me.
 *
 * Cobre o que o Vendas Itinga precisa:
 *  - criar recebedores (vendedores e entregadores) para receber o split;
 *  - criar pedidos com split (cartão e Pix);
 *  - estornar (total ou parcial) na devolução;
 *  - transferir o saldo retido ao vendedor quando a janela de 7 dias vence.
 *
 * Autenticação: Basic com a chave secreta como usuário e senha vazia.
 *
 * ATENÇÃO antes de ir para produção: rode todo este fluxo na chave `sk_test_`
 * e confira os campos com a documentação vigente em https://docs.pagar.me/ —
 * a pagar.me evolui o payload da v5, principalmente em `split` e em
 * `transfer_settings`.
 */

import { ambiente } from '../ambiente';
import { ErroDeIntegracao } from '../erros';
import type { RegraSplitPagarme } from '../dominio/comissao';

const AUTORIZACAO =
  'Basic ' + Buffer.from(`${ambiente.PAGARME_SECRET_KEY}:`).toString('base64');

interface OpcoesRequisicao {
  metodo: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  caminho: string;
  corpo?: unknown;
  /** Evita cobrança duplicada se a mesma requisição for repetida. */
  chaveIdempotencia?: string;
}

async function requisitar<T>({
  metodo,
  caminho,
  corpo,
  chaveIdempotencia,
}: OpcoesRequisicao): Promise<T> {
  const cabecalhos: Record<string, string> = {
    Authorization: AUTORIZACAO,
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  if (chaveIdempotencia) {
    cabecalhos['Idempotency-Key'] = chaveIdempotencia;
  }

  const resposta = await fetch(`${ambiente.PAGARME_URL_BASE}${caminho}`, {
    method: metodo,
    headers: cabecalhos,
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  });

  const texto = await resposta.text();
  const dados = texto ? JSON.parse(texto) : {};

  if (!resposta.ok) {
    throw new ErroDeIntegracao('pagar.me', resposta.status, dados);
  }
  return dados as T;
}

// ---------------------------------------------------------------------------
// Recebedores (vendedores e entregadores)
// ---------------------------------------------------------------------------

export interface DadosBancarios {
  bancoCodigo: string;
  agencia: string;
  agenciaDigito?: string;
  conta: string;
  contaDigito: string;
  tipoConta: 'checking' | 'savings';
  titular: string;
  documentoTitular: string;
}

export interface NovoRecebedor {
  nome: string;
  email: string;
  documento: string; // CPF ou CNPJ, só dígitos
  tipo: 'individual' | 'company';
  telefone?: { ddd: string; numero: string };
  banco: DadosBancarios;
}

export interface RespostaRecebedor {
  id: string;
  status: string;
  [chave: string]: unknown;
}

/**
 * Cria um recebedor com transferência automática DESLIGADA.
 *
 * É de propósito: o dinheiro do split fica parado no saldo do vendedor até a
 * janela de 7 dias vencer. Se o comprador pedir reembolso, o valor ainda está
 * lá para ser estornado. O repasse sai depois, via `transferir()`.
 */
export function criarRecebedor(
  dados: NovoRecebedor,
  chaveIdempotencia?: string,
): Promise<RespostaRecebedor> {
  return requisitar<RespostaRecebedor>({
    metodo: 'POST',
    caminho: '/recipients',
    chaveIdempotencia,
    corpo: {
      name: dados.nome,
      email: dados.email,
      document: dados.documento,
      type: dados.tipo,
      code: dados.documento,
      ...(dados.telefone
        ? {
            phone_numbers: [
              {
                ddd: dados.telefone.ddd,
                number: dados.telefone.numero,
                type: 'mobile',
              },
            ],
          }
        : {}),
      default_bank_account: {
        holder_name: dados.banco.titular,
        holder_type: dados.tipo,
        holder_document: dados.banco.documentoTitular,
        bank: dados.banco.bancoCodigo,
        branch_number: dados.banco.agencia,
        branch_check_digit: dados.banco.agenciaDigito,
        account_number: dados.banco.conta,
        account_check_digit: dados.banco.contaDigito,
        type: dados.banco.tipoConta,
      },
      transfer_settings: {
        transfer_enabled: false, // repasse manual, depois da janela de teste
        transfer_interval: 'Daily',
        transfer_day: 0,
      },
    },
  });
}

export function consultarRecebedor(recipientId: string): Promise<RespostaRecebedor> {
  return requisitar<RespostaRecebedor>({
    metodo: 'GET',
    caminho: `/recipients/${recipientId}`,
  });
}

export function saldoDoRecebedor(
  recipientId: string,
): Promise<{ available_amount: number; waiting_funds_amount: number }> {
  return requisitar({
    metodo: 'GET',
    caminho: `/recipients/${recipientId}/balance`,
  });
}

// ---------------------------------------------------------------------------
// Pedidos e cobranças
// ---------------------------------------------------------------------------

export interface ClientePagarme {
  nome: string;
  email: string;
  documento: string;
  tipo: 'individual' | 'company';
  telefone?: { ddd: string; numero: string };
}

export interface ItemPedido {
  descricao: string;
  valorUnitario: number; // centavos
  quantidade: number;
  codigo?: string;
}

export type Pagamento =
  | {
      tipo: 'credit_card';
      /** Token do cartão gerado no app (a chave pública nunca sai do celular). */
      tokenCartao: string;
      parcelas: number;
    }
  | {
      tipo: 'pix';
      /** Tempo de vida do QR Code, em segundos. */
      expiraEm: number;
    };

export interface RespostaPedidoPagarme {
  id: string;
  status: string;
  charges: Array<{
    id: string;
    status: string;
    last_transaction?: {
      qr_code?: string;
      qr_code_url?: string;
      expires_at?: string;
      [chave: string]: unknown;
    };
    [chave: string]: unknown;
  }>;
  [chave: string]: unknown;
}

export interface NovoPedido {
  /** Código do pedido no nosso sistema, para conciliar depois. */
  codigoExterno: string;
  cliente: ClientePagarme;
  itens: ItemPedido[];
  pagamento: Pagamento;
  split: RegraSplitPagarme[];
  metadados?: Record<string, string>;
}

export function criarPedido(
  dados: NovoPedido,
  chaveIdempotencia?: string,
): Promise<RespostaPedidoPagarme> {
  const pagamento =
    dados.pagamento.tipo === 'credit_card'
      ? {
          payment_method: 'credit_card',
          credit_card: {
            installments: dados.pagamento.parcelas,
            statement_descriptor: 'VENDASITINGA',
            card_token: dados.pagamento.tokenCartao,
          },
          split: dados.split,
        }
      : {
          payment_method: 'pix',
          pix: { expires_in: dados.pagamento.expiraEm },
          split: dados.split,
        };

  return requisitar<RespostaPedidoPagarme>({
    metodo: 'POST',
    caminho: '/orders',
    chaveIdempotencia: chaveIdempotencia ?? dados.codigoExterno,
    corpo: {
      code: dados.codigoExterno,
      customer: {
        name: dados.cliente.nome,
        email: dados.cliente.email,
        document: dados.cliente.documento,
        type: dados.cliente.tipo,
        ...(dados.cliente.telefone
          ? {
              phones: {
                mobile_phone: {
                  country_code: '55',
                  area_code: dados.cliente.telefone.ddd,
                  number: dados.cliente.telefone.numero,
                },
              },
            }
          : {}),
      },
      items: dados.itens.map((item) => ({
        amount: item.valorUnitario,
        description: item.descricao,
        quantity: item.quantidade,
        code: item.codigo,
      })),
      payments: [pagamento],
      metadata: dados.metadados,
      closed: true,
    },
  });
}

export function consultarPedido(orderId: string): Promise<RespostaPedidoPagarme> {
  return requisitar<RespostaPedidoPagarme>({
    metodo: 'GET',
    caminho: `/orders/${orderId}`,
  });
}

// ---------------------------------------------------------------------------
// Estorno
// ---------------------------------------------------------------------------

export interface EstornoParcial {
  chargeId: string;
  /** Quanto devolver ao comprador, em centavos. */
  valor: number;
  /**
   * Como o estorno é dividido entre os recebedores. Passar isso explicitamente
   * é o que garante que a comissão e o frete fiquem retidos e que o entregador
   * não seja debitado.
   */
  split?: RegraSplitPagarme[];
}

export function estornar(
  { chargeId, valor, split }: EstornoParcial,
  chaveIdempotencia?: string,
): Promise<{ id: string; status: string }> {
  return requisitar({
    metodo: 'DELETE',
    caminho: `/charges/${chargeId}`,
    chaveIdempotencia: chaveIdempotencia ?? `estorno-${chargeId}-${valor}`,
    corpo: {
      amount: valor,
      ...(split ? { split_rules: split } : {}),
    },
  });
}

// ---------------------------------------------------------------------------
// Repasse (saque do saldo retido para a conta do vendedor/entregador)
// ---------------------------------------------------------------------------

export function transferir(
  recipientId: string,
  valor: number,
  chaveIdempotencia?: string,
): Promise<{ id: string; status: string; amount: number }> {
  return requisitar({
    metodo: 'POST',
    caminho: `/recipients/${recipientId}/withdrawals`,
    chaveIdempotencia,
    corpo: { amount: valor },
  });
}

/** Confere a autenticação básica que a pagar.me envia nos webhooks. */
export function webhookAutenticado(cabecalhoAutorizacao?: string): boolean {
  const usuario = ambiente.PAGARME_WEBHOOK_USUARIO;
  const senha = ambiente.PAGARME_WEBHOOK_SENHA;

  // sem credencial configurada não dá para validar: só aceite assim em
  // desenvolvimento, nunca em produção.
  if (!usuario || !senha) return ambiente.NODE_ENV !== 'production';
  if (!cabecalhoAutorizacao?.startsWith('Basic ')) return false;

  const esperado = Buffer.from(`${usuario}:${senha}`).toString('base64');
  const recebido = cabecalhoAutorizacao.slice('Basic '.length);

  // comparação de tempo constante
  if (recebido.length !== esperado.length) return false;
  let diferenca = 0;
  for (let i = 0; i < esperado.length; i++) {
    diferenca |= esperado.charCodeAt(i) ^ recebido.charCodeAt(i);
  }
  return diferenca === 0;
}
