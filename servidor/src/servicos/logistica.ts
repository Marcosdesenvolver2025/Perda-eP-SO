/**
 * Operação das entregas.
 *
 * Este serviço é o único lugar que muda o estado de uma corrida. Ele consulta
 * a máquina de estados pura (`dominio/logistica.ts`), grava a transição, move
 * o pedido junto e dispara as notificações.
 *
 * FRONTEIRA COM O FINANCEIRO — leia antes de mexer:
 * nada aqui cria cobrança, altera split, repassa ou estorna. A logística só
 * empurra o pedido até ENTREGUE (ida) ou DEVOLVIDO_AO_VENDEDOR (volta). Quem
 * cuida do dinheiro é `checkout.ts`, `repasse.ts` e `reembolso.ts`, que já
 * existiam e continuam mandando nas regras da pagar.me.
 */

import { ambiente } from '../ambiente';
import {
  estadoDoPedidoPara,
  gerarCodigoConfirmacao,
  mascararTelefone,
  podeTransitar,
  proximosPassos,
  type Ator,
  type EstadoEntrega,
  type TipoEntrega,
} from '../dominio/logistica';
import { prazoParaTestar } from '../dominio/reembolso';
import { conflito, erroDeValidacao, naoEncontrado, semPermissao } from '../erros';
import { log } from '../log';
import { prisma } from '../prisma';
import { avisar, avisos } from './notificacoes';

/** Monta o endereço em uma linha só, do jeito que o entregador lê. */
function enderecoEmLinha(e: {
  logradouro: string;
  numero: string;
  complemento?: string | null;
  bairro: string;
  cidade: string;
  uf: string;
}): string {
  return [e.logradouro, e.numero, e.complemento, e.bairro, `${e.cidade}/${e.uf}`]
    .filter(Boolean)
    .join(', ');
}

// ---------------------------------------------------------------------------
// Criação da corrida
// ---------------------------------------------------------------------------

/**
 * Cria a entrega de ida assim que o pagamento é confirmado.
 *
 * Chamada por `checkout.ts#marcarComoPago`. Não cria cobrança nem mexe no
 * split: a corrida é só a contraparte operacional de um pedido já pago.
 *
 * Idempotente: se já existe entrega de ida para o pedido, devolve a que existe.
 */
export async function abrirEntregaDoPedido(pedidoId: string) {
  const pedido = await prisma.pedido.findUnique({
    where: { id: pedidoId },
    include: {
      anuncio: { include: { enderecoColeta: true } },
      vendedor: true,
      comprador: true,
      endereco: true,
      entregas: { where: { tipo: 'ENTREGA' } },
    },
  });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');

  const existente = pedido.entregas[0];
  if (existente) return existente;

  if (pedido.modalidade !== 'ENTREGADOR_PROPRIO') {
    // combinado entre as partes: não há corrida a fazer
    return null;
  }
  const enderecoEntrega = pedido.endereco;
  if (!enderecoEntrega) {
    throw conflito('Pedido sem endereço de entrega.');
  }

  const coleta = pedido.anuncio.enderecoColeta;
  const entrega = await prisma.$transaction(async (tx) => {
    const criada = await tx.entrega.create({
      data: {
        pedidoId: pedido.id,
        tipo: 'ENTREGA',
        estado: 'AGUARDANDO_ATRIBUICAO',
        valorEntregador: pedido.custoEntregador,
        coletaEndereco: coleta
          ? enderecoEmLinha(coleta)
          : `Combinar com ${pedido.vendedor.nome} — ${pedido.vendedor.bairro ?? ambiente.CIDADE}`,
        coletaReferencia: coleta?.referencia ?? null,
        coletaContato: pedido.vendedor.nome,
        coletaTelefone: pedido.vendedor.telefone,
        entregaEndereco: enderecoEmLinha(enderecoEntrega),
        entregaReferencia: enderecoEntrega.referencia,
        entregaContato: pedido.comprador.nome,
        entregaTelefone: pedido.comprador.telefone,
        // o código já nasce aqui; o comprador vê no pedido dele
        codigoConfirmacao: gerarCodigoConfirmacao(),
      },
    });

    await tx.pedido.update({
      where: { id: pedido.id },
      data: { estado: 'AGUARDANDO_AGENDAMENTO_DE_COLETA' },
    });

    await tx.eventoEntrega.create({
      data: { entregaId: criada.id, para: 'AGUARDANDO_ATRIBUICAO' },
    });
    await tx.eventoPedido.create({
      data: { pedidoId: pedido.id, tipo: 'entrega_aberta', detalhe: { entregaId: criada.id } },
    });

    return criada;
  });

  log.info({ pedido: pedido.codigo, entrega: entrega.id }, 'entrega aberta na fila');
  return entrega;
}

