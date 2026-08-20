/**
 * Service worker do Vendas Itinga.
 *
 * É o que falta para o site ser um PWA de verdade: sem service worker o
 * navegador não oferece "instalar", e o app não abre sem rede.
 *
 * A versão é carimbada no build por `scripts/finalizar-web.mjs`, que troca
 * `__VERSAO__` pelo hash do bundle. Assim cada deploy cria um cache novo e
 * limpa os antigos — sem isso o app ficaria preso na versão velha.
 *
 * Estratégias, por tipo de pedido:
 *
 *   navegação (abrir/recarregar)  rede primeiro, cache como reserva
 *     mantém o app atualizado quando há rede e faz ele abrir offline.
 *
 *   /_expo/static/** e /assets/**  cache primeiro
 *     têm hash no nome, então nunca mudam de conteúdo: uma vez baixados,
 *     valem para sempre e não precisam de rede.
 *
 *   o resto                        rede, com o cache como reserva
 */

const VERSAO = '__VERSAO__';
const CACHE = `vendas-itinga-${VERSAO}`;

/** Baixado já na instalação, para o app abrir offline logo de cara. */
const CASCA = [
  '/',
  '/index.html',
  '/manifest.webmanifest',
  '/favicon.ico',
  '/icone-192.png',
  '/icone-512.png',
  '/apple-touch-icon.png',
  '__BUNDLES__',
].filter((u) => u !== '__BUNDLES__');

self.addEventListener('install', (evento) => {
  evento.waitUntil(
    (async () => {
      const cache = await caches.open(CACHE);
      // addAll falha inteiro se um item falhar; um a um é mais tolerante
      await Promise.all(
        CASCA.map((url) => cache.add(url).catch(() => undefined)),
      );
      await self.skipWaiting();
    })(),
  );
});

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    (async () => {
      const nomes = await caches.keys();
      await Promise.all(
        nomes
          .filter((n) => n.startsWith('vendas-itinga-') && n !== CACHE)
          .map((n) => caches.delete(n)),
      );
      await self.clients.claim();
    })(),
  );
});

const imutavel = (url) =>
  url.pathname.startsWith('/_expo/static/') || url.pathname.startsWith('/assets/');

self.addEventListener('fetch', (evento) => {
  const req = evento.request;

  // o service worker só cuida do próprio site, e só de leitura
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;

  // abrir ou recarregar: tenta a rede, cai no cache se estiver offline
  if (req.mode === 'navigate') {
    evento.respondWith(
      (async () => {
        try {
          const resposta = await fetch(req);
          const cache = await caches.open(CACHE);
          cache.put('/index.html', resposta.clone());
          return resposta;
        } catch {
          const cache = await caches.open(CACHE);
          return (
            (await cache.match('/index.html')) ??
            (await cache.match('/')) ??
            Response.error()
          );
        }
      })(),
    );
    return;
  }

  // bundle e assets: têm hash no nome, o cache vale para sempre
  if (imutavel(url)) {
    evento.respondWith(
      (async () => {
        const cache = await caches.open(CACHE);
        const guardado = await cache.match(req);
        if (guardado) return guardado;
        const resposta = await fetch(req);
        if (resposta.ok) cache.put(req, resposta.clone());
        return resposta;
      })(),
    );
    return;
  }

  // o resto: rede, com o cache como reserva
  evento.respondWith(
    (async () => {
      try {
        const resposta = await fetch(req);
        if (resposta.ok) {
          const cache = await caches.open(CACHE);
          cache.put(req, resposta.clone());
        }
        return resposta;
      } catch {
        const cache = await caches.open(CACHE);
        return (await cache.match(req)) ?? Response.error();
      }
    })(),
  );
});
