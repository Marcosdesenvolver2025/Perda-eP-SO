// O leitor. Capítulo grátis mostra as imagens direto de /paginas/.
// Capítulo pago pede cada imagem à função `pagina`, levando o crachá junto —
// e sem crachá o que aparece é o convite para comprar, não a página.

import { api, acesso, porteiroDeIdade } from './comum.js';

porteiroDeIdade();

const alvo = document.getElementById('conteudo');
const numero = Number(new URLSearchParams(location.search).get('n')) || 1;

const doisDigitos = (n) => String(n).padStart(2, '0');
const tresDigitos = (n) => String(n).padStart(3, '0');

function navegacao(atual, total) {
  const anterior = atual > 1 ? `<a class="botao botao-secundario" href="/capitulo.html?n=${atual - 1}">← Capítulo ${atual - 1}</a>` : '';
  const proximo = atual < total ? `<a class="botao botao-principal" href="/capitulo.html?n=${atual + 1}">Capítulo ${atual + 1} →</a>` : '';
  return `<div class="navegacao-capitulo">${anterior}<a class="botao botao-secundario" href="/">Todos os capítulos</a>${proximo}</div>`;
}

// Convite para comprar, no lugar das páginas. Aparece quando o capítulo é
// pago e o visitante ainda não tem crachá.
function paredao(cap, preco) {
  return `
    <div class="paredao">
      <div class="cadeado">🔒</div>
      <h2>Capítulo ${cap.numero} — ${cap.titulo}</h2>
      <p>
        Daqui em diante a história é para quem comprou o acesso.
        ${preco.descricao} Pagamento único por Pix, sem mensalidade.
      </p>
      <p class="preco" style="font-size:28px;font-weight:700;color:var(--destaque)">${preco.rotulo}</p>
      <p style="margin-top:18px">
        <a class="botao botao-principal botao-largo" href="/comprar.html">Comprar acesso</a>
      </p>
      <p style="margin-top:10px">
        <a class="botao botao-secundario botao-largo" href="/liberar.html">Já comprei — liberar com meu código</a>
      </p>
    </div>
  `;
}

function imagens(cap, cracha) {
  const linhas = [];
  for (let p = 1; p <= cap.paginas; p++) {
    const endereco = cap.pago
      ? `/api/pagina?cap=${cap.numero}&p=${p}&t=${encodeURIComponent(cracha)}`
      : `/paginas/cap-${doisDigitos(cap.numero)}/${tresDigitos(p)}.svg`;
    // Só as duas primeiras páginas carregam de imediato; o resto entra
    // conforme a rolagem, que é o que segura o consumo de dados no celular.
    const carga = p <= 2 ? 'eager' : 'lazy';
    linhas.push(`<img src="${endereco}" alt="Página ${p}" loading="${carga}" decoding="async">`);
  }
  return `<div class="leitor">${linhas.join('')}</div>`;
}

try {
  const dados = await api('/api/catalogo');
  const cap = dados.capitulos.find((c) => c.numero === numero);

  document.getElementById('marca-titulo').textContent = dados.obra.titulo;

  if (!cap) {
    alvo.innerHTML = `<div class="envolucro"><div class="recado recado-erro">Esse capítulo não existe.</div>${navegacao(1, dados.capitulos.length)}</div>`;
  } else {
    document.title = `Capítulo ${cap.numero} — ${dados.obra.titulo}`;
    document.getElementById('rotulo-capitulo').textContent = `Capítulo ${cap.numero} · ${cap.titulo}`;

    const total = dados.capitulos.length;
    const miolo = cap.liberado
      ? imagens(cap, acesso.ler())
      : `<div class="envolucro">${paredao(cap, dados.preco)}</div>`;

    alvo.innerHTML = `${miolo}<div class="envolucro">${navegacao(cap.numero, total)}</div>`;
  }
} catch (erro) {
  alvo.innerHTML = `<div class="envolucro"><div class="recado recado-erro">Não consegui abrir o capítulo: ${erro.message}</div></div>`;
}