/**
 * Cria a corrida REVERSA de uma devolução aprovada: busca no comprador e
 * devolve ao vendedor. O estorno só acontece quando ela é concluída.
 */
export async function abrirDevolucao(pedidoId: string, adminId: string) {
  const pedido = await prisma.pedido.findUnique({
    where: { id: pedidoId },
    include: {
      anuncio: { include: { enderecoColeta: true } },
      vendedor: true,
      comprador: true,
      endereco: true,
      entregas: { where: { tipo: 'DEVOLUCAO' } },
    },
  });
  if (!pedido) throw naoEncontrado('Pedido não encontrado.');

  const existente = pedido.entregas[0];
  if (existente) return existente;

  const enderecoComprador = pedido.endereco;
  if (!enderecoComprador) throw conflito('Pedido sem endereço do comprador.');

  const destino = pedido.anuncio.enderecoColeta;
  const entrega = await prisma.$transaction(async (tx) => {
    const criada = await tx.entrega.create({
      data: {
        pedidoId: pedido.id,
        tipo: 'DEVOLUCAO',
        estado: 'AGUARDANDO_ATRIBUICAO',
        valorEntregador: pedido.custoEntregador,
        // na volta, a coleta é no comprador e a entrega é no vendedor
        coletaEndereco: enderecoEmLinha(enderecoComprador),
        coletaReferencia: enderecoComprador.referencia,
        coletaContato: pedido.comprador.nome,
        coletaTelefone: pedido.comprador.telefone,
        entregaEndereco: destino
          ? enderecoEmLinha(destino)
          : `Combinar com ${pedido.vendedor.nome} — ${pedido.vendedor.bairro ?? ambiente.CIDADE}`,
        entregaReferencia: destino?.referencia ?? null,
        entregaContato: pedido.vendedor.nome,
        entregaTelefone: pedido.vendedor.telefone,
        observacoes: 'DEVOLUÇÃO: buscar no comprador e devolver ao vendedor.',
        codigoConfirmacao: gerarCodigoConfirmacao(),
      },
    });

    await tx.pedido.update({
      where: { id: pedido.id },
      data: { estado: 'DEVOLUCAO_APROVADA' },
    });
    await tx.eventoEntrega.create({
      data: { entregaId: criada.id, para: 'AGUARDANDO_ATRIBUICAO', autorId: adminId },
    });
    await tx.eventoPedido.create({
      data: {
        pedidoId: pedido.id,
        tipo: 'devolucao_coleta_aberta',
        autorId: adminId,
        detalhe: { entregaId: criada.id },
      },
    });

    return criada;
  });

  await avisar(pedido.compradorId, avisos.devolucaoAprovada());
  log.info({ pedido: pedido.codigo, entrega: entrega.id }, 'coleta reversa aberta');
  return entrega;
}

// ---------------------------------------------------------------------------
// Transição de estado
// ---------------------------------------------------------------------------

export interface PedidoDeTransicao {
  entregaId: string;
  para: EstadoEntrega;
  ator: Ator;
  autorId: string;
  fotoPacote?: string | null;
  volumes?: number | null;
  codigoConfirmacao?: string | null;
  motivo?: string | null;
  /** Só na atribuição: quem vai fazer a corrida. */
  entregadorId?: string | null;
  fotoEntrega?: string | null;
}

/**
 * Aplica uma transição. É o coração do módulo — todo endpoint do entregador e
 * do administrador cai aqui.
 */
