/**
 * Cliente HTTP da API do Vendas Itinga.
 *
 * Sem `EXPO_PUBLIC_API_URL` configurado, o app entra em **modo demonstração**:
 * as mesmas chamadas são respondidas por um servidor falso em memória
 * (`src/demo/servidor.ts`), que só existe para navegar pelas telas antes de o
 * servidor de verdade estar no ar.
 *
 * O código de demonstração fica todo em `src/demo/` e é carregado por
 * `import()` preguiçoso — em produção (`EXPO_PUBLIC_API_URL` preenchido) ele
 * nunca chega a ser executado.
 */

import AsyncStorage from '@react-native-async-storage/async-storage';

export const URL_API = process.env.EXPO_PUBLIC_API_URL ?? '';

/** Liga a demonstração mesmo com API configurada. Usado no build web da vitrine. */
const FORCAR_DEMONSTRACAO = process.env.EXPO_PUBLIC_MODO_DEMO === '1';

export const MODO_DEMONSTRACAO = FORCAR_DEMONSTRACAO || URL_API === '';

const CHAVE_TOKEN = '@vendasitinga:token';

let tokenEmMemoria: string | null = null;

export async function guardarToken(token: string | null) {
  tokenEmMemoria = token;
  if (token) await AsyncStorage.setItem(CHAVE_TOKEN, token);
  else await AsyncStorage.removeItem(CHAVE_TOKEN);
}

export async function lerToken(): Promise<string | null> {
  if (tokenEmMemoria) return tokenEmMemoria;
  tokenEmMemoria = await AsyncStorage.getItem(CHAVE_TOKEN);
  return tokenEmMemoria;
}

export class ErroDaApi extends Error {
  constructor(
    readonly status: number,
    mensagem: string,
    readonly codigo?: string,
  ) {
    super(mensagem);
    this.name = 'ErroDaApi';
  }
}

interface Opcoes {
  metodo?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  corpo?: unknown;
  /** Rotas públicas (vitrine) não precisam de token. */
  publico?: boolean;
}

export async function api<T>(caminho: string, opcoes: Opcoes = {}): Promise<T> {
  if (MODO_DEMONSTRACAO) {
    // carregado só aqui: o bundle de produção não executa nada de src/demo/
    const { responderDemo } = await import('../demo/servidor');
    try {
      return await responderDemo<T>(caminho, opcoes.metodo ?? 'GET', opcoes.corpo);
    } catch (e) {
      const erro = e as { status?: number; message?: string };
      throw new ErroDaApi(
        erro.status ?? 400,
        erro.message ?? 'Não conseguimos completar essa ação.',
        'demonstracao',
      );
    }
  }

  const cabecalhos: Record<string, string> = { 'Content-Type': 'application/json' };
  if (!opcoes.publico) {
    const token = await lerToken();
    if (token) cabecalhos.Authorization = `Bearer ${token}`;
  }

  let resposta: Response;
  try {
    resposta = await fetch(`${URL_API}${caminho}`, {
      method: opcoes.metodo ?? 'GET',
      headers: cabecalhos,
      body: opcoes.corpo === undefined ? undefined : JSON.stringify(opcoes.corpo),
    });
  } catch {
    throw new ErroDaApi(0, 'Sem conexão. Verifique sua internet.', 'rede');
  }

  if (resposta.status === 204) return undefined as T;

  const texto = await resposta.text();
  const dados = texto ? JSON.parse(texto) : {};

  if (!resposta.ok) {
    // sessão expirada: limpa o token para o app voltar à tela de entrada
    if (resposta.status === 401) await guardarToken(null);
    throw new ErroDaApi(
      resposta.status,
      dados?.erro?.mensagem ?? 'Não conseguimos completar essa ação.',
      dados?.erro?.codigo,
    );
  }

  return dados as T;
}
