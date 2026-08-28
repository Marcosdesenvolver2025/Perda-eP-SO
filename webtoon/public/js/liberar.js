// Troca o código de acesso pelo crachá que abre os capítulos pagos.

import { api, acesso, porteiroDeIdade } from './comum.js';

porteiroDeIdade();

const campoEmail = document.getElementById('email');
const campoCodigo = document.getElementById('codigo');
const botao = document.getElementById('liberar');
const recado = document.getElementById('recado');

async function liberar() {
  const email = campoEmail.value.trim().toLowerCase();
  const codigo = campoCodigo.value.trim();
  recado.innerHTML = '';

  if (!email || !codigo) {
    recado.innerHTML = '<div class="recado recado-erro">Preencha o e-mail e o código.</div>';
    return;
  }

  botao.disabled = true;
  recado.innerHTML = '<div class="recado recado-espera">Conferindo…</div>';

  try {
    const resposta = await api('/api/liberar-acesso', {
      method: 'POST',
      body: JSON.stringify({ email, codigo }),
    });

    acesso.guardar(resposta.cracha);
    recado.innerHTML =
      '<div class="recado recado-ok">Acesso liberado. Abrindo os capítulos…</div>';
    setTimeout(() => { location.href = '/capitulo.html?n=6'; }, 900);
  } catch (erro) {
    recado.innerHTML = `<div class="recado recado-erro">${erro.message}</div>`;
    botao.disabled = false;
  }
}

botao.addEventListener('click', liberar);
for (const campo of [campoEmail, campoCodigo]) {
  campo.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') liberar();
  });
}
