/**
 * Login com conta Google.
 *
 * O app faz o Sign-In pelo SDK nativo e manda o `id_token` para cá. Aqui o
 * token é validado contra as chaves públicas do Google — é o que garante que
 * o e-mail recebido é mesmo daquela pessoa.
 */

import { OAuth2Client } from 'google-auth-library';

import { ambiente } from '../ambiente';
import { naoAutenticado } from '../erros';

const cliente = new OAuth2Client();

/** IDs aceitos: o do app Android e o Web (usado para gerar o id_token). */
const publicosAceitos = [
  ambiente.GOOGLE_CLIENT_ID_WEB,
  ambiente.GOOGLE_CLIENT_ID_ANDROID,
].filter((v): v is string => Boolean(v));

export interface ContaGoogle {
  googleId: string;
  email: string;
  emailVerificado: boolean;
  nome: string;
  fotoUrl?: string;
}

export async function verificarIdToken(idToken: string): Promise<ContaGoogle> {
  let payload;
  try {
    const ticket = await cliente.verifyIdToken({
      idToken,
      audience: publicosAceitos,
    });
    payload = ticket.getPayload();
  } catch {
    throw naoAutenticado('Não foi possível validar seu login do Google.');
  }

  if (!payload?.sub || !payload.email) {
    throw naoAutenticado('Login do Google incompleto.');
  }
  if (!payload.email_verified) {
    throw naoAutenticado('Confirme seu e-mail no Google antes de entrar.');
  }

  return {
    googleId: payload.sub,
    email: payload.email.toLowerCase(),
    emailVerificado: true,
    nome: payload.name ?? payload.email.split('@')[0]!,
    fotoUrl: payload.picture,
  };
}
