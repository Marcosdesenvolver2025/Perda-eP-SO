// Leitura do catálogo e dos arquivos das páginas pagas.
//
// `conteudo/**` viaja junto com as funções por causa do `included_files` no
// netlify.toml. O caminho absoluto dessa pasta muda conforme onde a função
// roda (local com `netlify dev`, ou na nuvem), então tentamos os lugares
// possíveis e guardamos o que existir.

import { readFile, stat } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));

const CANDIDATOS = [
  resolve(process.cwd(), 'conteudo'),
  resolve(process.cwd(), 'webtoon/conteudo'),
  resolve(AQUI, '../../../conteudo'),
];

let raizResolvida = null;

async function raizDoConteudo() {
  if (raizResolvida) return raizResolvida;
  for (const caminho of CANDIDATOS) {
    try {
      if ((await stat(caminho)).isDirectory()) {
        raizResolvida = caminho;
        return raizResolvida;
      }
    } catch {
      // tenta o próximo
    }
  }
  throw new Error(
    `Pasta conteudo/ não encontrada. Procurei em: ${CANDIDATOS.join(', ')}. ` +
      'Confira o included_files no netlify.toml.',
  );
}

let catalogoEmCache = null;

export async function lerCatalogo() {
  if (catalogoEmCache) return catalogoEmCache;
  const bruto = await readFile(join(await raizDoConteudo(), 'catalogo.json'), 'utf8');
  catalogoEmCache = JSON.parse(bruto);
  return catalogoEmCache;
}

export async function acharCapitulo(numero) {
  const catalogo = await lerCatalogo();
  return catalogo.capitulos.find((c) => c.numero === Number(numero)) || null;
}

const TIPOS = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.avif': 'image/avif',
  '.svg': 'image/svg+xml',
};

// Lê a página de um capítulo pago. Aceita qualquer uma das extensões
// conhecidas, então trocar os SVGs de exemplo por .jpg não exige mexer no
// código nem no catálogo.
export async function lerPaginaPaga(capitulo, pagina) {
  const nomeCap = `cap-${String(capitulo).padStart(2, '0')}`;
  const nomeArquivo = String(pagina).padStart(3, '0');
  const pasta = join(await raizDoConteudo(), nomeCap);

  for (const [extensao, tipo] of Object.entries(TIPOS)) {
    try {
      const bytes = await readFile(join(pasta, nomeArquivo + extensao));
      return { bytes, tipo };
    } catch {
      // tenta a próxima extensão
    }
  }
  return null;
}

// O que o navegador pode ver antes de pagar: título, número de páginas e o
// aviso de que é pago. Os endereços das imagens pagas não saem daqui.
export function catalogoPublico(catalogo, temAcesso) {
  return {
    obra: catalogo.obra,
    preco: catalogo.preco,
    temAcesso,
    capitulos: catalogo.capitulos.map((c) => ({
      numero: c.numero,
      titulo: c.titulo,
      paginas: c.paginas,
      pago: c.pago,
      resumo: c.resumo || '',
      liberado: !c.pago || temAcesso,
    })),
  };
}