export async function transitar(pedidoDeTransicao: PedidoDeTransicao) {
  const {
    entregaId,
    para,
    ator,
    autorId,
    entregadorId,
    fotoPacote,
    volumes,
    codigoConfirmacao,
    motivo,
    fotoEntrega,
  } = pedidoDeTransicao;

  const entrega = await prisma.entrega.findUnique({
    where: { id: entregaId },
    include: { pedido: { include: { anuncio: { select: { titulo: true } } } } },
  });
  if (!entrega) throw naoEncontrado('Entrega não encontrada.');

  const de = entrega.estado as EstadoEntrega;
  const tipo = entrega.tipo as TipoEntrega;

  // o entregador só mexe na corrida que é dele
  if (ator === 'ENTREGADOR' && entrega.entregadorId !== autorId) {
    throw semPermissao('Esta entrega é de outro entregador.');
  }

  const validacao = podeTransitar(de, para, ator, {
    fotoPacote,
    volumes,
    codigoConfirmacao,
    motivo,
    entregador: entregadorId,
  });
  if (!validacao.permitida) throw conflito(validacao.motivo ?? 'Transição não permitida.');

  // o código digitado precisa bater com o que o sistema gerou
  if (para === 'ENTREGUE' && entrega.codigoConfirmacao) {
    if (codigoConfirmacao?.trim() !== entrega.codigoConfirmacao) {
      throw erroDeValidacao('Código de confirmação incorreto. Confira com quem está recebendo.');
    }
  }

  const agora = new Date();
  const estadoPedido = estadoDoPedidoPara(para, tipo);

  const atualizada = await prisma.$transaction(async (tx) => {
    const dados: Record<string, unknown> = { estado: para };

    switch (para) {
      case 'ATRIBUIDA':
        dados.entregadorId = entregadorId;
        dados.atribuidaPorId = autorId;
        dados.atribuidaEm = agora;
        break;
      case 'ACEITA':
        dados.aceitaEm = agora;
        break;
      case 'RECUSADA':
        // desgruda o entregador para a corrida poder voltar para a fila
        dados.entregadorId = null;
        break;
      case 'AGUARDANDO_ATRIBUICAO':
        dados.entregadorId = null;
        dados.atribuidaEm = null;
        dados.atribuidaPorId = null;
        break;
      case 'PRODUTO_COLETADO':
        dados.fotoPacoteUrl = fotoPacote;
        dados.volumes = volumes;
        dados.coletadaEm = agora;
        break;
      case 'ENTREGUE':
        dados.entregueEm = agora;
        if (fotoEntrega) dados.fotoEntregaUrl = fotoEntrega;
        break;
      case 'CANCELADA':
        dados.canceladaEm = agora;
        break;
    }

    const nova = await tx.entrega.update({ where: { id: entregaId }, data: dados });

    if (para === 'RECUSADA' && entrega.entregadorId) {
      // guarda quem recusou, para não reoferecer a mesma corrida a ele
      await tx.recusaEntrega.upsert({
        where: {
          entregaId_entregadorId: {
            entregaId,
            entregadorId: entrega.entregadorId,
          },
        },
        update: { motivo: motivo ?? 'sem motivo' },
        create: {
          entregaId,
          entregadorId: entrega.entregadorId,
          motivo: motivo ?? 'sem motivo',
        },
      });
    }

    await tx.eventoEntrega.create({
      data: {
        entregaId,
        de,
        para,
        autorId,
        detalhe: {
          ...(motivo ? { motivo } : {}),
          ...(volumes ? { volumes } : {}),
          ...(entregadorId ? { entregadorId } : {}),
        },
      },
    });

    if (estadoPedido) {
      const dadosPedido: Record<string, unknown> = { estado: estadoPedido };

      // ENTREGUE na ida é o marco que abre a janela de devolução
      if (estadoPedido === 'ENTREGUE') {
        dadosPedido.entregueEm = agora;
        dadosPedido.prazoTesteAte = prazoParaTestar(agora, ambiente.DIAS_PARA_TESTAR);
      }

      await tx.pedido.update({ where: { id: entrega.pedidoId }, data: dadosPedido });
      await tx.eventoPedido.create({
        data: {
          pedidoId: entrega.pedidoId,
          tipo: `logistica_${estadoPedido.toLowerCase()}`,
          autorId,
          detalhe: { entregaId, estadoEntrega: para },
        },
      });
    }

    return nova;
  });

  // recusou: volta sozinha para a fila do administrador
  if (para === 'RECUSADA') {
    await transitar({
      entregaId,
      para: 'AGUARDANDO_ATRIBUICAO',
      ator: 'SISTEMA',
      autorId,
    });
  }

  if (para === 'ENTREGUE') {
    await concluirCorrida(entregaId, entrega.pedidoId, tipo, autorId);
  }

  await notificarTransicao(entrega.pedidoId, entregaId, para, tipo);

  log.info({ entrega: entregaId, de, para, ator }, 'transição de entrega');
  return atualizada;
}

/**
 * Efeitos financeiros do fim de uma corrida.
 *
 * Só existem dois, e os dois são chamadas para serviços financeiros que já
 * existiam — a logística não calcula nem move dinheiro por conta própria:
 *
 *  1. pagar o entregador pela corrida concluída;
 *  2. na DEVOLUÇÃO, disparar o estorno agora que o vendedor tem o produto.
 *
 * Os imports são dinâmicos para não criar ciclo entre logística e reembolso.
 * Falha aqui não desfaz a entrega: o entregador já entregou, e o log fica com
 * o alerta para a equipe resolver.
 */
