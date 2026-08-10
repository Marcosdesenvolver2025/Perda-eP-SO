/**
 * Login com conta Google e dados da conta logada.
 */

import { Router } from 'express';
import { z } from 'zod';

import { ambiente } from '../ambiente';
import { verificarIdToken } from '../integracoes/google';
import { emitirToken, exigirLogin } from '../middlewares/autenticacao';
import { prisma } from '../prisma';

export const rotasAutenticacao = Router();

const entrarEsquema = z.object({
  idToken: z.string().min(20, 'token do Google ausente'),
});

/**
 * POST /auth/google
 * O app manda o id_token do Google Sign-In; devolvemos o token de sessão.
 * Primeiro login já cria a conta (é o mesmo fluxo do vídeo: entra com Google
 * e as compras e vendas ficam salvas na conta).
 */
rotasAutenticacao.post('/google', async (req, res, next) => {
  try {
    const { idToken } = entrarEsquema.parse(req.body);
    const conta = await verificarIdToken(idToken);

    const usuario = await prisma.usuario.upsert({
      where: { googleId: conta.googleId },
      update: {
        nome: conta.nome,
        fotoUrl: conta.fotoUrl ?? null,
        // reativa conta que havia sido excluída e voltou a entrar
        excluidoEm: null,
      },
      create: {
        googleId: conta.googleId,
        email: conta.email,
        nome: conta.nome,
        fotoUrl: conta.fotoUrl ?? null,
        cidade: ambiente.CIDADE,
        uf: ambiente.UF,
      },
    });

    const token = emitirToken({ usuarioId: usuario.id, papel: usuario.papel });

    return res.json({
      token,
      usuario: {
        id: usuario.id,
        nome: usuario.nome,
        email: usuario.email,
        fotoUrl: usuario.fotoUrl,
        papel: usuario.papel,
        apelidoLoja: usuario.apelidoLoja,
        // o app usa isto para pedir o que falta antes da primeira compra/venda
        precisaCompletarCadastro: !usuario.cpf || !usuario.telefone,
      },
    });
  } catch (erro) {
    return next(erro);
  }
});

/** GET /auth/eu — perfil da sessão atual. */
rotasAutenticacao.get('/eu', exigirLogin, async (req, res, next) => {
  try {
    const usuario = await prisma.usuario.findUnique({
      where: { id: req.sessao!.usuarioId },
      include: { recebedor: { select: { estado: true } } },
    });
    if (!usuario) return res.status(404).json({ erro: { mensagem: 'Conta não encontrada.' } });

    return res.json({
      id: usuario.id,
      nome: usuario.nome,
      email: usuario.email,
      fotoUrl: usuario.fotoUrl,
      telefone: usuario.telefone,
      papel: usuario.papel,
      apelidoLoja: usuario.apelidoLoja,
      bioLoja: usuario.bioLoja,
      bairro: usuario.bairro,
      cidade: usuario.cidade,
      recebedor: usuario.recebedor?.estado ?? null,
      precisaCompletarCadastro: !usuario.cpf || !usuario.telefone,
    });
  } catch (erro) {
    return next(erro);
  }
});

const perfilEsquema = z.object({
  nome: z.string().min(2).max(80).optional(),
  telefone: z.string().min(10).max(15).optional(),
  cpf: z.string().regex(/^\d{11}$/, 'CPF deve ter 11 dígitos').optional(),
  bairro: z.string().max(80).optional(),
  apelidoLoja: z
    .string()
    .min(3)
    .max(30)
    .regex(/^[a-z0-9._-]+$/, 'use letras minúsculas, números, ponto, hífen ou _')
    .optional(),
  bioLoja: z.string().max(280).optional(),
});

/** PATCH /auth/eu — completa ou edita o cadastro. */
rotasAutenticacao.patch('/eu', exigirLogin, async (req, res, next) => {
  try {
    const dados = perfilEsquema.parse(req.body);
    const usuario = await prisma.usuario.update({
      where: { id: req.sessao!.usuarioId },
      data: dados,
    });
    return res.json({ id: usuario.id, nome: usuario.nome, apelidoLoja: usuario.apelidoLoja });
  } catch (erro) {
    return next(erro);
  }
});
