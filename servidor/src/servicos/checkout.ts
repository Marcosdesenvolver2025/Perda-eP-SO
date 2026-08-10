/**
 * Compra: monta o split, cobra pela pagar.me e cria o pedido.
 *
 * O dinheiro NÃO cai na mão do vendedor aqui. O split já distribui os valores,
 * mas os recebedores são criados com transferência automática desligada, então
 * o valor fica retido no saldo até a janela de 7 dias vencer
 * (ver `servicos/repasse.ts`).
 */

import { randomBytes } from 'node:crypto';

import { ambiente } from '../ambiente';
import { calcularSplit, montarRegrasSplit } from '../dominio/comissao';
import { calcularFrete, validarMedidas } from '../dominio/frete';
import type { ModalidadeEntrega } from '../dominio/regras';
import { conflito, erroDeValidacao, naoEncontrado } from '../erros';
import * as pagarme from '../integracoes/pagarme';
import { log } from '../log';
import { prisma } from '../prisma';

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
  modalidade: ModalidadeEntrega;
  enderecoId?: string;
  pagamento:
    | { tipo: 'credit_card'; tokenCartao: string; parcelas: number }
    | { tipo: 'pix' };
}

export interface ResumoDaCompra {
  valorProduto: number;
  valorFrete: number;
  valorTotal: number;
  taxaComissao: number;
  valorComissao: number;
  /** O que o vendedor recebe depois da janela de teste. */
  valorVendedor: number;
  modalidade: ModalidadeEntrega;
}

/**
 * Simula a compra sem cobrar nada. O app chama isso na tela do produto para
 * mostrar frete e prazo antes de o comprador decidir.
 */
export async function simular(
  anuncioId: string,
  modalidade: ModalidadeEntrega,
): Promise<ResumoDaCompra> {
  const anuncio = await prisma.anuncio.findUnique({ where: { id: anuncioId } });
  if (!anuncio || anuncio.estado !== 'ATIVO') {
    throw naoEncontrado('Este anúncio não está mais disponível.');
  }

  const medidas = {
    pesoG: anuncio.pesoG,
    comprimentoCm: anuncio.comprimentoCm,
    larguraCm: anuncio.larguraCm,
    alturaCm: anuncio.alturaCm,
  };

  const valorFrete =
    modalidade === 'ENTREGADOR_PROPRIO' ? calcularFrete(medidas) : 0;

  const split = calcularSplit({
    valorProduto: anuncio.preco,
    valorFrete,
    modalidade,
  });

  return {
    valorProduto: split.valorProduto,
    valorFrete: split.valorFrete,
    valorTotal: split.total,
    taxaComissao: split.taxaComissao,
    valorComissao: split.comissao,
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

  const medidas = {
    pesoG: anuncio.pesoG,
    comprimentoCm: anuncio.comprimentoCm,
    larguraCm: anuncio.larguraCm,
    alturaCm: anuncio.alturaCm,
  };
  const validacao = validarMedidas(medidas);
  if (!validacao.valido) {
    throw erroDeValidacao(validacao.erros.join(' '), validacao.erros);
  }

  const comEntregador = entrada.modalidade === 'ENTREGADOR_PROPRIO';
  if (comEntregador && !anuncio.aceitaEntregador) {
    throw conflito('Este anúncio não aceita entrega pelos nossos entregadores.');
  }
  if (comEntregador && !entrada.enderecoId) {
    throw erroDeValidacao('Escolha o endereço de entrega.');
  }

  const valorFrete = comEntregador ? calcularFrete(medidas) : 0;
  const split = calcularSplit({
    valorProduto: anuncio.preco,
    valorFrete,
    modalidade: entrada.modalidade,
    repasseEntregador: ambiente.REPASSE_ENTREGADOR,
  });

  /**
   * O entregador só é escalado depois do pagamento, então na hora do split
   * ainda não sabemos quem vai entregar. A parte do frete que caberia a ele
   * fica com a plataforma na cobrança e é repassada ao entregador quando a
   * entrega é concluída (ver `servicos/repasse.ts#pagarEntregador`).
   */
  const regras = montarRegrasSplit(
    { ...split, valorEntregador: 0, valorPlataforma: split.valorPlataforma + split.valorEntregador },
    {
      plataforma: ambiente.PAGARME_RECEBEDOR_PLATAFORMA,
      vendedor: recebedorVendedor.recipientId,
    },
  );

  const codigo = gerarCodigo();

  const pedido = await prisma.pedido.create({
    data: {
      codigo,
      anuncioId: anuncio.id,
      compradorId: comprador.id,
      vendedorId: anuncio.vendedorId,
      enderecoId: entrada.enderecoId ?? null,
      modalidade: entrada.modalidade,
      estado: 'AGUARDANDO_PAGAMENTO',
      valorProduto: split.valorProduto,
      valorFrete: split.valorFrete,
      valorTotal: split.total,
      taxaComissao: split.taxaComissao,
      valorComissao: split.comissao,
      valorVendedor: split.valorVendedor,
      valorEntregador: split.valorEntregador,
      valorPlataforma: split.valorPlataforma,
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
          ...(split.valorFrete > 0
            ? [
                {
                  descricao: 'Entrega Vendas Itinga',
                  valorUnitario: split.valorFrete,
                  quantidade: 1,
                  codigo: 'frete',
                },
              ]
            : []),
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
 * Confirma o pagamento: reserva o anúncio e abre a entrega quando for o caso.
 * Chamado pelo checkout (cartão aprovado na hora) e pelo webhook (Pix).
 */
export async function marcarComoPago(pedidoId: string) {
  const pedido = await prisma.pedido.findUnique({
    where: { id: pedidoId },
    include: { anuncio: true, endereco: true, vendedor: true },
  });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');
  if (pedido.estado !== 'AGUARDANDO_PAGAMENTO' && pedido.estado !== 'PAGO') {
    return pedido; // já seguiu adiante; webhook repetido não faz nada
  }

  await prisma.$transaction(async (tx) => {
    await tx.pedido.update({
      where: { id: pedido.id },
      data: { estado: 'PAGO', pagoEm: pedido.pagoEm ?? new Date() },
    });

    // o anúncio sai do ar: é peça única
    await tx.anuncio.update({
      where: { id: pedido.anuncioId },
      data: { estado: 'VENDIDO' },
    });

    if (pedido.modalidade === 'ENTREGADOR_PROPRIO' && pedido.endereco) {
      const jaExiste = await tx.entrega.findUnique({
        where: { pedidoId: pedido.id },
      });
      if (!jaExiste) {
        await tx.entrega.create({
          data: {
            pedidoId: pedido.id,
            estado: 'AGUARDANDO_ENTREGADOR',
            valorEntregador: pedido.valorEntregador,
            coletaEndereco: `Combinar com ${pedido.vendedor.nome} — ${
              pedido.vendedor.bairro ?? ambiente.CIDADE
            }`,
            entregaEndereco: [
              pedido.endereco.logradouro,
              pedido.endereco.numero,
              pedido.endereco.complemento,
              pedido.endereco.bairro,
              `${pedido.endereco.cidade}/${pedido.endereco.uf}`,
            ]
              .filter(Boolean)
              .join(', '),
            observacoes: pedido.endereco.referencia,
            codigoConfirmacao: String(Math.floor(1000 + Math.random() * 9000)),
          },
        });
      }
    }

    await tx.eventoPedido.create({
      data: { pedidoId: pedido.id, tipo: 'pagamento_confirmado' },
    });
  });

  log.info({ pedido: pedido.codigo }, 'pagamento confirmado');
  return prisma.pedido.findUnique({ where: { id: pedido.id } });
}