async function concluirCorrida(
  entregaId: string,
  pedidoId: string,
  tipo: TipoEntrega,
  autorId: string,
): Promise<void> {
  try {
    const { pagarEntregador } = await import('./repasse');
    await pagarEntregador(entregaId);
  } catch (erro) {
    log.error({ erro, entregaId }, 'falha ao pagar entregador');
  }

  if (tipo !== 'DEVOLUCAO') return;

  try {
    const { concluirAposDevolucao } = await import('./reembolso');
    await concluirAposDevolucao(pedidoId, autorId);
  } catch (erro) {
    // o produto já voltou ao vendedor; o estorno precisa ser resolvido na mão
    log.error({ erro, pedidoId }, 'produto devolvido mas o estorno falhou');
  }
}

/** Avisos disparados por cada transição. Falha aqui nunca derruba a operação. */
async function notificarTransicao(
  pedidoId: string,
  entregaId: string,
  para: EstadoEntrega,
  tipo: TipoEntrega,
): Promise<void> {
  try {
    const pedido = await prisma.pedido.findUnique({
      where: { id: pedidoId },
      include: { anuncio: { select: { titulo: true } } },
    });
    if (!pedido) return;

    const entrega = await prisma.entrega.findUnique({ where: { id: entregaId } });
    const produto = pedido.anuncio.titulo;

    switch (para) {
      case 'ATRIBUIDA':
        if (entrega?.entregadorId) {
          await avisar(
            entrega.entregadorId,
            avisos.novaCorridaParaVoce(pedido.codigo, entrega.coletaReferencia ?? null),
          );
        }
        break;

      case 'A_CAMINHO_DA_COLETA':
        // quem espera o entregador na coleta: vendedor na ida, comprador na volta
        await avisar(
          tipo === 'ENTREGA' ? pedido.vendedorId : pedido.compradorId,
          avisos.entregadorACaminho(produto),
        );
        break;

      case 'PRODUTO_COLETADO':
        if (tipo === 'ENTREGA') {
          await avisar(pedido.compradorId, avisos.produtoColetado(produto));
        } else {
          await avisar(pedido.vendedorId, avisos.devolucaoACaminhoDoVendedor());
        }
        break;

      case 'CHEGOU_NA_ENTREGA':
        if (entrega?.codigoConfirmacao) {
          await avisar(
            tipo === 'ENTREGA' ? pedido.compradorId : pedido.vendedorId,
            avisos.chegouParaEntregar(entrega.codigoConfirmacao),
          );
        }
        break;

      case 'ENTREGUE':
        if (tipo === 'ENTREGA') {
          await avisar(pedido.compradorId, avisos.entregue(ambiente.DIAS_PARA_TESTAR));
          await avisar(
            pedido.vendedorId,
            avisos.vendaConcluida(
              (pedido.valorVendedor / 100).toLocaleString('pt-BR', {
                style: 'currency',
                currency: 'BRL',
              }),
            ),
          );
        }
        break;
    }
  } catch (erro) {
    log.error({ erro, entregaId, para }, 'falha ao notificar transição');
  }
}

// ---------------------------------------------------------------------------
// Consultas
// ---------------------------------------------------------------------------

const CAMPOS_DA_CORRIDA = {
  id: true,
  tipo: true,
  estado: true,
  valorEntregador: true,
  coletaEndereco: true,
  coletaReferencia: true,
  coletaContato: true,
  coletaTelefone: true,
  entregaEndereco: true,
  entregaReferencia: true,
  entregaContato: true,
  entregaTelefone: true,
  observacoes: true,
  janelaColetaInicio: true,
  janelaColetaFim: true,
  volumes: true,
  fotoPacoteUrl: true,
  criadoEm: true,
  pedido: {
    select: {
      codigo: true,
      valorProduto: true,
      anuncio: {
        select: {
          titulo: true,
          pesoG: true,
          comprimentoCm: true,
          larguraCm: true,
          alturaCm: true,
          fotos: { take: 1, orderBy: { ordem: 'asc' as const } },
        },
      },
    },
  },
} as const;

interface CorridaBruta {
  estado: string;
  coletaTelefone: string | null;
  entregaTelefone: string | null;
}

/**
 * Protege os telefones: número inteiro só depois de aceitar a corrida.
 * Antes disso, o entregador vê mascarado.
 */
