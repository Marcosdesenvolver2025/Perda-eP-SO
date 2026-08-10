/**
 * Tokenização do cartão de crédito.
 *
 * SEGURANÇA: o número do cartão vai do aparelho DIRETO para a pagar.me, usando
 * a CHAVE PÚBLICA (pk_...). O que volta é um token de uso único, e é só esse
 * token que chega no nosso servidor. Assim nem o app nem a nossa API guardam
 * dado de cartão — é o que o PCI-DSS exige e o que evita um problema sério
 * caso o servidor seja comprometido.
 *
 * Nunca mande `numero`, `cvv` ou `validade` para a nossa API.
 */

export interface DadosDoCartao {
  numero: string;
  titular: string;
  validadeMes: string;
  validadeAno: string;
  cvv: string;
}

const CHAVE_PUBLICA = process.env.EXPO_PUBLIC_PAGARME_CHAVE_PUBLICA ?? '';
const URL_TOKENS = 'https://api.pagar.me/core/v5/tokens';

export function apenasDigitos(texto: string): string {
  return texto.replace(/\D/g, '');
}

/** Formata enquanto digita: 1234 5678 9012 3456. */
export function formatarNumero(texto: string): string {
  return apenasDigitos(texto)
    .slice(0, 16)
    .replace(/(\d{4})(?=\d)/g, '$1 ')
    .trim();
}

/** Formata a validade como MM/AA. */
export function formatarValidade(texto: string): string {
  const digitos = apenasDigitos(texto).slice(0, 4);
  if (digitos.length <= 2) return digitos;
  return `${digitos.slice(0, 2)}/${digitos.slice(2)}`;
}

/** Algoritmo de Luhn: pega número digitado errado antes de ir para a rede. */
export function numeroValido(numero: string): boolean {
  const digitos = apenasDigitos(numero);
  if (digitos.length < 13 || digitos.length > 19) return false;

  let soma = 0;
  let dobra = false;
  for (let i = digitos.length - 1; i >= 0; i--) {
    let d = Number(digitos[i]);
    if (dobra) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    soma += d;
    dobra = !dobra;
  }
  return soma % 10 === 0;
}

export function validadeValida(mes: string, ano: string): boolean {
  const m = Number(mes);
  const a = Number(ano.length === 2 ? `20${ano}` : ano);
  if (!m || m < 1 || m > 12) return false;

  const agora = new Date();
  const ultimoDia = new Date(a, m, 0, 23, 59, 59);
  return ultimoDia >= agora;
}

export function validarCartao(cartao: DadosDoCartao): string[] {
  const erros: string[] = [];
  if (!numeroValido(cartao.numero)) erros.push('Número do cartão inválido.');
  if (cartao.titular.trim().length < 3) erros.push('Informe o nome como está no cartão.');
  if (!validadeValida(cartao.validadeMes, cartao.validadeAno)) {
    erros.push('Validade inválida ou vencida.');
  }
  if (!/^\d{3,4}$/.test(cartao.cvv)) erros.push('CVV inválido.');
  return erros;
}

/**
 * Troca os dados do cartão por um token de uso único na pagar.me.
 * @throws se a chave pública não estiver configurada ou o cartão for recusado.
 */
export async function tokenizarCartao(cartao: DadosDoCartao): Promise<string> {
  if (!CHAVE_PUBLICA) {
    throw new Error(
      'Chave pública da pagar.me não configurada (EXPO_PUBLIC_PAGARME_CHAVE_PUBLICA).',
    );
  }

  const problemas = validarCartao(cartao);
  if (problemas.length) throw new Error(problemas.join(' '));

  const ano = cartao.validadeAno.length === 2 ? `20${cartao.validadeAno}` : cartao.validadeAno;

  const resposta = await fetch(`${URL_TOKENS}?appId=${encodeURIComponent(CHAVE_PUBLICA)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      type: 'card',
      card: {
        number: apenasDigitos(cartao.numero),
        holder_name: cartao.titular.trim(),
        exp_month: Number(cartao.validadeMes),
        exp_year: Number(ano),
        cvv: cartao.cvv,
      },
    }),
  });

  const dados = await resposta.json();
  if (!resposta.ok || !dados?.id) {
    throw new Error(
      dados?.message ?? 'Não conseguimos validar seu cartão. Confira os dados.',
    );
  }
  return dados.id as string;
}
