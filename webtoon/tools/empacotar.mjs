// Monta o pacote pronto para publicar arrastando no Netlify.
//
//     node tools/empacotar.mjs
//
// Sai daqui a pasta `pacote/` (e o `pacote.zip`), com o site inteiro já
// construído: páginas, funções e capítulos pagos. É o caminho para quem não
// quer configurar build nenhum — arrasta no painel do Netlify e está no ar.
//
// Por que as funções precisam ser "empacotadas": numa publicação por arrasto
// o Netlify não roda build, então não existe quem resolva os `import` entre
// os arquivos nem quem leve a pasta conteudo/ junto. O esbuild junta cada
// função num arquivo só, e as páginas pagas entram embutidas no código.
//
// O cadeado continua inteiro: as páginas pagas não vão para a pasta
// publicada, e só saem pela função `pagina`, depois da conferência do crachá.
//
// PARA ARTE PESADA (dezenas de MB), prefira publicar pelo repositório: lá a
// pasta conteudo/ viaja como arquivo, sem o limite de tamanho do código da
// função. O README explica os dois caminhos.

import { cp, mkdir, readFile, readdir, rm, writeFile, stat } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { dirname, extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const rodar = promisify(execFile);
const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..');
const PACOTE = join(RAIZ, 'pacote');
const OFICINA = join(RAIZ, '.oficina');

const TIPOS = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.avif': 'image/avif',
  '.svg': 'image/svg+xml',
};

const FUNCOES = ['catalogo', 'pagina', 'cobranca', 'liberar-acesso', 'conferir-pagamento'];

await rm(PACOTE, { recursive: true, force: true });
await rm(OFICINA, { recursive: true, force: true });

// 1. As páginas públicas viram a raiz do site.
await cp(join(RAIZ, 'public'), PACOTE, { recursive: true });

// 2. Junta catálogo e páginas pagas num mapa embutido.
const catalogo = JSON.parse(await readFile(join(RAIZ, 'conteudo', 'catalogo.json'), 'utf8'));
const mapa = { catalogo };
let embutidas = 0;
let bytesEmbutidos = 0;

for (const cap of catalogo.capitulos.filter((c) => c.pago)) {
  const nomeCap = `cap-${String(cap.numero).padStart(2, '0')}`;
  const pasta = join(RAIZ, 'conteudo', nomeCap);

  let arquivos = [];
  try {
    arquivos = await readdir(pasta);
  } catch {
    console.warn(`aviso: ${nomeCap} não tem pasta em conteudo/`);
    continue;
  }

  for (const arquivo of arquivos) {
    const tipo = TIPOS[extname(arquivo).toLowerCase()];
    if (!tipo) continue;
    const bytes = await readFile(join(pasta, arquivo));
    mapa[`${nomeCap}/${arquivo.replace(extname(arquivo), '')}`] = {
      tipo,
      dados: bytes.toString('base64'),
    };
    embutidas++;
    bytesEmbutidos += bytes.length;
  }
}

// 3. Copia as funções para uma oficina e troca o mapa vazio pelo cheio.
await cp(join(RAIZ, 'netlify', 'functions'), join(OFICINA, 'functions'), { recursive: true });
await writeFile(
  join(OFICINA, 'functions', '_lib', 'paginas-embutidas.mjs'),
  `// GERADO POR tools/empacotar.mjs — não edite à mão.\nexport const PAGINAS_EMBUTIDAS = ${JSON.stringify(mapa)};\n`,
);

// 4. esbuild junta cada função num arquivo só.
await mkdir(join(PACOTE, 'netlify', 'functions'), { recursive: true });
for (const nome of FUNCOES) {
  await rodar('npx', [
    '-y', 'esbuild',
    join(OFICINA, 'functions', `${nome}.mjs`),
    '--bundle',
    '--platform=node',
    '--format=esm',
    '--target=node20',
    `--outfile=${join(PACOTE, 'netlify', 'functions', `${nome}.mjs`)}`,
  ]);
}

// 5. Configuração da publicação por arrasto.
//
// As rotas /api/* são declaradas aqui em vez de ficarem só no `export const
// config` de cada função: sem build, é o redirect que garante o caminho.
await writeFile(
  join(PACOTE, 'netlify.toml'),
  `# Gerado por tools/empacotar.mjs — publicação por arrasto, sem build.

[functions]
  directory = "netlify/functions"

[[redirects]]
  from = "/api/*"
  to = "/.netlify/functions/:splat"
  status = 200

[[headers]]
  for = "/api/*"
  [headers.values]
    Cache-Control = "no-store"

[[headers]]
  for = "/*"
  [headers.values]
    X-Content-Type-Options = "nosniff"
    Referrer-Policy = "strict-origin-when-cross-origin"
`,
);

await rm(OFICINA, { recursive: true, force: true });

// 6. Compacta, que é o formato que o painel do Netlify aceita.
await rm(join(RAIZ, 'pacote.zip'), { force: true });
try {
  await rodar('zip', ['-qr', join(RAIZ, 'pacote.zip'), '.'], { cwd: PACOTE });
} catch {
  console.warn('aviso: comando `zip` indisponível — a pasta pacote/ ficou pronta assim mesmo.');
}

const tamanhoZip = await stat(join(RAIZ, 'pacote.zip')).then((s) => s.size).catch(() => 0);
const mb = (n) => (n / 1024 / 1024).toFixed(2) + ' MB';

console.log(`
Pacote pronto.

  páginas pagas embutidas : ${embutidas} (${mb(bytesEmbutidos)})
  pasta                   : pacote/
  arquivo                 : pacote.zip ${tamanhoZip ? '(' + mb(tamanhoZip) + ')' : ''}

Publicar: painel do Netlify -> o projeto -> aba Deploys -> arraste o
pacote.zip na área de publicação manual.
`);

if (bytesEmbutidos > 40 * 1024 * 1024) {
  console.warn(
    'ATENÇÃO: as páginas pagas passam de 40 MB embutidas, perto do limite do\n' +
    'código de função. Com arte desse tamanho, publique pelo repositório.',
  );
}
