/**
 * Sessão do app: JWT assinado por nós, emitido depois do login com Google.
 */

import type { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';

import { ambiente } from '../ambiente';
import { naoAutenticado, semPermissao } from '../erros';

export type Papel = 'CLIENTE' | 'ENTREGADOR' | 'ADMIN';

export interface Sessao {
  usuarioId: string;
  papel: Papel;
}

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      sessao?: Sessao;
    }
  }
}

export function emitirToken(sessao: Sessao): string {
  return jwt.sign(sessao, ambiente.JWT_SEGREDO, {
    expiresIn: ambiente.JWT_EXPIRACAO,
  } as jwt.SignOptions);
}

/** Exige usuário autenticado. */
export function exigirLogin(req: Request, _res: Response, next: NextFunction) {
  const cabecalho = req.headers.authorization;
  if (!cabecalho?.startsWith('Bearer ')) {
    return next(naoAutenticado());
  }
  try {
    req.sessao = jwt.verify(
      cabecalho.slice('Bearer '.length),
      ambiente.JWT_SEGREDO,
    ) as Sessao;
    return next();
  } catch {
    return next(naoAutenticado('Sua sessão expirou. Entre de novo.'));
  }
}

/** Exige um dos papéis informados (ex.: só entregador, só admin). */
export function exigirPapel(...papeis: Papel[]) {
  return (req: Request, _res: Response, next: NextFunction) => {
    if (!req.sessao) return next(naoAutenticado());
    if (!papeis.includes(req.sessao.papel)) {
      return next(semPermissao('Esta área é restrita.'));
    }
    return next();
  };
}

/** Lê a sessão se houver, mas não bloqueia (usado na vitrine pública). */
export function loginOpcional(req: Request, _res: Response, next: NextFunction) {
  const cabecalho = req.headers.authorization;
  if (cabecalho?.startsWith('Bearer ')) {
    try {
      req.sessao = jwt.verify(
        cabecalho.slice('Bearer '.length),
        ambiente.JWT_SEGREDO,
      ) as Sessao;
    } catch {
      // token inválido na vitrine: segue como visitante
    }
  }
  return next();
}
