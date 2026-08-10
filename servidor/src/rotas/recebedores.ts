/**
 * Cadastro de recebedor na pagar.me.
 *
 * Sem isso o vendedor (ou entregador) não recebe a parte dele no split.
 * A conta é criada com transferência automática desligada de propósito: o
 * dinheiro fica retido até vencer a janela de 7 dias.
 */

import { Router } from 'express';
import { z } from 'zod';

import { conflito, naoEncontrado } from '../erros';
import * as pagarme from '../integracoes/pagarme';
import { exigirLogin } from '../middlewares/autenticacao';
import { prisma } from '../prisma';

export const rotasRecebedores = Router();

rotasRecebedores.use(exigirLogin);

const cadastroEsquema = z.object({
  documento: z
    .string()
    .transform((v) => v.replace(/\D/g, ''))
    .refine((v) => v.length === 11 || v.length === 14, 'informe um CPF ou CNPJ válido'),
  tipo: z.enum(['individual', 'company']),
  titular: z.string().min(3).max(80),
  banco: z.object({
    codigo: z.string().regex(/^\d{3}$/, 'código do banco tem 3 dígitos'),
    agencia: z.string().regex(/^\d{1,5}$/),
    agenciaDigito: z.string().max(1).optional(),
    conta: z.string().regex(/^\d{1,13}$/),
    contaDigito: z.string().min(1).max(2),
    tipoConta: z.enum(['checking', 'savings']).default('checking'),
  }),
  telefone: z
    .object({ ddd: z.string().regex(/^\d{2}$/), numero: z.string().regex(/^\d{8,9}$/) })
    .optional(),
});

/** GET /recebedores/eu — situação do meu cadastro de recebimento. */
rotasRecebedores.get('/eu', async (req, res, next) => {
  try {
    const recebedor = await prisma.recebedor.findUnique({
      where: { usuarioId: req.sessao!.usuarioId },
    });
    if (!recebedor) return res.json({ cadastrado: false });

    return res.json({
      cadastrado: true,
      estado: recebedor.estado,
      banco: recebedor.bancoCodigo,
      conta: recebedor.conta ? `••••${recebedor.conta.slice(-3)}` : null,
    });
  } catch (erro) {
    return next(erro);
  }
});

/** POST /recebedores — cria o recebedor na pagar.me. */
rotasRecebedores.post('/', async (req, res, next) => {
  try {
    const dados = cadastroEsquema.parse(req.body);

    const usuario = await prisma.usuario.findUnique({
      where: { id: req.sessao!.usuarioId },
      include: { recebedor: true },
    });
    if (!usuario) throw naoEncontrado('Conta não encontrada.');
    if (usuario.recebedor) {
      throw conflito('Você já tem uma conta de recebimento cadastrada.');
    }

    const resposta = await pagarme.criarRecebedor(
      {
        nome: dados.titular,
        email: usuario.email,
        documento: dados.documento,
        tipo: dados.tipo,
        telefone: dados.telefone,
        banco: {
          bancoCodigo: dados.banco.codigo,
          agencia: dados.banco.agencia,
          agenciaDigito: dados.banco.agenciaDigito,
          conta: dados.banco.conta,
          contaDigito: dados.banco.contaDigito,
          tipoConta: dados.banco.tipoConta,
          titular: dados.titular,
          documentoTitular: dados.documento,
        },
      },
      `recebedor-${usuario.id}`,
    );

    const recebedor = await prisma.recebedor.create({
      data: {
        usuarioId: usuario.id,
        recipientId: resposta.id,
        // a pagar.me analisa o cadastro; só liberamos venda quando fica ativo
        estado: resposta.status === 'active' ? 'ATIVO' : 'PENDENTE',
        documento: dados.documento,
        tipo: dados.tipo,
        bancoCodigo: dados.banco.codigo,
        agencia: dados.banco.agencia,
        conta: dados.banco.conta,
        transferenciaAutomatica: false,
      },
    });

    return res.status(201).json({ estado: recebedor.estado });
  } catch (erro) {
    return next(erro);
  }
});

/** POST /recebedores/eu/sincronizar — reconsulta a situação na pagar.me. */
rotasRecebedores.post('/eu/sincronizar', async (req, res, next) => {
  try {
    const recebedor = await prisma.recebedor.findUnique({
      where: { usuarioId: req.sessao!.usuarioId },
    });
    if (!recebedor) throw naoEncontrado('Você ainda não cadastrou conta de recebimento.');

    const resposta = await pagarme.consultarRecebedor(recebedor.recipientId);
    const estado =
      resposta.status === 'active'
        ? 'ATIVO'
        : resposta.status === 'refused'
          ? 'RECUSADO'
          : resposta.status === 'blocked'
            ? 'BLOQUEADO'
            : 'PENDENTE';

    const atualizado = await prisma.recebedor.update({
      where: { id: recebedor.id },
      data: { estado },
    });
    return res.json({ estado: atualizado.estado });
  } catch (erro) {
    return next(erro);
  }
});

/** GET /recebedores/eu/saldo — quanto está retido e quanto já liberou. */
rotasRecebedores.get('/eu/saldo', async (req, res, next) => {
  try {
    const recebedor = await prisma.recebedor.findUnique({
      where: { usuarioId: req.sessao!.usuarioId },
    });
    if (!recebedor) throw naoEncontrado('Você ainda não cadastrou conta de recebimento.');

    const saldo = await pagarme.saldoDoRecebedor(recebedor.recipientId);
    return res.json({
      disponivel: saldo.available_amount,
      aguardando: saldo.waiting_funds_amount,
    });
  } catch (erro) {
    return next(erro);
  }
});
