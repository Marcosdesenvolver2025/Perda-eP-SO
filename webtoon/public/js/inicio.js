// Página inicial: capa, sinopse e a lista dos 11 capítulos.

import { api, porteiroDeIdade, acesso } from './comum.js';

porteiroDeIdade();

const alvo = document.getElementById('conteudo');

function cartaoCapitulo(cap) {
  const trancado = cap.pago && !cap.liberado;
  const destino = trancado ? '/comprar.html' : `/capitulo.html?n=${cap.numero}`;

  let etiqueta = '<span class="etiqueta etiqueta-gratis">grátis</span>';
  if (cap.pago) {
    etiqueta = cap.liberado
      ? '<span class="etiqueta etiqueta-liberado">liberado</span>'
      : '<span class="etiqueta etiqueta-pago">🔒 pago</span>';
  }

  return `
    <a class="capitulo ${trancado ? 'trancado' : ''}" href="${destino}">
      <span class="numero">${cap.numero}</span>
      <span class="corpo">
        <strong>${cap.titulo}</strong>
        <small>${cap.resumo || `${cap.paginas} páginas`}</small>
      </span>
      ${etiqueta}
    </a>
  `;
}

function faixaDeVenda(dados) {
  if (dados.temAcesso) {
    return `
      <div class="faixa-venda">
        <h2>Acesso liberado ✓</h2>
        <p>Você já comprou. Todos os capítulos estão abertos neste navegador.</p>
      </div>
    `;
  }

  return `
    <div class="faixa-venda">
      <h2>Continue a história</h2>
      <p>${dados.preco.descricao} Pagamento por Pix, uma vez só — sem mensalidade.</p>
      <p class="preco">${dados.preco.rotulo}</p>
      <a class="botao botao-principal" href="/comprar.html">Liberar do capítulo 6 ao 11</a>
    </div>
  `;
}

try {
  const dados = await api('/api/catalogo');

  document.title = `${dados.obra.titulo} — leitura online`;
  document.getElementById('marca-titulo').textContent = dados.obra.titulo;
  document.getElementById('rodape-aviso').textContent = dados.obra.aviso;

  const gratis = dados.capitulos.filter((c) => !c.pago);
  const pagos = dados.capitulos.filter((c) => c.pago);

  alvo.innerHTML = `
    <section class="capa-obra">
      <img src="${dados.obra.capa}" alt="Capa de ${dados.obra.titulo}">
      <div>
        <h1>${dados.obra.titulo}</h1>
        <p class="autor">por ${dados.obra.autor}</p>
        <p class="sinopse">${dados.obra.sinopse}</p>
        <p style="margin-top:18px">
          <a class="botao botao-principal" href="/capitulo.html?n=1">Começar a ler de graça</a>
        </p>
      </div>
    </section>

    <h2 class="titulo-secao">Capítulos abertos <span>leia sem pagar</span></h2>
    <div class="lista-capitulos">${gratis.map(cartaoCapitulo).join('')}</div>

    ${faixaDeVenda(dados)}

    <h2 class="titulo-secao">Capítulos pagos <span>do 6 ao 11</span></h2>
    <div class="lista-capitulos">${pagos.map(cartaoCapitulo).join('')}</div>
  `;
} catch (erro) {
  // Crachá velho ou segredo trocado: apaga e recomeça limpo.
  acesso.apagar();
  alvo.innerHTML = `<div class="recado recado-erro">Não consegui carregar os capítulos: ${erro.message}</div>`;
}
