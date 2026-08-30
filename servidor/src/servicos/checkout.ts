/**
 * Compra: monta o split, cobra pela pagar.me e cria o pedido.
 *
 * O comprador paga UM valor — o preço do produto. Dele saem a comissão de 12%
 * e a tarifa fixa da faixa, e o resto é do vendedor. Não há cobrança separada
 * de frete: a entrega está coberta pela tarifa.
 *
 * O dinheiro NÃO cai na mão do vendedor aqui. O split já distribui os valores,
 * mas os recebedores são criados com transferência automática desligada, então
 * o valor fica retido no saldo até a janela de 7 dias vencer
 * (ver `servicos/repasse.ts`).
 */

import { randomBytes } from 'node:crypto';

import { ambiente } from '../ambiente';
import { calcularSplit, montarRegrasSplit } from '../dominio/comissao';
import * as ofertasServico from './ofertas';
import { validarMedidas } from '../dominio/frete';
import type { ModalidadeEntrega } from '../dominio/regras';
import { conflito, erroDeValidacao, naoEncontrado } from '../erros';
import * as pagarme from '../integracoes/pagarme';
import { log } from '../log';
import { prisma } from '../prisma';
import { abrirEntregaDoVendedor } from './entregaDoVendedor';
import { abrirEntregaDoPedido } from './logistica';

/** Código curto e legível do pedido, ex.: VI-7K3QM2. */
function gerarCodigo(): string {
  const alfabeto = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // sem O/0 e I/1
  const bytes = randomBytes(6);
  let saida = '';
  for (const b of bytes) saida += alfabeto[b % alfabeto.length];
  return `VI-${saida}`;
}

export interface PedidoDeCompra {
  compradorId: string;
  anuncioId: string;
  enderecoId?: string;
  /** Oferta aceita que define o preço. Sem ela, vale o preço do anúncio. */
  ofertaId?: string;
  pagamento:
    | { tipo: 'credit_card'; tokenCartao: string; parcelas: number }
    | { tipo: 'pix' };
}

export interface ResumoDaCompra {
  valorProduto: number;
  valorTotal: number;
  taxaComissao: number;
  valorComissao: number;
  valorTarifa: number;
  /** O que o vendedor recebe depois da janela de teste. */
  valorVendedor: number;
  modalidade: ModalidadeEntrega;
}

/**
 * Simula a compra sem cobrar nada. O app chama isso na tela do produto para
 * mostrar os valores antes de o comprador decidir.
 */
export async function simular(anuncioId: string): Promise<ResumoDaCompra> {
  const anuncio = await prisma.anuncio.findUnique({ where: { id: anuncioId } });
  if (!anuncio || anuncio.estado !== 'ATIVO') {
    throw naoEncontrado('Este anúncio não está mais disponível.');
  }

  // quem escolhe a modalidade é o vendedor, no anúncio
  const modalidade = anuncio.modalidadeEntrega as ModalidadeEntrega;
  const split = calcularSplit({
    valorProduto: anuncio.preco,
    modalidade,
    taxaComissao: ambiente.COMISSAO,
  });

  return {
    valorProduto: split.valorProduto,
    valorTotal: split.total,
    taxaComissao: split.taxaComissao,
    valorComissao: split.comissao,
    valorTarifa: split.tarifa,
    valorVendedor: split.valorVendedor,
    modalidade,
  };
}

