// Código de acesso do comprador.
//
// Como a chave Pix avulsa não avisa o site quando o dinheiro cai, a liberação
// é sua: você vê o Pix no aplicativo do banco, gera o código com
//
//     node tools/gerar-codigo.mjs email-do-comprador@exemplo.com
//
// e manda para ele. O comprador entra em /liberar.html, digita o e-mail e o
// código, e os capítulos pagos abrem.
//
// O código é a assinatura do próprio e-mail, feita com o segredo do site. Sai
// disso um jeito de conferir sem guardar nada em lugar nenhum:
//
//   - ninguém adivinha um código sem conhecer o segredo;
//   - o código de um e-mail não funciona em outro, então repassar o código
//     para os amigos só funciona se o comprador repassar também o e-mail
//     dele — e aí é uma conta só, do jeito que qualquer assinatura funciona;
//   - trocar SEGREDO_ACESSO invalida todos os códigos de uma vez, que é a
//     saída se algum deles vazar em massa.

import { createHmac, timingSafeEqual } from 'node:crypto';

// Sem I, O, 0 e 1: some a dúvida entre letra e número quando alguém digita
// o código lendo de um print.
const ALFABETO = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const TAMANHO = 10;

function segredo() {
  const s = process.env.SEGREDO_ACESSO;
  if (!s || s.length < 16) {
    throw new Error(
      'SEGREDO_ACESSO não está definida (ou é curta demais). ' +
        'Gere uma com `openssl rand -hex 32` e cadastre nas variáveis de ambiente do Netlify.',
    );
  }
  return s;
}

export function normalizarEmail(email) {
  return String(email || '').trim().toLowerCase();
}

// Deixa o código comparável mesmo que a pessoa digite com espaço, minúscula
// ou sem os hifens.
export function normalizarCodigo(codigo) {
  return String(codigo || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
}

export function gerarCodigo(email) {
  const digest = createHmac('sha256', segredo())
    .update(`codigo:${normalizarEmail(email)}`)
    .digest();

  let bruto = '';
  for (let i = 0; i < TAMANHO; i++) {
    bruto += ALFABETO[digest[i] % ALFABETO.length];
  }
  // Formatado em blocos, que é mais fácil de ditar e de digitar.
  return `${bruto.slice(0, 5)}-${bruto.slice(5)}`;
}

export function codigoConfere(email, codigo) {
  const esperado = Buffer.from(normalizarCodigo(gerarCodigo(email)));
  const recebido = Buffer.from(normalizarCodigo(codigo));
  if (esperado.length !== recebido.length) return false;
  return timingSafeEqual(esperado, recebido);
}
