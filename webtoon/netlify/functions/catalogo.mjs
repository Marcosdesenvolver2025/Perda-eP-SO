// GET /api/catalogo
//
// Lista a obra e os capítulos. Com um crachá válido, os capítulos pagos vêm
// marcados como liberados. Em nenhum dos casos esta resposta traz o endereço
// das imagens pagas: elas só saem pela função `pagina`.

import { conferirCracha, crachaDaRequisicao } from './_lib/acesso.mjs';
import { lerCatalogo, catalogoPublico } from './_lib/catalogo.mjs';

export default async (req) => {
  try {
    const catalogo = await lerCatalogo();
    const cracha = conferirCracha(crachaDaRequisicao(req));

    return Response.json(catalogoPublico(catalogo, cracha.valido), {
      headers: { 'Cache-Control': 'no-store' },
    });
  } catch (erro) {
    return Response.json({ erro: erro.message }, { status: 500 });
  }
};

export const config = { path: '/api/catalogo' };
