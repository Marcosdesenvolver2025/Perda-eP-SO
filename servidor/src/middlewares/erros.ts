import type { NextFunction, Request, Response } from 'express';
import { ZodError } from 'zod';

import { ErroDaApi } from '../erros';
import { emProducao } from '../ambiente';
import { log } from '../log';

/** Envelope único de erro: o app sempre lê `{ erro: { codigo, mensagem } }`. */
export function tratarErros(
  erro: unknown,
  _req: Request,
  res: Response,
  _next: NextFunction,
) {
  if (erro instanceof ZodError) {
    return res.status(422).json({
      erro: {
        codigo: 'validacao',
        mensagem: 'Confira os dados enviados.',
        detalhes: erro.issues.map((i) => ({
          campo: i.path.join('.'),
          mensagem: i.message,
        })),
      },
    });
  }

  if (erro instanceof ErroDaApi) {
    // erro de integração merece log completo: é problema nosso, não do usuário
    if (erro.status >= 500) log.error({ erro }, 'falha de integração');
    return res.status(erro.status).json({
      erro: {
        codigo: erro.codigo,
        mensagem: erro.message,
        ...(emProducao ? {} : { detalhes: erro.detalhes }),
      },
    });
  }

  log.error({ erro }, 'erro não tratado');
  return res.status(500).json({
    erro: {
      codigo: 'interno',
      mensagem: 'Deu problema aqui do nosso lado. Tente de novo em instantes.',
    },
  });
}

export function rotaNaoEncontrada(_req: Request, res: Response) {
  return res
    .status(404)
    .json({ erro: { codigo: 'nao_encontrado', mensagem: 'Rota inexistente.' } });
}
