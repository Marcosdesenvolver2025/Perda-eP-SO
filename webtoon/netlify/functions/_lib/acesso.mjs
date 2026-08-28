// Crachá de acesso: prova, sem banco de dados, que aquele visitante pagou.
//
// O crachá é um texto assinado com HMAC-SHA256 usando o segredo do site. O
// navegador guarda esse texto e devolve a cada página paga que pede. Como a
// assinatura depende do segredo — que só existe no servidor — ninguém fabrica
// um crachá válido no console do navegador.
//
// Formato:  <corpo em base64url>.<assinatura em base64url>
// Corpo:    {"p":"<id do pagamento>","exp":<segundos epoch>}

import { createHmac, timingSafeEqual } from 'node:crypto';

const DIAS = 60 * 60 * 24;

export const DURACAO_PADRAO = 365 * DIAS; // 1 ano; o comprador renova entrando de novo

function segredo() {
  const s = process.env.SEGREDO_ACESSO;
  if (!s || s.length < 16) {
    throw new Error(
      'SEGREDO_ACESSO não está definida (ou é curta demais). ' +
        'Gere uma com `openssl rand -hex 32` e cadastre nas variáveis de ambiente do Netlify.',
    );
  }
  return s;
}

const paraBase64Url = (buf) =>
  Buffer.from(buf).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

const deBase64Url = (txt) => Buffer.from(txt.replace(/-/g, '+').replace(/_/g, '/'), 'base64');

function assinar(corpo) {
  return paraBase64Url(createHmac('sha256', segredo()).update(corpo).digest());
}

export function criarCracha(idPagamento, duracaoSegundos = DURACAO_PADRAO) {
  const corpo = paraBase64Url(
    JSON.stringify({ p: String(idPagamento), exp: Math.floor(Date.now() / 1000) + duracaoSegundos }),
  );
  return `${corpo}.${assinar(corpo)}`;
}

// Devolve { valido, motivo, pagamento }. Nunca lança por crachá malformado:
// crachá inválido é o caso comum (visitante que ainda não comprou).
export function conferirCracha(cracha) {
  if (typeof cracha !== 'string' || !cracha.includes('.')) {
    return { valido: false, motivo: 'ausente' };
  }

  const [corpo, assinatura] = cracha.split('.');
  let esperada;
  try {
    esperada = assinar(corpo);
  } catch (erro) {
    return { valido: false, motivo: 'configuracao', detalhe: erro.message };
  }

  // Comparação em tempo constante: evita descobrir a assinatura por tentativa.
  const a = Buffer.from(assinatura || '');
  const b = Buffer.from(esperada);
  if (a.length !== b.length || !timingSafeEqual(a, b)) {
    return { valido: false, motivo: 'assinatura' };
  }

  let dados;
  try {
    dados = JSON.parse(deBase64Url(corpo).toString('utf8'));
  } catch {
    return { valido: false, motivo: 'corpo' };
  }

  if (!dados?.exp || dados.exp < Math.floor(Date.now() / 1000)) {
    return { valido: false, motivo: 'expirado' };
  }

  return { valido: true, pagamento: dados.p };
}

// O crachá chega pelo cabeçalho Authorization (fetch) ou pela query `?t=`
// (tags <img>, que não mandam cabeçalho).
export function crachaDaRequisicao(req) {
  const cabecalho = req.headers.get('authorization') || '';
  if (cabecalho.toLowerCase().startsWith('bearer ')) return cabecalho.slice(7).trim();
  return new URL(req.url).searchParams.get('t') || '';
}
