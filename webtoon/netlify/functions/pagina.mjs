// GET /api/pagina?cap=6&p=3&t=<crachá>
//
// A única porta de saída das páginas pagas. A imagem não tem endereço fixo na
// internet: ela é lida do disco da função e devolvida aqui, e só depois de o
// crachá passar pela conferência.
//
// Como <img src> não manda cabeçalho, o crachá vem na query. Por isso a
// resposta é `private, no-store`: nem a CDN nem um proxy guardam cópia.

import { conferirCracha, crachaDaRequisicao } from './_lib/acesso.mjs';
import { acharCapitulo, lerPaginaPaga } from './_lib/catalogo.mjs';

export default async (req) => {
  const url = new URL(req.url);
  const numeroCap = Number(url.searchParams.get('cap'));
  const numeroPag = Number(url.searchParams.get('p'));

  if (!Number.isInteger(numeroCap) || !Number.isInteger(numeroPag) || numeroPag < 1) {
    return Response.json({ erro: 'Parâmetros cap e p são obrigatórios.' }, { status: 400 });
  }

  try {
    const capitulo = await acharCapitulo(numeroCap);
    if (!capitulo) {
      return Response.json({ erro: 'Capítulo não existe.' }, { status: 404 });
    }
    if (numeroPag > capitulo.paginas) {
      return Response.json({ erro: 'Página não existe.' }, { status: 404 });
    }

    // Capítulo grátis não passa por aqui: o arquivo está em public/paginas/.
    if (!capitulo.pago) {
      return Response.redirect(
        new URL(
          `/paginas/cap-${String(numeroCap).padStart(2, '0')}/${String(numeroPag).padStart(3, '0')}.svg`,
          url.origin,
        ),
        302,
      );
    }

    const cracha = conferirCracha(crachaDaRequisicao(req));
    if (!cracha.valido) {
      const status = cracha.motivo === 'configuracao' ? 500 : 402;
      return Response.json(
        { erro: 'Capítulo pago. É preciso comprar o acesso.', motivo: cracha.motivo },
        { status },
      );
    }

    const arquivo = await lerPaginaPaga(numeroCap, numeroPag);
    if (!arquivo) {
      return Response.json(
        { erro: `Arquivo da página ${numeroPag} do capítulo ${numeroCap} não foi encontrado.` },
        { status: 404 },
      );
    }

    return new Response(arquivo.bytes, {
      headers: {
        'Content-Type': arquivo.tipo,
        'Cache-Control': 'private, no-store',
        'X-Content-Type-Options': 'nosniff',
      },
    });
  } catch (erro) {
    return Response.json({ erro: erro.message }, { status: 500 });
  }
};

export const config = { path: '/api/pagina' };