export async function comprar(entrada: PedidoDeCompra) {
  const [anuncio, comprador] = await Promise.all([
    prisma.anuncio.findUnique({
      where: { id: entrada.anuncioId },
      include: { vendedor: { include: { recebedor: true } } },
    }),
    prisma.usuario.findUnique({ where: { id: entrada.compradorId } }),
  ]);

  if (!anuncio || anuncio.estado !== 'ATIVO') {
    throw naoEncontrado('Este anúncio não está mais disponível.');
  }
  if (!comprador) throw naoEncontrado('Conta não encontrada.');
  if (anuncio.vendedorId === entrada.compradorId) {
    throw conflito('Você não pode comprar o seu próprio anúncio.');
  }
  if (!comprador.cpf) {
    throw erroDeValidacao('Informe seu CPF antes de comprar.');
  }

  const recebedorVendedor = anuncio.vendedor.recebedor;
  if (!recebedorVendedor || recebedorVendedor.estado !== 'ATIVO') {
    throw conflito(
      'O vendedor ainda não terminou o cadastro para receber pagamentos.',
    );
  }

  // a modalidade vem do anúncio e é congelada no pedido logo abaixo
  const modalidade = anuncio.modalidadeEntrega as ModalidadeEntrega;
  const pelaPlataforma = modalidade === 'PLATAFORMA';

  const validacao = validarMedidas(
    {
      pesoG: anuncio.pesoG,
      comprimentoCm: anuncio.comprimentoCm,
      larguraCm: anuncio.larguraCm,
      alturaCm: anuncio.alturaCm,
    },
    modalidade,
  );
  if (!validacao.valido) {
    throw erroDeValidacao(validacao.erros.join(' '), validacao.erros);
  }

  if (pelaPlataforma && !entrada.enderecoId) {
    throw erroDeValidacao('Escolha o endereço de entrega.');
  }
  if (pelaPlataforma && !anuncio.enderecoColetaId) {
    throw conflito('O vendedor ainda não informou o endereço de coleta deste anúncio.');
  }

  /**
   * Preço a cobrar. É o do anúncio, salvo quando existe uma oferta ACEITA
   * desta pessoa para este anúncio — aí vale o valor combinado.
   *
   * `valorCombinado` devolve null em qualquer caso duvidoso (oferta de outra
   * pessoa, de outro anúncio, já usada em outra compra, ou não aceita), e o
   * preço do anúncio é o padrão seguro: o erro caro aqui seria cobrar menos
   * do que o combinado.
   *
   * Daqui para baixo nada muda: o valor entra no `calcularSplit` de sempre,
   * e comissão, tarifa e repasse saem do mesmo cálculo. Como a tarifa é por
   * faixa, uma oferta que derruba o valor para outra faixa derruba a tarifa
   * junto — é a tabela funcionando.
   */
  const valorDaOferta = entrada.ofertaId
    ? await ofertasServico.valorCombinado(
        entrada.ofertaId,
        entrada.compradorId,
        anuncio.id,
      )
    : null;

  const split = calcularSplit({
    valorProduto: valorDaOferta ?? anuncio.preco,
    modalidade,
    taxaComissao: ambiente.COMISSAO,
  });

  const regras = montarRegrasSplit(split, {
    plataforma: ambiente.PAGARME_RECEBEDOR_PLATAFORMA,
    vendedor: recebedorVendedor.recipientId,
  });

  const codigo = gerarCodigo();

  const pedido = await prisma.pedido.create({
    data: {
      codigo,
      anuncioId: anuncio.id,
      compradorId: comprador.id,
      vendedorId: anuncio.vendedorId,
      enderecoId: entrada.enderecoId ?? null,
      // congelado: se a política mudar, este pedido mantém a regra da venda
      modalidade,
      estado: 'AGUARDANDO_PAGAMENTO',
      ofertaId: valorDaOferta != null ? entrada.ofertaId : null,
      valorProduto: split.valorProduto,
      valorTotal: split.total,
      taxaComissao: split.taxaComissao,
      valorComissao: split.comissao,
      valorTarifa: split.tarifa,
      valorVendedor: split.valorVendedor,
      valorPlataforma: split.valorPlataforma,
      // custo da corrida, pago pela plataforma; não entra no split da cobrança
      custoEntregador: pelaPlataforma ? ambiente.PAGAMENTO_POR_ENTREGA : 0,
      metodoPagamento: entrada.pagamento.tipo,
      parcelas: entrada.pagamento.tipo === 'credit_card' ? entrada.pagamento.parcelas : 1,
    },
  });

  let respostaPagarme: pagarme.RespostaPedidoPagarme;
  try {
    respostaPagarme = await pagarme.criarPedido(
      {
        codigoExterno: codigo,
        cliente: {
          nome: comprador.nome,
          email: comprador.email,
          documento: comprador.cpf.replace(/\D/g, ''),
          tipo: 'individual',
        },
        itens: [
          {
            descricao: anuncio.titulo.slice(0, 255),
            valorUnitario: split.valorProduto,
            quantidade: 1,
            codigo: anuncio.id,
          },
        ],
        pagamento:
          entrada.pagamento.tipo === 'credit_card'
            ? {
                tipo: 'credit_card',
                tokenCartao: entrada.pagamento.tokenCartao,
                parcelas: entrada.pagamento.parcelas,
              }
            : { tipo: 'pix', expiraEm: 3600 },
        split: regras,
        metadados: { pedido: codigo, cidade: ambiente.CIDADE },
      },
      codigo, // idempotência: repetir a compra não cobra duas vezes
    );
  } catch (erro) {
    await prisma.pedido.update({
      where: { id: pedido.id },
      data: { estado: 'CANCELADO', canceladoEm: new Date() },
    });
    throw erro;
  }

  const cobranca = respostaPagarme.charges?.[0];
  const pago = cobranca?.status === 'paid';

  const atualizado = await prisma.pedido.update({
    where: { id: pedido.id },
    data: {
      pagarmeOrderId: respostaPagarme.id,
      pagarmeChargeId: cobranca?.id ?? null,
      ...(pago ? { estado: 'PAGO', pagoEm: new Date() } : {}),
    },
  });

  await prisma.eventoPedido.create({
    data: {
      pedidoId: pedido.id,
      tipo: 'checkout',
      autorId: comprador.id,
      detalhe: {
        pagarmeOrderId: respostaPagarme.id,
        status: respostaPagarme.status,
        split: regras.map((r) => ({ recebedor: r.recipient_id, valor: r.amount })),
      },
    },
  });

  if (pago) {
    await marcarComoPago(pedido.id);
  }

  log.info({ pedido: codigo, status: respostaPagarme.status }, 'pedido criado');

  return {
    pedido: atualizado,
    // no Pix o app precisa do QR Code para exibir
    pix: cobranca?.last_transaction?.qr_code
      ? {
          qrCode: cobranca.last_transaction.qr_code,
          qrCodeUrl: cobranca.last_transaction.qr_code_url,
          expiraEm: cobranca.last_transaction.expires_at,
        }
      : null,
  };
}

