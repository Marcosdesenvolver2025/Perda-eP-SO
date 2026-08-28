// GET /api/conferir-pagamento?id=<id do pagamento>
//
// Só faz sentido com MP_ACCESS_TOKEN definida. Com a chave Pix avulsa não há
// o que consultar — o site não fica sabendo do pagamento — e aqui devolvemos
// `automatica: false` para a tela de compra explicar isso ao comprador em vez
// de ficar girando uma espera que nunca termina.

import { criarCracha } from './_lib/acesso.mjs';
import { confirmacaoAutomatica, consultarNoMercadoPago } from './_lib/pix.mjs';

export default async (req) => {
  if (!confirmacaoAutomatica()) {
    return Response.json(
      { automatica: false, pago: false },
      { headers: { 'Cache-Control': 'no-store' } },
    );
  }

  const id = new URL(req.url).searchParams.get('id');
  if (!id) {
    return Response.json({ erro: 'Informe o id do pagamento.' }, { status: 400 });
  }

  try {
    const pagamento = await consultarNoMercadoPago(id);
    if (!pagamento.pago) {
      return Response.json(
        { automatica: true, pago: false, situacao: pagamento.situacao },
        { headers: { 'Cache-Control': 'no-store' } },
      );
    }
    return Response.json(
      { automatica: true, pago: true, cracha: criarCracha(pagamento.email || id) },
      { headers: { 'Cache-Control': 'no-store' } },
    );
  } catch (erro) {
    return Response.json({ erro: erro.message }, { status: 502 });
  }
};

export const config = { path: '/api/conferir-pagamento' };
