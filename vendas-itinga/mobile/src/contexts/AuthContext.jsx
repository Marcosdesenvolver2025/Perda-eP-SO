import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import Constants from 'expo-constants';
import * as Google from 'expo-auth-session/providers/google';
import * as WebBrowser from 'expo-web-browser';
import api, { apiError, isNetworkError, loadToken, saveToken, setUnauthorizedHandler } from '../api/client';

WebBrowser.maybeCompleteAuthSession();

const extra = Constants.expoConfig?.extra || {};
const AuthContext = createContext(null);

/**
 * O provedor Google do expo-auth-session LANCA um erro quando o client id da
 * plataforma vem `undefined` ("Client Id property `androidClientId` must be
 * defined..."). Como o AuthProvider envolve o app inteiro, esse erro derruba
 * tudo logo apos a splash.
 *
 * Por isso sempre entregamos strings ao hook e tratamos a configuracao
 * ausente como um estado normal do app, com mensagem clara na tela de login.
 */
const GOOGLE_PLACEHOLDER = 'nao-configurado.apps.googleusercontent.com';

const googleConfig = {
  webClientId: extra.googleWebClientId || GOOGLE_PLACEHOLDER,
  androidClientId: extra.googleAndroidClientId || GOOGLE_PLACEHOLDER,
  iosClientId: extra.googleIosClientId || GOOGLE_PLACEHOLDER,
};

const GOOGLE_CONFIGURED = Object.values(googleConfig).some(
  (id) => id && id !== GOOGLE_PLACEHOLDER
);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [signingIn, setSigningIn] = useState(false);
  const [connectionError, setConnectionError] = useState(null);

  const [request, response, promptAsync] = Google.useIdTokenAuthRequest({
    clientId: googleConfig.webClientId,
    androidClientId: googleConfig.androidClientId,
    iosClientId: googleConfig.iosClientId,
  });

  const signOut = useCallback(async () => {
    await saveToken(null);
    setUser(null);
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const { data } = await api.get('/auth/me', { timeout: 8000 });
      setUser(data);
      setConnectionError(null);
      return data;
    } catch (error) {
      // Sessao invalida derruba o usuario; API fora do ar apenas registra o
      // problema - o app continua utilizavel e mostra o aviso de conexao.
      if (isNetworkError(error)) {
        setConnectionError(apiError(error));
      } else if (error?.response?.status === 401) {
        await saveToken(null);
        setUser(null);
      }
      return null;
    }
  }, []);

  const authenticateWithIdToken = useCallback(async (idToken) => {
    setSigningIn(true);
    try {
      const { data } = await api.post('/auth/google', { idToken });
      await saveToken(data.token);
      setUser(data.user);
      setConnectionError(null);
      return { ok: true };
    } catch (error) {
      return { ok: false, message: apiError(error, 'Não foi possível entrar com o Google.') };
    } finally {
      setSigningIn(false);
    }
  }, []);

  // Restaura a sessao salva. O `finally` garante que o app SEMPRE sai do
  // estado de carregamento, mesmo se o armazenamento ou a API falharem.
  useEffect(() => {
    let active = true;
    setUnauthorizedHandler(() => setUser(null));

    (async () => {
      try {
        const token = await loadToken();
        if (token) await refreshUser();
      } catch (error) {
        if (__DEV__) console.warn('[Vendas Itinga] falha ao restaurar sessão:', error?.message);
      } finally {
        if (active) setLoading(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [refreshUser]);

  // Resposta do provedor Google.
  useEffect(() => {
    if (response?.type === 'success') {
      const idToken = response.params?.id_token || response.authentication?.idToken;
      if (idToken) authenticateWithIdToken(idToken);
    }
  }, [response, authenticateWithIdToken]);

  const signInWithGoogle = useCallback(async () => {
    if (!GOOGLE_CONFIGURED) {
      return {
        ok: false,
        message:
          'Login do Google ainda não configurado. Preencha EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID (e o web) no arquivo .env do app.',
      };
    }
    if (!request) {
      return { ok: false, message: 'Login do Google ainda está carregando. Tente em um instante.' };
    }

    try {
      const result = await promptAsync();
      if (result?.type !== 'success') {
        return { ok: false, message: result?.type === 'dismiss' ? null : 'Login cancelado.' };
      }
      return { ok: true };
    } catch (error) {
      return { ok: false, message: error?.message || 'Não foi possível abrir o login do Google.' };
    }
  }, [request, promptAsync]);

  const value = useMemo(
    () => ({
      user,
      loading,
      signingIn,
      connectionError,
      isAuthenticated: Boolean(user),
      isSeller: Boolean(user?.isSeller),
      googleConfigured: GOOGLE_CONFIGURED,
      googleReady: Boolean(request),
      signInWithGoogle,
      authenticateWithIdToken,
      signOut,
      refreshUser,
      setUser,
    }),
    [
      user,
      loading,
      signingIn,
      connectionError,
      request,
      signInWithGoogle,
      authenticateWithIdToken,
      signOut,
      refreshUser,
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth precisa estar dentro de <AuthProvider>.');
  return context;
}
