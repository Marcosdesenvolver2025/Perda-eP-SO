/**
 * Cliente HTTP da API do Vendas Itinga.
 *
 * Sem `EXPO_PUBLIC_API_URL` configurado, o app roda com os dados de exemplo
 * (`src/dados/exemplo.ts`). Isso deixa qualquer pessoa abrir o app e navegar
 * pelas telas antes de o servidor estar no ar.
 */

import AsyncStorage from '@react-native-async-storage/async-storage';

export const URL_API = process.env.EXPO_PUBLIC_API_URL ?? '';
export const MODO_DEMONSTRACAO = URL_API === '';

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
    throw new ErroDaApi(
      0,
      'App em modo demonstração. Configure EXPO_PUBLIC_API_URL para conectar no servidor.',
      'demonstracao',
    );
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
