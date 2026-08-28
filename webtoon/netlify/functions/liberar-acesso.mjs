// POST /api/liberar-acesso   { email, codigo }
//
// Onde o comprador troca o código que você mandou pelo crachá que abre os
// capítulos pagos. Serve tanto para a primeira liberação quanto para
// recuperar o acesso em outro aparelho — o código continua valendo.

import { criarCracha } from './_lib/acesso.mjs';
import { codigoConfere, normalizarEmail } from './_lib/codigos.mjs';

// Uma pausa curta em toda tentativa: quem quisesse descobrir um código no
// chute precisaria de mais tempo do que vale a pena.
const espera = (ms) => new Promise((r) => setTimeout(r, ms));

export default async (req) => {
  if (req.method !== 'POST') {
    return Response.json({ erro: 'Use POST.' }, { status: 405 });
  }

  let corpo;
  try {
    corpo = await req.json();
  } catch {
    return Response.json({ erro: 'Corpo inválido.' }, { status: 400 });
  }

  const email = normalizarEmail(corpo?.email);
  const codigo = String(corpo?.codigo || '');

  if (!email || !codigo) {
    return Response.json({ erro: 'Informe o e-mail e o código de acesso.' }, { status: 400 });
  }

  try {
    await espera(600);

    if (!codigoConfere(email, codigo)) {
      return Response.json(
        { erro: 'Código não confere com esse e-mail. Confira os dois e tente de novo.' },
        { status: 403 },
      );
    }

    return Response.json(
      { cracha: criarCracha(email) },
      { headers: { 'Cache-Control': 'no-store' } },
    );
  } catch (erro) {
    return Response.json({ erro: erro.message }, { status: 500 });
  }
};

export const config = { path: '/api/liberar-acesso' };
