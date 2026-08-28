// POST /api/cobranca   { email }
//
// Devolve o Pix "copia e cola" da compra, o identificador do pedido e por
// onde o comprador manda o comprovante. Não libera nada: a liberação é sua,
// depois de ver o dinheiro cair.

import { lerCatalogo } from './_lib/catalogo.mjs';
import { montarCopiaECola, novoIdentificador, confirmacaoAutomatica } from './_lib/pix.mjs';
import { normalizarEmail } from './_lib/codigos.mjs';

const EMAIL_VALIDO = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

// O catálogo nasce com contato de exemplo. Enquanto ele não for trocado pelo
// seu de verdade, é melhor não mandar nada para a tela do que oferecer ao
// comprador um botão que abre uma conversa que não existe.
const PLACEHOLDERS = [/exemplo/i, /^5533900000000$/, /^0+$/];

function contatoUtil(contato = {}) {
  const util = {};
  for (const [canal, valor] of Object.entries(contato)) {
    const texto = String(valor || '').trim();
    if (texto && !PLACEHOLDERS.some((p) => p.test(texto))) util[canal] = texto;
  }
  return util;
}

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
  if (!EMAIL_VALIDO.test(email)) {
    return Response.json({ erro: 'Informe um e-mail válido.' }, { status: 400 });
  }

  try {
    const catalogo = await lerCatalogo();
    const identificador = novoIdentificador();

    const copiaECola = montarCopiaECola({
      chave: catalogo.pix.chave,
      nome: catalogo.pix.nome,
      cidade: catalogo.pix.cidade,
      centavos: catalogo.preco.centavos,
      identificador,
    });

    return Response.json(
      {
        identificador,
        copiaECola,
        valor: catalogo.preco.rotulo,
        contato: contatoUtil(catalogo.pix.contato),
        confirmacaoAutomatica: confirmacaoAutomatica(),
      },
      { headers: { 'Cache-Control': 'no-store' } },
    );
  } catch (erro) {
    return Response.json({ erro: erro.message }, { status: 500 });
  }
};

export const config = { path: '/api/cobranca' };