/**
 * Confirma o pagamento e passa a bola para a logística.
 *
 * Chamado pelo checkout (cartão aprovado na hora) e pelo webhook (Pix). É a
 * fronteira entre o financeiro e a operação: daqui em diante quem move o
 * pedido é `servicos/logistica.ts`.
 */
export async function marcarComoPago(pedidoId: string) {
  const pedido = await prisma.pedido.findUnique({ where: { id: pedidoId } });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');

  const jaSeguiu = !['AGUARDANDO_PAGAMENTO', 'PAGO'].includes(pedido.estado);
  if (jaSeguiu) return pedido; // webhook repetido não faz nada

  await prisma.$transaction([
    prisma.pedido.update({
      where: { id: pedido.id },
      data: { estado: 'PAGO', pagoEm: pedido.pagoEm ?? new Date() },
    }),
    // o anúncio sai do ar: é peça única
    prisma.anuncio.update({
      where: { id: pedido.anuncioId },
      data: { estado: 'VENDIDO' },
    }),
    prisma.eventoPedido.create({
      data: { pedidoId: pedido.id, tipo: 'pagamento_confirmado' },
    }),
  ]);

  // abre a operação conforme a modalidade congelada no pedido. Nenhum dos dois
  // caminhos cria cobrança nova nem mexe no split.
  if (pedido.modalidade === 'PLATAFORMA') {
    await abrirEntregaDoPedido(pedido.id);
  } else {
    await abrirEntregaDoVendedor(pedido.id);
  }

  log.info({ pedido: pedido.codigo }, 'pagamento confirmado');
  return prisma.pedido.findUnique({ where: { id: pedido.id } });
}
