/**
 * Vitrine e anúncios.
 */

import { Router } from 'express';
import { z } from 'zod';

import { ambiente } from '../ambiente';
import { validarMedidas } from '../dominio/frete';
import {
  ALTURA_MAXIMA_CM,
  LARGURA_MAXIMA_CM,
  PESO_MAXIMO_G,
  VALOR_MINIMO_VENDA,
  tarifaFixa,
  type ModalidadeEntrega,
} from '../dominio/regras';
import { erroDeValidacao, naoEncontrado, semPermissao } from '../erros';
import { exigirLogin, loginOpcional } from '../middlewares/autenticacao';
import { prisma } from '../prisma';

export const rotasAnuncios = Router();

const listarEsquema = z.object({
  busca: z.string().max(120).optional(),
  categoria: z.string().optional(),
  /** Filtra a vitrine pela lojinha de um vendedor. */
  vendedor: z.string().optional(),
  condicao: z.enum(['NOVO', 'SEMINOVO', 'USADO']).optional(),
  precoMin: z.coerce.number().int().nonnegative().optional(),
  precoMax: z.coerce.number().int().positive().optional(),
  ordem: z.enum(['recentes', 'menor_preco', 'maior_preco']).default('recentes'),
  pagina: z.coerce.number().int().positive().default(1),
  porPagina: z.coerce.number().int().min(1).max(50).default(20),
});

/** GET /anuncios — vitrine com busca e filtros. */
rotasAnuncios.get('/', loginOpcional, async (req, res, next) => {
  try {
    const f = listarEsquema.parse(req.query);

    const where = {
      estado: 'ATIVO' as const,
      ...(f.vendedor ? { vendedorId: f.vendedor } : {}),
      ...(f.categoria ? { categoria: { slug: f.categoria } } : {}),
      ...(f.condicao ? { condicao: f.condicao } : {}),
      ...(f.precoMin !== undefined || f.precoMax !== undefined
        ? { preco: { gte: f.precoMin ?? 0, ...(f.precoMax ? { lte: f.precoMax } : {}) } }
        : {}),
      ...(f.busca
        ? {
            OR: [
              { titulo: { contains: f.busca, mode: 'insensitive' as const } },
              { descricao: { contains: f.busca, mode: 'insensitive' as const } },
              { marca: { contains: f.busca, mode: 'insensitive' as const } },
            ],
          }
        : {}),
    };

    const ordenacao =
      f.ordem === 'menor_preco'
        ? { preco: 'asc' as const }
        : f.ordem === 'maior_preco'
          ? { preco: 'desc' as const }
          : { criadoEm: 'desc' as const };

    const [itens, total] = await Promise.all([
      prisma.anuncio.findMany({
        where,
        orderBy: ordenacao,
        skip: (f.pagina - 1) * f.porPagina,
        take: f.porPagina,
        include: {
          fotos: { orderBy: { ordem: 'asc' }, take: 1 },
          vendedor: { select: { id: true, nome: true, apelidoLoja: true, fotoUrl: true } },
          categoria: { select: { slug: true, nome: true } },
        },
      }),
      prisma.anuncio.count({ where }),
    ]);

    return res.json({
      itens,
      total,
      pagina: f.pagina,
      paginas: Math.ceil(total / f.porPagina),
    });
  } catch (erro) {
    return next(erro);
  }
});

/** GET /anuncios/:id — página do produto. */
rotasAnuncios.get('/:id', loginOpcional, async (req, res, next) => {
  try {
    const anuncio = await prisma.anuncio.findUnique({
      where: { id: req.params.id },
      include: {
        fotos: { orderBy: { ordem: 'asc' } },
        categoria: true,
        vendedor: {
          select: {
            id: true,
            nome: true,
            apelidoLoja: true,
            fotoUrl: true,
            bairro: true,
            criadoEm: true,
            avaliacoesRecebidas: { select: { nota: true } },
          },
        },
      },
    });
    if (!anuncio) throw naoEncontrado('Anúncio não encontrado.');

    // contagem de visualizações não pode derrubar a resposta
    prisma.anuncio
      .update({ where: { id: anuncio.id }, data: { visualizacoes: { increment: 1 } } })
      .catch(() => undefined);

    const notas = anuncio.vendedor.avaliacoesRecebidas.map((a) => a.nota);

    return res.json({
      ...anuncio,
      vendedor: {
        ...anuncio.vendedor,
        avaliacoesRecebidas: undefined,
        totalAvaliacoes: notas.length,
        notaMedia: notas.length
          ? Number((notas.reduce((a, b) => a + b, 0) / notas.length).toFixed(1))
          : null,
      },
      entrega: {
        // não há frete separado em nenhuma das modalidades: o comprador paga
        // só o preço do produto.
        modalidade: anuncio.modalidadeEntrega,
        diasParaTestar: ambiente.DIAS_PARA_TESTAR,
        entregaInclusa: anuncio.modalidadeEntrega === 'PLATAFORMA',
      },
      /** O que a plataforma desconta desta venda. */
      taxas: {
        comissao: Math.floor(anuncio.preco * ambiente.COMISSAO),
        tarifa: tarifaFixa(anuncio.preco, anuncio.modalidadeEntrega as ModalidadeEntrega),
      },
    });
  } catch (erro) {
    return next(erro);
  }
});

