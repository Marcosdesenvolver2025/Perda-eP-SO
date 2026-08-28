// Pedaços usados por todas as telas: crachá de acesso, porteiro de idade e
// uma função curta para falar com as funções do servidor.

const CHAVE_CRACHA = 'webtoon:cracha';
const CHAVE_IDADE = 'webtoon:idade-confirmada';

export const acesso = {
  ler() {
    try {
      return localStorage.getItem(CHAVE_CRACHA) || '';
    } catch {
      return '';
    }
  },
  guardar(cracha) {
    try {
      localStorage.setItem(CHAVE_CRACHA, cracha);
    } catch {
      // navegador com armazenamento bloqueado: o acesso vale só nesta aba
    }
  },
  apagar() {
    try {
      localStorage.removeItem(CHAVE_CRACHA);
    } catch {
      /* nada a fazer */
    }
  },
};

// GET/POST nas funções, já mandando o crachá quando existe.
export async function api(caminho, opcoes = {}) {
  const cracha = acesso.ler();
  const resposta = await fetch(caminho, {
    ...opcoes,
    headers: {
      ...(opcoes.body ? { 'Content-Type': 'application/json' } : {}),
      ...(cracha ? { Authorization: `Bearer ${cracha}` } : {}),
      ...(opcoes.headers || {}),
    },
  });

  const dados = await resposta.json().catch(() => ({}));
  if (!resposta.ok) {
    throw new Error(dados.erro || `Falha na chamada (${resposta.status}).`);
  }
  return dados;
}

// O porteiro de idade. Fica por cima de tudo até a pessoa confirmar que tem
// 18 anos, e a confirmação vale para as próximas visitas naquele navegador.
//
// Isto é o que a lei espera de um site adulto: aviso claro e confirmação
// antes de mostrar qualquer coisa. Não é identificação de verdade — se você
// precisar disso, o caminho é um serviço de verificação de identidade.
export function porteiroDeIdade() {
  let confirmado = false;
  try {
    confirmado = localStorage.getItem(CHAVE_IDADE) === '1';
  } catch {
    confirmado = false;
  }
  if (confirmado) return;

  const tela = document.createElement('div');
  tela.className = 'porteiro';
  tela.innerHTML = `
    <div class="caixa">
      <span class="selo-18">18+</span>
      <h2>Conteúdo adulto</h2>
      <p>
        Este site tem história em quadrinhos com cenas de sexo, feita para
        maiores de 18 anos. Entrando, você declara ter 18 anos ou mais e
        querer ver esse tipo de conteúdo.
      </p>
      <div class="botoes">
        <button class="botao botao-principal" id="tenho-idade">Tenho 18 anos ou mais — entrar</button>
        <a class="botao botao-secundario" href="https://www.google.com">Sair do site</a>
      </div>
    </div>
  `;

  document.body.appendChild(tela);
  document.body.style.overflow = 'hidden';

  tela.querySelector('#tenho-idade').addEventListener('click', () => {
    try {
      localStorage.setItem(CHAVE_IDADE, '1');
    } catch {
      /* segue mesmo sem conseguir guardar */
    }
    tela.remove();
    document.body.style.overflow = '';
  });
}
