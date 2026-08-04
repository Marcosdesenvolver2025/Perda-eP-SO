import axios from 'axios';
import Constants from 'expo-constants';
import * as SecureStore from 'expo-secure-store';

const extra = Constants.expoConfig?.extra || {};

export const API_URL = extra.apiUrl || 'http://localhost:3333';
const TOKEN_KEY = 'vendasitinga.token';

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
  if (token) {
    await SecureStore.setItemAsync(TOKEN_KEY, token);
  } else {
    await SecureStore.deleteItemAsync(TOKEN_KEY);
  }
}

export async function loadToken() {
  if (memoryToken) return memoryToken;
  memoryToken = await SecureStore.getItemAsync(TOKEN_KEY);
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

/** Extrai a mensagem de erro amigavel vinda da API. */
export function apiError(error, fallback = 'Não foi possível concluir. Tente novamente.') {
  const data = error?.response?.data;
  if (data?.details?.length) {
    const first = data.details[0];
    return typeof first === 'string' ? first : first.message || data.error || fallback;
  }
  return data?.error || error?.message || fallback;
}

export default api;
