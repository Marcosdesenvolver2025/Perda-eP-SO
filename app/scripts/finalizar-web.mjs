/**
 * Passo final do build web.
 *
 * O `expo export` não conhece o service worker: ele copia `public/sw.js`
 * como está, com os marcadores `__VERSAO__` e `__BUNDLES__` intactos. Este
 * script preenche os dois lendo o `dist` que acabou de ser gerado.
 *
 * Por que carimbar a versão: o nome do cache precisa mudar a cada deploy,
 * senão o service worker antigo continua servindo a versão velha do app e
 * a atualização nunca chega. O hash do bundle é a versão natural — muda
 * exatamente quando o código muda.
 *
 * Roda sozinho no `npm run build:web`.
 */

import { readdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const DIST = new URL('../dist/', import.meta.url).pathname;
const CAMINHO_SW = join(DIST, 'sw.js');

if (!existsSync(CAMINHO_SW)) {
  console.error('finalizar-web: dist/sw.js não existe. O export rodou?');
  process.exit(1);
}

// os bundles ficam em dist/_expo/static/js/web/, com hash no nome
const PASTA_JS = join(DIST, '_expo/static/js/web');
const bundles = existsSync(PASTA_JS)
  ? readdirSync(PASTA_JS)
      .filter((n) => n.endsWith('.js'))
      // o chunk vazio do import() preguiçoso não vale a pena pré-carregar
      .filter((n) => readFileSync(join(PASTA_JS, n)).length > 0)
      .map((n) => `/_expo/static/js/web/${n}`)
  : [];

if (bundles.length === 0) {
  console.error('finalizar-web: nenhum bundle encontrado em', PASTA_JS);
  process.exit(1);
}

// a versão sai do maior bundle: é o que muda quando o app muda
const principal = bundles
  .map((u) => ({ u, tam: readFileSync(join(DIST, u.slice(1))).length }))
  .sort((a, b) => b.tam - a.tam)[0].u;
const versao = principal.match(/index-([a-f0-9]+)\.js$/)?.[1]?.slice(0, 12) ?? String(Date.now());

let sw = readFileSync(CAMINHO_SW, 'utf8');

// mira a linha da constante, não o `__VERSAO__` citado no comentário do topo
const antes = sw;
sw = sw.replace(/const VERSAO = '__VERSAO__';/, `const VERSAO = '${versao}';`);
if (sw === antes) {
  console.error('finalizar-web: não achei a linha `const VERSAO` em sw.js');
  process.exit(1);
}

sw = sw.replace(
  "  '__BUNDLES__',\n",
  bundles.map((u) => `  '${u}',\n`).join(''),
);
if (sw.includes('__BUNDLES__') && !sw.includes("u !== '__BUNDLES__'")) {
  console.error('finalizar-web: a lista de bundles não foi substituída');
  process.exit(1);
}

writeFileSync(CAMINHO_SW, sw);

console.log(`finalizar-web: sw.js versão ${versao}, ${bundles.length} bundle(s) na casca`);
