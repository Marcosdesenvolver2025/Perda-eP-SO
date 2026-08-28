// Tela de compra: pede o e-mail, mostra o Pix e explica como o acesso chega.

import { api, porteiroDeIdade } from './comum.js';

porteiroDeIdade();

const campoEmail = document.getElementById('email');
const botao = document.getElementById('gerar');
const recado = document.getElementById('recado-email');
const passoPix = document.getElementById('passo-pix');

const EMAIL_VALIDO = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

// Avisa o Netlify que existe um pedido esperando pagamento. É um envio de
// formulário comum: aparece no painel, em Forms -> pedidos, com o e-mail e o
// número do pedido. Se falhar, a compra segue — o comprovante ainda chega
// pelo contato, e o comprador tem o número do pedido na tela.
async function registrarPedido(email, identificador, valor) {
  try {
    await fetch('/', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        'form-name': 'pedidos',
        email,
        pedido: identificador,
        valor,
        'deixe-vazio': '',
      }).toString(),
    });
  } catch {
    // sem rede ou formulário desligado: não atrapalha a compra
  }
}

function linkDeContato(contato, identificador, email) {
  const texto = encodeURIComponent(
    `Olá! Paguei o Pix do acesso aos capítulos.\nPedido: ${identificador}\nE-mail: ${email}`,
  );

  if (contato.whatsapp) {
    return `<a class="botao botao-principal botao-largo" target="_blank" rel="noopener"
      href="https://wa.me/${contato.whatsapp}?text=${texto}">Enviar comprovante pelo WhatsApp</a>`;
  }
  if (contato.email) {
    return `<a class="botao botao-principal botao-largo"
      href="mailto:${contato.email}?subject=Comprovante%20-%20${identificador}&body=${texto}">Enviar comprovante por e-mail</a>`;
  }
  return `
    <div class="recado recado-espera">
      Guarde o número do pedido. Assim que o pagamento for conferido, o código
      de acesso chega no e-mail que você informou.
    </div>
  `;
}

async function gerarPix() {
  const email = campoEmail.value.trim().toLowerCase();
  recado.innerHTML = '';

  if (!EMAIL_VALIDO.test(email)) {
    recado.innerHTML = '<div class="recado recado-erro">Digite um e-mail válido.</div>';
    campoEmail.focus();
    return;
  }

  botao.disabled = true;
  botao.textContent = 'Gerando…';

  try {
    const cobranca = await api('/api/cobranca', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });

    registrarPedido(email, cobranca.identificador, cobranca.valor);

    passoPix.hidden = false;
    passoPix.innerHTML = `
      <h2 style="margin-top:0;font-size:20px">Pague ${cobranca.valor} no Pix</h2>
      <div id="qr" class="qr"></div>
      <p style="font-size:14px;color:var(--texto-fraco)">
        Abra o aplicativo do seu banco, escolha <strong>Pix Copia e Cola</strong> e use o código abaixo
        (ou aponte a câmera para o QR).
      </p>
      <div class="pix-codigo" id="copia-e-cola">${cobranca.copiaECola}</div>
      <button class="botao botao-secundario botao-largo" id="copiar" style="margin-top:10px">Copiar código Pix</button>

      <h3 style="font-size:16px;margin:24px 0 6px">Depois de pagar</h3>
      <p style="font-size:14px;color:var(--texto-fraco)">
        Mande o comprovante junto com o número do pedido
        <strong>${cobranca.identificador}</strong>. Assim que a gente confere o pagamento,
        você recebe um <strong>código de acesso</strong> por e-mail — é ele que abre os
        capítulos, lá em <a href="/liberar.html">Já comprei</a>.
      </p>
      ${linkDeContato(cobranca.contato || {}, cobranca.identificador, email)}
      <p style="font-size:13px;color:var(--texto-fraco);margin-top:14px">
        Guarde este número de pedido: <strong>${cobranca.identificador}</strong>
      </p>
    `;

    // O QR é desenhado no navegador a partir do mesmo copia-e-cola. Se a
    // biblioteca não carregar (rede ruim, bloqueador), o código escrito
    // continua ali e a compra segue do mesmo jeito.
    const caixaQr = document.getElementById('qr');
    if (window.QRCode) {
      caixaQr.innerHTML = '';
      new window.QRCode(caixaQr, {
        text: cobranca.copiaECola,
        width: 220,
        height: 220,
        correctLevel: window.QRCode.CorrectLevel.M,
      });
    } else {
      caixaQr.remove();
    }

    document.getElementById('copiar').addEventListener('click', async (evento) => {
      try {
        await navigator.clipboard.writeText(cobranca.copiaECola);
        evento.target.textContent = 'Código copiado ✓';
      } catch {
        // Sem permissão para a área de transferência: seleciona para o
        // usuário copiar com os dedos.
        const faixa = document.getElementById('copia-e-cola');
        const selecao = window.getSelection();
        const trecho = document.createRange();
        trecho.selectNodeContents(faixa);
        selecao.removeAllRanges();
        selecao.addRange(trecho);
        evento.target.textContent = 'Selecionado — use copiar';
      }
    });

    passoPix.scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (erro) {
    recado.innerHTML = `<div class="recado recado-erro">${erro.message}</div>`;
  } finally {
    botao.disabled = false;
    botao.textContent = 'Gerar o Pix';
  }
}

botao.addEventListener('click', gerarPix);
campoEmail.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') gerarPix();
});
