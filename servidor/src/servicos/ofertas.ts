/**
 * Negociação de preço: o que o banco precisa fazer.
 *
 * As decisões ("pode?", "quanto vale?") ficam em `dominio/ofertas.ts`, que é
 * puro e testado. Aqui é só o que depende do banco: buscar, gravar, e checar
 * que quem está agindo é mesmo parte da negociação.
 *
 * NÃO MOVE DINHEIRO. Uma oferta aceita apenas grava o valor combinado; a
 * cobrança continua acontecendo no `checkout.ts`, que lê esse valor e o passa
 * para o `calcularSplit` de sempre.
 */

import {
  DIAS_PARA_RESPONDER_OFERTA,
  estaEmAberto,
  estaVencida,
  podeAgir,
  validarContraproposta,
  validarProposta,
  type AtorDaOferta,
} from '../dominio/ofertas';
import { erroDeValidacao, naoEncontrado, semPermissao } from '../erros';
import { prisma } from '../prisma';

const incluir = {
  anuncio: { select: { id: true, titulo: true, preco: true, fotos: true } },
  comprador: { select: { id: true, nome: true, fotoUrl: true } },
  vendedor: { select: { id: true, nome: true, apelidoLoja: true } },
  lances: { orderBy: { criadoEm: 'asc' } },
} as const;

/** Qual é o papel deste usuário nesta negociação. Lança se não for nenhum. */
function papelDe(
  oferta: { compradorId: string; vendedorId: string },
  usuarioId: string,
): AtorDaOferta {
  if (oferta.compradorId === usuarioId) return 'COMPRADOR';
  if (oferta.vendedorId === usuarioId) return 'VENDEDOR';
  throw semPermissao('Esta negociação não é sua.');
}

/**
 * Marca como vencida a oferta que passou do prazo.
 *
 * Roda na leitura em vez de depender só do job: quem abre a tela precisa ver
 * o estado certo agora, não no próximo ciclo de tarefas.
 */
async function expirarSeVencida<T extends { id: string; estado: string; lanceEm: Date }>(
  oferta: T,
): Promise<T> {
  if (!estaEmAberto(oferta.estado as never) || !estaVencida(oferta.lanceEm)) return oferta;
  await prisma.oferta.update({ where: { id: oferta.id }, data: { estado: 'EXPIRADA' } });
  return { ...oferta, estado: 'EXPIRADA' };
}

/** Todas as negociações em que eu sou uma das partes. */
export async function listarMinhas(usuarioId: string) {
  const ofertas = await prisma.oferta.findMany({
    where: { OR: [{ compradorId: usuarioId }, { vendedorId: usuarioId }] },
    include: incluir,
    orderBy: { lanceEm: 'desc' },
    take: 100,
  });

  return Promise.all(
    ofertas.map(async (o) => {
      const atual = await expirarSeVencida(o);
      const meuPapel = papelDe(o, usuarioId);
      return {
        ...atual,
        meuPapel,
        minhaVez:
          (atual.estado === 'ABERTA' && meuPapel === 'VENDEDOR') ||
          (atual.estado === 'CONTRAPROPOSTA' && meuPapel === 'COMPRADOR'),
        prazoAte: new Date(atual.lanceEm.getTime() + DIAS_PARA_RESPONDER_OFERTA * 86_400_000),
      };
    }),
  );
}

/** O comprador propõe um valor pelo anúncio. */
export async function propor(dados: {
  anuncioId: string;
  compradorId: string;
  valor: number;
  recado?: string;
}) {
  const anuncio = await prisma.anuncio.findUnique({
    where: { id: dados.anuncioId },
    select: { id: true, preco: true, vendedorId: true, estado: true },
  });
  if (!anuncio) throw naoEncontrado('Anúncio não encontrado.');
  if (anuncio.estado !== 'ATIVO') {
    throw erroDeValidacao('Este anúncio não está mais disponível.');
  }
  if (anuncio.vendedorId === dados.compradorId) {
    throw erroDeValidacao('Você não pode fazer oferta no seu próprio anúncio.');
  }

  const validacao = validarProposta(dados.valor, anuncio.preco);
  if (!validacao.valido) throw erroDeValidacao(validacao.motivo);

  return prisma.$transaction(async (tx) => {
    // uma negociação em aberto por par comprador/anúncio: a nova cancela a anterior
    await tx.oferta.updateMany({
      where: {
        anuncioId: anuncio.id,
        compradorId: dados.compradorId,
        estado: { in: ['ABERTA', 'CONTRAPROPOSTA'] },
      },
      data: { estado: 'CANCELADA' },
    });

    return tx.oferta.create({
      data: {
        anuncioId: anuncio.id,
        compradorId: dados.compradorId,
        vendedorId: anuncio.vendedorId,
        precoAnunciado: anuncio.preco,
        valorAtual: dados.valor,
        estado: 'ABERTA',
        ultimoLancePor: 'COMPRADOR',
        recado: dados.recado ?? null,
        lances: {
          create: {
            por: 'COMPRADOR',
            valor: dados.valor,
            recado: dados.recado ?? null,
          },
        },
      },
      include: incluir,
    });
  });
}