const anuncioEsquema = z.object({
  titulo: z.string().min(4).max(120),
  descricao: z.string().min(10).max(4000),
  preco: z
    .number()
    .int()
    .min(VALOR_MINIMO_VENDA, `o valor mínimo de venda é R$ ${(VALOR_MINIMO_VENDA / 100).toFixed(2)}`),
  precoOriginal: z.number().int().positive().optional(),
  condicao: z.enum(['NOVO', 'SEMINOVO', 'USADO']),
  categoriaId: z.string().optional(),
  marca: z.string().max(60).optional(),
  tamanho: z.string().max(20).optional(),
  cor: z.string().max(30).optional(),
  // os limites por campo valem só na modalidade PLATAFORMA; a checagem
  // completa é feita por `validarMedidas` logo abaixo, que conhece a modalidade
  pesoG: z.number().int().positive(),
  comprimentoCm: z.number().int().positive(),
  larguraCm: z.number().int().positive(),
  alturaCm: z.number().int().positive(),
  /** Quem entrega. Define a taxa e se há limite de peso/tamanho. */
  modalidadeEntrega: z.enum(['PLATAFORMA', 'VENDEDOR']).default('PLATAFORMA'),
  /** Onde o entregador busca o produto. Obrigatório na modalidade PLATAFORMA. */
  enderecoColetaId: z.string().optional(),
  fotos: z.array(z.string().url()).min(1, 'envie pelo menos uma foto').max(10),
});

/** POST /anuncios — publica um anúncio. */
rotasAnuncios.post('/', exigirLogin, async (req, res, next) => {
  try {
    const dados = anuncioEsquema.parse(req.body);

    // os limites de 20 kg / 100 cm valem só quando quem entrega é a plataforma
    const validacao = validarMedidas(dados, dados.modalidadeEntrega);
    if (!validacao.valido) {
      throw erroDeValidacao(
        dados.modalidadeEntrega === 'PLATAFORMA'
          ? `${validacao.erros.join(' ')} Você pode anunciar escolhendo entregar por conta própria.`
          : validacao.erros.join(' '),
        validacao.erros,
      );
    }

    // sem endereço de coleta o entregador não tem onde buscar o produto
    if (dados.modalidadeEntrega === 'PLATAFORMA' && !dados.enderecoColetaId) {
      throw erroDeValidacao(
        'Escolha o endereço onde o entregador vai buscar o produto.',
      );
    }
    if (dados.enderecoColetaId) {
      const endereco = await prisma.endereco.findFirst({
        where: { id: dados.enderecoColetaId, usuarioId: req.sessao!.usuarioId },
      });
      if (!endereco) throw erroDeValidacao('Endereço de coleta não encontrado.');
    }

    const { fotos, ...resto } = dados;
    const anuncio = await prisma.anuncio.create({
      data: {
        ...resto,
        vendedorId: req.sessao!.usuarioId,
        estado: 'ATIVO',
        fotos: { create: fotos.map((url, ordem) => ({ url, ordem })) },
      },
      include: { fotos: true },
    });

    return res.status(201).json(anuncio);
  } catch (erro) {
    return next(erro);
  }
});

/** PATCH /anuncios/:id — edita o próprio anúncio. */
rotasAnuncios.patch('/:id', exigirLogin, async (req, res, next) => {
  try {
    const anuncio = await prisma.anuncio.findUnique({ where: { id: req.params.id } });
    if (!anuncio) throw naoEncontrado('Anúncio não encontrado.');
    if (anuncio.vendedorId !== req.sessao!.usuarioId) throw semPermissao();

    const dados = anuncioEsquema.partial().omit({ fotos: true }).parse(req.body);
    const medidas = {
      pesoG: dados.pesoG ?? anuncio.pesoG,
      comprimentoCm: dados.comprimentoCm ?? anuncio.comprimentoCm,
      larguraCm: dados.larguraCm ?? anuncio.larguraCm,
      alturaCm: dados.alturaCm ?? anuncio.alturaCm,
    };
    const modalidade = (dados.modalidadeEntrega ??
      anuncio.modalidadeEntrega) as ModalidadeEntrega;

    const validacao = validarMedidas(medidas, modalidade);
    if (!validacao.valido) throw erroDeValidacao(validacao.erros.join(' '), validacao.erros);

    const atualizado = await prisma.anuncio.update({
      where: { id: anuncio.id },
      data: dados,
    });
    return res.json(atualizado);
  } catch (erro) {
    return next(erro);
  }
});

/** DELETE /anuncios/:id — tira o anúncio do ar. */
rotasAnuncios.delete('/:id', exigirLogin, async (req, res, next) => {
  try {
    const anuncio = await prisma.anuncio.findUnique({ where: { id: req.params.id } });
    if (!anuncio) throw naoEncontrado('Anúncio não encontrado.');
    if (anuncio.vendedorId !== req.sessao!.usuarioId) throw semPermissao();

    await prisma.anuncio.update({
      where: { id: anuncio.id },
      data: { estado: 'REMOVIDO' },
    });
    return res.status(204).end();
  } catch (erro) {
    return next(erro);
  }
});

/** GET /anuncios/meus/lista — a lojinha do vendedor logado. */
rotasAnuncios.get('/meus/lista', exigirLogin, async (req, res, next) => {
  try {
    const anuncios = await prisma.anuncio.findMany({
      where: { vendedorId: req.sessao!.usuarioId, estado: { not: 'REMOVIDO' } },
      orderBy: { criadoEm: 'desc' },
      include: { fotos: { take: 1, orderBy: { ordem: 'asc' } } },
    });
    return res.json({ itens: anuncios });
  } catch (erro) {
    return next(erro);
  }
});