function protegerContatos<T extends CorridaBruta>(corrida: T, ator: Ator) {
  const aceita = !['AGUARDANDO_ATRIBUICAO', 'ATRIBUIDA', 'RECUSADA'].includes(corrida.estado);
  const liberar = ator === 'ADMIN' || aceita;

  return {
    ...corrida,
    coletaTelefone: liberar ? corrida.coletaTelefone : mascararTelefone(corrida.coletaTelefone),
    entregaTelefone: liberar ? corrida.entregaTelefone : mascararTelefone(corrida.entregaTelefone),
    telefoneLiberado: liberar,
  };
}

/** Corridas que o entregador logado pode aceitar (as atribuídas a ele). */
export async function corridasOferecidas(entregadorId: string) {
  const itens = await prisma.entrega.findMany({
    where: { entregadorId, estado: 'ATRIBUIDA' },
    orderBy: { atribuidaEm: 'asc' },
    select: CAMPOS_DA_CORRIDA,
  });
  return itens.map((c) => protegerContatos(c, 'ENTREGADOR'));
}

/** Corridas em andamento do entregador logado. */
export async function minhasCorridas(entregadorId: string) {
  const itens = await prisma.entrega.findMany({
    where: {
      entregadorId,
      estado: {
        in: [
          'ACEITA',
          'A_CAMINHO_DA_COLETA',
          'CHEGOU_NA_COLETA',
          'PRODUTO_COLETADO',
          'EM_ROTA_PARA_ENTREGA',
          'CHEGOU_NA_ENTREGA',
        ],
      },
    },
    orderBy: { atualizadoEm: 'asc' },
    select: CAMPOS_DA_CORRIDA,
  });

  return itens.map((c) => ({
    ...protegerContatos(c, 'ENTREGADOR'),
    proximosPassos: proximosPassos(c.estado as EstadoEntrega, 'ENTREGADOR'),
  }));
}

/** Histórico do entregador: o que ele já concluiu. */
export function corridasConcluidas(entregadorId: string, limite = 50) {
  return prisma.entrega.findMany({
    where: { entregadorId, estado: 'ENTREGUE' },
    orderBy: { entregueEm: 'desc' },
    take: limite,
    select: {
      id: true,
      tipo: true,
      valorEntregador: true,
      entregueEm: true,
      pedido: { select: { codigo: true, anuncio: { select: { titulo: true } } } },
    },
  });
}

/** Fila do administrador: corridas sem entregador. */
export async function filaDeAtribuicao() {
  const itens = await prisma.entrega.findMany({
    where: { estado: { in: ['AGUARDANDO_ATRIBUICAO', 'ATRIBUIDA'] } },
    orderBy: { criadoEm: 'asc' },
    select: {
      ...CAMPOS_DA_CORRIDA,
      atribuidaEm: true,
      entregador: { select: { id: true, nome: true, telefone: true } },
      recusas: {
        select: { motivo: true, criadoEm: true, entregador: { select: { nome: true } } },
      },
    },
  });
  return itens.map((c) => protegerContatos(c, 'ADMIN'));
}

/** Uma corrida específica, com a linha do tempo — usado no detalhe. */
export async function detalheDaCorrida(entregaId: string, ator: Ator) {
  const entrega = await prisma.entrega.findUnique({
    where: { id: entregaId },
    select: {
      ...CAMPOS_DA_CORRIDA,
      codigoConfirmacao: ator === 'ADMIN',
      fotoEntregaUrl: true,
      entregador: { select: { id: true, nome: true } },
      eventos: { orderBy: { criadoEm: 'asc' } },
    },
  });
  if (!entrega) throw naoEncontrado('Entrega não encontrada.');

  return {
    ...protegerContatos(entrega, ator),
    proximosPassos: proximosPassos(entrega.estado as EstadoEntrega, ator),
  };
}

/** Entregador liga e desliga a disponibilidade dele. */
export async function definirDisponibilidade(entregadorId: string, disponivel: boolean) {
  return prisma.usuario.update({
    where: { id: entregadorId },
    data: { entregadorDisponivel: disponivel },
    select: { id: true, entregadorDisponivel: true },
  });
}

/**
 * Guarda a última posição do entregador.
 * Hoje é só registro; é o que vai alimentar a atribuição automática por
 * distância quando ela for ligada (ver `atribuicao.ts`).
 */
export async function registrarPosicao(
  entregadorId: string,
  latitude: number,
  longitude: number,
) {
  return prisma.usuario.update({
    where: { id: entregadorId },
    data: {
      entregadorLat: latitude,
      entregadorLng: longitude,
      entregadorPosicaoEm: new Date(),
    },
    select: { id: true },
  });
}