/** Busca a oferta garantindo que o usuário é parte dela e que ainda vale. */
async function paraAgir(ofertaId: string, usuarioId: string) {
  const oferta = await prisma.oferta.findUnique({ where: { id: ofertaId }, include: incluir });
  if (!oferta) throw naoEncontrado('Negociação não encontrada.');

  const atual = await expirarSeVencida(oferta);
  const papel = papelDe(atual, usuarioId);
  return { oferta: atual, papel };
}

export async function aceitar(ofertaId: string, usuarioId: string) {
  const { oferta, papel } = await paraAgir(ofertaId, usuarioId);
  if (!podeAgir(oferta.estado as never, papel, 'ACEITAR')) {
    throw erroDeValidacao('Você não pode aceitar esta oferta agora.');
  }
  return prisma.oferta.update({
    where: { id: ofertaId },
    data: { estado: 'ACEITA', respondidoEm: new Date() },
    include: incluir,
  });
}

export async function recusar(ofertaId: string, usuarioId: string) {
  const { oferta, papel } = await paraAgir(ofertaId, usuarioId);
  if (!podeAgir(oferta.estado as never, papel, 'RECUSAR')) {
    throw erroDeValidacao('Você não pode recusar esta oferta agora.');
  }
  return prisma.oferta.update({
    where: { id: ofertaId },
    data: { estado: 'RECUSADA', respondidoEm: new Date() },
    include: incluir,
  });
}

/** Quem propôs desiste antes de a outra parte responder. */
export async function cancelar(ofertaId: string, usuarioId: string) {
  const { oferta, papel } = await paraAgir(ofertaId, usuarioId);
  if (!podeAgir(oferta.estado as never, papel, 'CANCELAR')) {
    throw erroDeValidacao('Você não pode cancelar: a proposta em aberto não é sua.');
  }
  return prisma.oferta.update({
    where: { id: ofertaId },
    data: { estado: 'CANCELADA' },
    include: incluir,
  });
}

/** O vendedor devolve com outro valor. Passa a vez para o comprador. */
export async function contrapropor(
  ofertaId: string,
  usuarioId: string,
  valor: number,
  recado?: string,
) {
  const { oferta, papel } = await paraAgir(ofertaId, usuarioId);
  if (!podeAgir(oferta.estado as never, papel, 'CONTRAPROPOR')) {
    throw erroDeValidacao('Só o vendedor contrapropõe, e só enquanto a proposta está aberta.');
  }

  const validacao = validarContraproposta(valor, oferta.valorAtual, oferta.precoAnunciado);
  if (!validacao.valido) throw erroDeValidacao(validacao.motivo);

  return prisma.oferta.update({
    where: { id: ofertaId },
    data: {
      estado: 'CONTRAPROPOSTA',
      valorAtual: valor,
      ultimoLancePor: 'VENDEDOR',
      recado: recado ?? null,
      // o prazo reinicia: agora é o comprador que tem 3 dias
      lanceEm: new Date(),
      lances: { create: { por: 'VENDEDOR', valor, recado: recado ?? null } },
    },
    include: incluir,
  });
}

/**
 * O valor que o checkout deve cobrar por causa desta oferta.
 *
 * Devolve `null` fora do caso aceito — inclusive quando a oferta é de outra
 * pessoa ou de outro anúncio. Cair no preço do anúncio é o padrão seguro:
 * o erro aqui seria vender mais barato do que o combinado.
 */
export async function valorCombinado(
  ofertaId: string,
  compradorId: string,
  anuncioId: string,
): Promise<number | null> {
  const oferta = await prisma.oferta.findUnique({
    where: { id: ofertaId },
    select: {
      estado: true,
      valorAtual: true,
      compradorId: true,
      anuncioId: true,
      pedido: { select: { id: true } },
    },
  });

  if (!oferta) return null;
  if (oferta.estado !== 'ACEITA') return null;
  if (oferta.compradorId !== compradorId) return null;
  if (oferta.anuncioId !== anuncioId) return null;
  // uma oferta aceita vale por uma compra só
  if (oferta.pedido) return null;

  return oferta.valorAtual;
}

/** Vence as ofertas paradas. Roda junto com o ciclo de repasses. */
export async function expirarVencidas(): Promise<number> {
  const limite = new Date(Date.now() - DIAS_PARA_RESPONDER_OFERTA * 86_400_000);
  const { count } = await prisma.oferta.updateMany({
    where: { estado: { in: ['ABERTA', 'CONTRAPROPOSTA'] }, lanceEm: { lt: limite } },
    data: { estado: 'EXPIRADA' },
  });
  return count;
}
