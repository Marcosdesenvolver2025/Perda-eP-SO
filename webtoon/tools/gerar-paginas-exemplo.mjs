// Gera as páginas de EXEMPLO do site (placeholders em SVG).
//
// Serve só para o site nascer navegável: cada arquivo é um retângulo cinza
// escrito "Capítulo N · Página P". Quando a sua arte estiver pronta, apague o
// que este script gerou e ponha os arquivos de verdade nos mesmos lugares:
//
//   capítulos GRÁTIS (1 a 5)  ->  webtoon/public/paginas/cap-01/001.jpg ...
//   capítulos PAGOS  (6 a 11) ->  webtoon/conteudo/cap-06/001.jpg  ...
//
// A pasta `public/` é publicada na internet: qualquer um baixa o que está lá.
// A pasta `conteudo/` NÃO é publicada — ela vai junto com as funções e só sai
// de lá pela função `pagina`, depois de conferir o acesso. É essa separação
// que faz o cadeado valer alguma coisa.
//
// Extensões aceitas pelo leitor: .jpg, .jpeg, .png, .webp, .svg — o nome do
// arquivo é o número da página com três dígitos (001, 002, ...).
//
// Uso: node webtoon/tools/gerar-paginas-exemplo.mjs

import { mkdir, writeFile, readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const RAIZ = join(AQUI, '..');

const LARGURA = 900;
const ALTURA = 1350;

function pagina({ numero, titulo, indice, total, pago }) {
  const fundo = pago ? '#1b1420' : '#17181d';
  const traco = pago ? '#3b2a45' : '#2a2c34';
  const realce = pago ? '#e0629b' : '#7f86a3';
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${LARGURA}" height="${ALTURA}" viewBox="0 0 ${LARGURA} ${ALTURA}">
  <rect width="${LARGURA}" height="${ALTURA}" fill="${fundo}"/>
  <rect x="24" y="24" width="${LARGURA - 48}" height="${ALTURA - 48}" fill="none" stroke="${traco}" stroke-width="3" stroke-dasharray="14 10"/>
  <g font-family="system-ui, -apple-system, Segoe UI, sans-serif" text-anchor="middle">
    <text x="${LARGURA / 2}" y="${ALTURA / 2 - 90}" fill="${realce}" font-size="34" letter-spacing="6">CAPÍTULO ${numero}</text>
    <text x="${LARGURA / 2}" y="${ALTURA / 2 - 20}" fill="#f2f3f7" font-size="52" font-weight="700">${titulo}</text>
    <text x="${LARGURA / 2}" y="${ALTURA / 2 + 50}" fill="#9aa0b5" font-size="30">página ${indice} de ${total}</text>
    <text x="${LARGURA / 2}" y="${ALTURA - 90}" fill="#5d6377" font-size="24">arquivo de exemplo — substitua pela sua arte</text>
  </g>
</svg>
`;
}

function capa(obra) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="1120" viewBox="0 0 800 1120">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#2a1830"/>
      <stop offset="1" stop-color="#12131a"/>
    </linearGradient>
  </defs>
  <rect width="800" height="1120" fill="url(#g)"/>
  <g font-family="system-ui, -apple-system, Segoe UI, sans-serif" text-anchor="middle">
    <text x="400" y="520" fill="#f2f3f7" font-size="54" font-weight="700">${obra}</text>
    <text x="400" y="580" fill="#9aa0b5" font-size="26">capa de exemplo</text>
  </g>
</svg>
`;
}

const catalogo = JSON.parse(await readFile(join(RAIZ, 'conteudo', 'catalogo.json'), 'utf8'));

await mkdir(join(RAIZ, 'public', 'paginas'), { recursive: true });
await writeFile(join(RAIZ, 'public', 'paginas', 'capa.svg'), capa(catalogo.obra.titulo));

let gerados = 0;
for (const cap of catalogo.capitulos) {
  const nome = `cap-${String(cap.numero).padStart(2, '0')}`;
  const destino = cap.pago
    ? join(RAIZ, 'conteudo', nome)
    : join(RAIZ, 'public', 'paginas', nome);
  await mkdir(destino, { recursive: true });

  for (let i = 1; i <= cap.paginas; i++) {
    const svg = pagina({
      numero: cap.numero,
      titulo: cap.titulo,
      indice: i,
      total: cap.paginas,
      pago: cap.pago,
    });
    await writeFile(join(destino, `${String(i).padStart(3, '0')}.svg`), svg);
    gerados++;
  }
  console.log(`${nome}: ${cap.paginas} páginas em ${cap.pago ? 'conteudo/' : 'public/paginas/'}`);
}

console.log(`\n${gerados} páginas de exemplo + 1 capa.`);
