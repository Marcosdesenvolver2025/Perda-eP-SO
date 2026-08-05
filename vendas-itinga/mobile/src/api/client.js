import axios from 'axios';
import { Platform } from 'react-native';
import Constants from 'expo-constants';
import * as SecureStore from 'expo-secure-store';

const extra = Constants.expoConfig?.extra || {};
const TOKEN_KEY = 'vendasitinga.token';

/**
 * Normaliza o endereco da API.
 *
 * Erros comuns que isso corrige:
 *   "192.168.0.10:3333"   -> "http://192.168.0.10:3333"  (falta o esquema)
 *   "http://meuip:3333/"  -> "http://meuip:3333"         (barra sobrando)
 *   "  http://ip:3333 "   -> "http://ip:3333"            (espacos)
 *
 * Sem o esquema, o axios lanca erro e o app quebra antes de qualquer tela.
 */
function normalizeApiUrl(raw) {
  const value = String(raw || '').trim().replace(/\/+$/, '');
  if (!value) return 'http://localhost:3333';
  if (!/^https?:\/\//i.test(value)) return `http://${value}`;
  return value;
}

export const API_URL = normalizeApiUrl(extra.apiUrl);

/**
 * "localhost" dentro do celular aponta para o PROPRIO celular, nunca para o
 * seu computador. No emulador Android use 10.0.2.2; em aparelho fisico use o
 * IP da sua maquina na rede local (ex.: 192.168.0.10).
 */
export const API_URL_WARNING = /^https?:\/\/(localhost|127\.0\.0\.1)/i.test(API_URL)
  ? Platform.OS === 'android'
    ? 'A API está apontando para localhost. No emulador Android use http://10.0.2.2:3333 e, em celular físico, o IP da sua máquina na rede (ex.: http://192.168.0.10:3333).'
    : 'A API está apontando para localhost. Em um aparelho físico use o IP da sua máquina na rede local.'
  : null;

if (__DEV__) {
  console.log(`[Vendas Itinga] API: ${API_URL}`);
  if (API_URL_WARNING) console.warn(`[Vendas Itinga] ${API_URL_WARNING}`);
}

export const api = axios.create({
  baseURL: API_URL,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
});

let memoryToken = null;
let onUnauthorized = null;

/** Callback disparado quando a sessao expira (o AuthContext desloga). */
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

export async function saveToken(token) {
  memoryToken = token;
  try {
    if (token) {
      await SecureStore.setItemAsync(TOKEN_KEY, token);
    } else {
      await SecureStore.deleteItemAsync(TOKEN_KEY);
    }
  } catch (error) {
    // O armazenamento seguro pode falhar em alguns aparelhos. A sessao segue
    // valida em memoria ate o app ser fechado - melhor que travar o login.
    if (__DEV__) console.warn('[Vendas Itinga] SecureStore indisponível:', error.message);
  }
}

export async function loadToken() {
  if (memoryToken) return memoryToken;
  try {
    memoryToken = await SecureStore.getItemAsync(TOKEN_KEY);
  } catch {
    memoryToken = null;
  }
  return memoryToken;
}

api.interceptors.request.use(async (config) => {
  const token = await loadToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      await saveToken(null);
      onUnauthorized?.();
    }
    return Promise.reject(error);
  }
);

/** true quando o erro foi de rede (servidor fora do ar, IP errado, sem wi-fi). */
export function isNetworkError(error) {
  if (!error) return false;
  if (error.response) return false; // o servidor respondeu, entao a rede foi
  return (
    error.code === 'ECONNABORTED' ||
    error.code === 'ERR_NETWORK' ||
    /network|timeout|failed to fetch/i.test(error.message || '')
  );
}

/** Extrai uma mensagem de erro legivel para mostrar ao usuario. */
export function apiError(error, fallback = 'Não foi possível concluir. Tente novamente.') {
  if (isNetworkError(error)) {
    return `Não foi possível conectar ao servidor (${API_URL}). Verifique se a API está no ar e se o celular está na mesma rede.`;
  }

  const data = error?.response?.data;
  if (data?.details?.length) {
    const first = data.details[0];
    return typeof first === 'string' ? first : first.message || data.error || fallback;
  }
  return data?.error || error?.message || fallback;
}

/** Testa a conexao com a API. Usado nas telas de erro para o botao "tentar de novo". */
export async function checkConnection() {
  try {
    const { data } = await api.get('/health', { timeout: 8000 });
    return { ok: true, data };
  } catch (error) {
    return { ok: false, message: apiError(error) };
  }
}

export default api;
