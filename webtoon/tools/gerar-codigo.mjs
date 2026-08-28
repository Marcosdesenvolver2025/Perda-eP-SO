// Gera o código de acesso de um comprador.
//
// É o que você roda depois de ver o Pix cair no aplicativo do banco:
//
//     SEGREDO_ACESSO=<o mesmo segredo do site> node tools/gerar-codigo.mjs comprador@exemplo.com
//
// O código sai amarrado àquele e-mail: só abre os capítulos para quem entrar
// com esse mesmo e-mail em /liberar.html.
//
// O SEGREDO_ACESSO tem que ser IGUAL ao que está nas variáveis de ambiente do
// Netlify. Se for outro, o código sai errado e o site recusa. Para não digitar
// toda vez, dá para guardá-lo em webtoon/.env (esse arquivo é ignorado pelo
// git de propósito — ele é a chave da sua loja).

import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..');

// Lê webtoon/.env quando a variável não veio pelo ambiente.
if (!process.env.SEGREDO_ACESSO) {
  try {
    const env = await readFile(join(RAIZ, '.env'), 'utf8');
    for (const linha of env.split('\n')) {
      const casa = linha.match(/^\s*SEGREDO_ACESSO\s*=\s*(.+?)\s*$/);
      if (casa) process.env.SEGREDO_ACESSO = casa[1].replace(/^["']|["']$/g, '');
    }
  } catch {
    // sem .env, seguimos e o erro abaixo explica o que fazer
  }
}

const email = process.argv[2];

if (!email || !email.includes('@')) {
  console.error('Uso: node tools/gerar-codigo.mjs comprador@exemplo.com');
  process.exit(1);
}

if (!process.env.SEGREDO_ACESSO) {
  console.error(
    'Falta o SEGREDO_ACESSO. Use o mesmo valor cadastrado no Netlify:\n' +
      '  SEGREDO_ACESSO=... node tools/gerar-codigo.mjs ' + email + '\n' +
      'ou grave a linha SEGREDO_ACESSO=... no arquivo webtoon/.env',
  );
  process.exit(1);
}

const { gerarCodigo, normalizarEmail } = await import(
  '../netlify/functions/_lib/codigos.mjs'
).catch(() => import(join(RAIZ, 'netlify/functions/_lib/codigos.mjs')));

const limpo = normalizarEmail(email);
const codigo = gerarCodigo(limpo);

console.log(`
E-mail do comprador: ${limpo}
Código de acesso:    ${codigo}

Mande as duas linhas abaixo para ele:

  Seu acesso está liberado. Entre em https://SEU-SITE.netlify.app/liberar.html
  E-mail: ${limpo}
  Código: ${codigo}
`);
