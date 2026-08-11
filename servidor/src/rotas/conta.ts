/**
 * Endereços, notificações e EXCLUSÃO DE CONTA.
 *
 * A exclusão de conta é obrigatória para publicar na Google Play: o app precisa
 * de um caminho dentro dele e de uma página na web para pedir a exclusão
 * (ver `loja/exclusao-de-conta.html`).
 */

import { Router } from 'express';
import { z } from 'zod';

import { ambiente } from '../ambiente';
import { conflito, naoEncontrado } from '../erros';
import { exigirLogin } from '../middlewares/autenticacao';
import { log } from '../log';
import { prisma } from '../prisma';

export const rotasConta = Router();

rotasConta.use(exigirLogin);

const enderecoEsquema = z.object({
  apelido: z.string().max(30).optional(),
  cep: z.string().regex(/^\d{8}$/, 'CEP com 8 dígitos, só números'),
  logradouro: z.string().min(3).max(120),
  numero: z.string().min(1).max(10),
  complemento: z.string().max(60).optional(),
  bairro: z.string().min(2).max(60),
  referencia: z.string().max(120).optional(),
  principal: z.boolean().default(false),
});

/** GET /conta/enderecos */
rotasConta.get('/enderecos', async (req, res, next) => {
  try {
    const enderecos = await prisma.endereco.findMany({
      where: { usuarioId: req.sessao!.usuarioId },
      orderBy: [{ principal: 'desc' }, { criadoEm: 'desc' }],
    });
    return res.json({ itens: enderecos });
  } catch (erro) {
    return next(erro);
  }
});

/** POST /conta/enderecos — só dentro da cidade atendida. */
rotasConta.post('/enderecos', async (req, res, next) => {
  try {
    const dados = enderecoEsquema.parse(req.body);

    if (dados.principal) {
      await prisma.endereco.updateMany({
        where: { usuarioId: req.sessao!.usuarioId },
        data: { principal: false },
      });
    }

    const endereco = await prisma.endereco.create({
      data: {
        ...dados,
        usuarioId: req.sessao!.usuarioId,
        cidade: ambiente.CIDADE,
        uf: ambiente.UF,
      },
    });
    return res.status(201).json(endereco);
  } catch (erro) {
    return next(erro);
  }
});

/** DELETE /conta/enderecos/:id */
rotasConta.delete('/enderecos/:id', async (req, res, next) => {
  try {
    const resultado = await prisma.endereco.deleteMany({
      where: { id: req.params.id, usuarioId: req.sessao!.usuarioId },
    });
    if (resultado.count === 0) throw naoEncontrado('Endereço não encontrado.');
    return res.status(204).end();
  } catch (erro) {
    return next(erro);
  }
});

/** POST /conta/dispositivos — registra o token de push do aparelho. */
rotasConta.post('/dispositivos', async (req, res, next) => {
  try {
    const { token } = z.object({ token: z.string().min(10) }).parse(req.body);
    await prisma.dispositivo.upsert({
      where: { token },
      update: { usuarioId: req.sessao!.usuarioId },
      create: { token, usuarioId: req.sessao!.usuarioId },
    });
    return res.status(204).end();
  } catch (erro) {
    return next(erro);
  }
});

/**
 * DELETE /conta
 * Exclusão de conta exigida pela Google Play.
 *
 * O que apagamos na hora: nome, e-mail, foto, telefone, CPF, endereços,
 * mensagens e tokens de push. O que fica: os pedidos, sem dado pessoal, porque
 * a legislação fiscal exige guardar o registro das vendas — e um pedido em
 * andamento tem dinheiro e prazo de devolução envolvidos.
 */
rotasConta.delete('/', async (req, res, next) => {
  try {
    const usuarioId = req.sessao!.usuarioId;

    const emAndamento = await prisma.pedido.count({
      where: {
        OR: [{ compradorId: usuarioId }, { vendedorId: usuarioId }],
        estado: {
          in: [
            'AGUARDANDO_PAGAMENTO',
            'PAGO',
            'AGUARDANDO_AGENDAMENTO_DE_COLETA',
            'A_CAMINHO_DA_COLETA',
            'PRODUTO_COLETADO',
            'EM_ROTA_PARA_ENTREGA',
            'ENTREGUE',
            'DEVOLUCAO_SOLICITADA',
            'DEVOLUCAO_APROVADA',
            'DEVOLUCAO_EM_TRANSITO',
          ],
        },
      },
    });

    if (emAndamento > 0) {
      throw conflito(
        `Você tem ${emAndamento} pedido(s) em andamento. Conclua ou cancele antes de excluir a conta — assim ninguém fica sem o dinheiro ou sem o produto.`,
      );
    }

    const anonimo = `excluido-${usuarioId.slice(-8)}`;

    await prisma.$transaction([
      prisma.anuncio.updateMany({
        where: { vendedorId: usuarioId, estado: { in: ['ATIVO', 'PAUSADO', 'RASCUNHO'] } },
        data: { estado: 'REMOVIDO' },
      }),
      prisma.endereco.deleteMany({ where: { usuarioId } }),
      prisma.dispositivo.deleteMany({ where: { usuarioId } }),
      prisma.mensagem.deleteMany({ where: { autorId: usuarioId } }),
      prisma.favorito.deleteMany({ where: { usuarioId } }),
      prisma.usuario.update({
        where: { id: usuarioId },
        data: {
          nome: 'Conta excluída',
          email: `${anonimo}@excluido.vendasitinga.app`,
          googleId: anonimo,
          fotoUrl: null,
          telefone: null,
          cpf: null,
          bairro: null,
          apelidoLoja: null,
          bioLoja: null,
          excluidoEm: new Date(),
        },
      }),
    ]);

    log.info({ usuarioId }, 'conta excluída a pedido do usuário');
    return res.status(204).end();
  } catch (erro) {
    return next(erro);
  }
});
