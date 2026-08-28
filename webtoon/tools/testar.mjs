// Teste do cadeado. Exercita as funções como o Netlify as chama — Request
// entra, Response sai — e confere as três coisas que não podem falhar:
// capítulo pago não sai sem crachá, crachá não pode ser forjado, e o código
// de um comprador não abre a conta de outro.
//
// Rode de dentro de webtoon/:  node tools/testar.mjs
process.env.SEGREDO_ACESSO = 'segredo-de-teste-0123456789abcdef';

const base = 'https://exemplo.netlify.app';
const carregar = (nome) => import(new URL(`../netlify/functions/${nome}.mjs`, import.meta.url).href);

let falhas = 0;
function conferir(descricao, condicao, extra = '') {
  console.log(`${condicao ? 'ok  ' : 'FALHA'} ${descricao}${extra ? ' — ' + extra : ''}`);
  if (!condicao) falhas++;
}

const catalogo = (await carregar('catalogo')).default;
const pagina = (await carregar('pagina')).default;
const cobranca = (await carregar('cobranca')).default;
const liberar = (await carregar('liberar-acesso')).default;
const { gerarCodigo } = await import(new URL('../netlify/functions/_lib/codigos.mjs', import.meta.url).href);

// --- visitante sem crachá -------------------------------------------------
let r = await catalogo(new Request(`${base}/api/catalogo`));
let dados = await r.json();
conferir('catálogo abre para quem não pagou', r.status === 200);
conferir('capítulos 1-5 vêm liberados', dados.capitulos.filter(c => c.liberado).length === 5);
conferir('capítulos 6-11 vêm trancados', dados.capitulos.filter(c => c.pago && !c.liberado).length === 6);
conferir('nenhum endereço de imagem paga vaza no catálogo', !JSON.stringify(dados).includes('conteudo/'));

// --- página paga sem crachá -----------------------------------------------
r = await pagina(new Request(`${base}/api/pagina?cap=6&p=1`));
conferir('página paga sem crachá é recusada (402)', r.status === 402);

// --- página paga com crachá inventado -------------------------------------
r = await pagina(new Request(`${base}/api/pagina?cap=6&p=1&t=eyJwIjoieCJ9.assinaturafalsa`));
conferir('crachá forjado é recusado', r.status === 402);

// --- página grátis --------------------------------------------------------
r = await pagina(new Request(`${base}/api/pagina?cap=2&p=1`));
conferir('página grátis é encaminhada para o arquivo público', r.status === 302);

// --- cobrança -------------------------------------------------------------
r = await cobranca(new Request(`${base}/api/cobranca`, {
  method: 'POST',
  body: JSON.stringify({ email: 'Comprador@Exemplo.com' }),
}));
const pix = await r.json();
conferir('cobrança devolve o copia-e-cola', r.status === 200 && pix.copiaECola.startsWith('000201'));
conferir('a chave Pix vai dentro do código', pix.copiaECola.includes('68124592000197'));
conferir('o valor cobrado é 10,00', pix.copiaECola.includes('540510.00'));
conferir('cada compra recebe um identificador', /^CAP[A-Z0-9]+$/.test(pix.identificador));

r = await cobranca(new Request(`${base}/api/cobranca`, { method: 'POST', body: JSON.stringify({ email: 'nao-e-email' }) }));
conferir('e-mail inválido é recusado', r.status === 400);

// --- liberação ------------------------------------------------------------
r = await liberar(new Request(`${base}/api/liberar-acesso`, {
  method: 'POST',
  body: JSON.stringify({ email: 'comprador@exemplo.com', codigo: 'ZZZZZ-ZZZZZ' }),
}));
conferir('código errado é recusado', r.status === 403);

const codigoCerto = gerarCodigo('comprador@exemplo.com');
r = await liberar(new Request(`${base}/api/liberar-acesso`, {
  method: 'POST',
  body: JSON.stringify({ email: 'comprador@exemplo.com', codigo: codigoCerto }),
}));
const liberacao = await r.json();
conferir('código certo devolve o crachá', r.status === 200 && Boolean(liberacao.cracha));

r = await liberar(new Request(`${base}/api/liberar-acesso`, {
  method: 'POST',
  body: JSON.stringify({ email: 'outra@pessoa.com', codigo: codigoCerto }),
}));
conferir('o código de um e-mail não abre para outro', r.status === 403);

// --- comprador com crachá -------------------------------------------------
const cracha = liberacao.cracha;
r = await catalogo(new Request(`${base}/api/catalogo`, { headers: { Authorization: `Bearer ${cracha}` } }));
dados = await r.json();
conferir('com crachá, os 11 capítulos aparecem liberados', dados.capitulos.every(c => c.liberado));

r = await pagina(new Request(`${base}/api/pagina?cap=6&p=3&t=${encodeURIComponent(cracha)}`));
const bytes = Buffer.from(await r.arrayBuffer());
conferir('página paga chega para quem tem crachá', r.status === 200 && bytes.length > 200);
conferir('a imagem não pode ser guardada em cache', r.headers.get('cache-control') === 'private, no-store');

r = await pagina(new Request(`${base}/api/pagina?cap=6&p=99&t=${encodeURIComponent(cracha)}`));
conferir('página inexistente responde 404', r.status === 404);

// --- crachá adulterado ----------------------------------------------------
const adulterado = cracha.slice(0, -3) + 'AAA';
r = await pagina(new Request(`${base}/api/pagina?cap=6&p=1&t=${encodeURIComponent(adulterado)}`));
conferir('crachá com assinatura mexida é recusado', r.status === 402);

console.log(falhas ? `\n${falhas} falha(s)` : '\nTudo passou.');
process.exit(falhas ? 1 : 0);
