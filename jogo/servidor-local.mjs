// Servidor estático mínimo, só para abrir o jogo na sua máquina.
//
// Por que precisa de servidor: o jogo usa módulos ES (`import`), e o navegador
// recusa módulo carregado via `file://`. Qualquer servidor estático serve —
// este existe para não obrigar ninguém a instalar nada.
//
//   node servidor-local.mjs        → http://localhost:8080
//   node servidor-local.mjs 3000   → outra porta

import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = fileURLToPath(new URL('.', import.meta.url));
const PORTA = Number(process.argv[2]) || 8080;

const TIPOS = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.webmanifest': 'application/manifest+json',
};

createServer(async (pedido, resposta) => {
  const caminho = decodeURIComponent(new URL(pedido.url, 'http://x').pathname);
  const alvo = normalize(join(RAIZ, caminho === '/' ? 'index.html' : caminho));

  // Nada fora da pasta do jogo, nem com "..".
  if (!alvo.startsWith(RAIZ.endsWith(sep) ? RAIZ : RAIZ + sep)) {
    resposta.writeHead(403).end('fora do lugar');
    return;
  }

  try {
    const conteudo = await readFile(alvo);
    resposta.writeHead(200, {
      'Content-Type': TIPOS[extname(alvo)] || 'application/octet-stream',
      'Cache-Control': 'no-cache',
    });
    resposta.end(conteudo);
  } catch {
    resposta.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    resposta.end('não achei ' + caminho);
  }
}).listen(PORTA, () => {
  console.log(`Volante rodando em http://localhost:${PORTA}`);
});
