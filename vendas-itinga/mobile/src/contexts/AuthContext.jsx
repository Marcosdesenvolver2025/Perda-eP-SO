import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import Constants from 'expo-constants';
import * as Google from 'expo-auth-session/providers/google';
import * as WebBrowser from 'expo-web-browser';
import api, { apiError, loadToken, saveToken, setUnauthorizedHandler } from '../api/client';

WebBrowser.maybeCompleteAuthSession();

const extra = Constants.expoConfig?.extra || {};
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [signingIn, setSigningIn] = useState(false);

  // Google Sign-In: fluxo obrigatorio de entrada do app.
  const [request, response, promptAsync] = Google.useIdTokenAuthRequest({
    clientId: extra.googleWebClientId,
    androidClientId: extra.googleAndroidClientId,
    iosClientId: extra.googleIosClientId,
  });

  const signOut = useCallback(async () => {
    await saveToken(null);
    setUser(null);
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const { data } = await api.get('/auth/me');
      setUser(data);
      return data;
    } catch {
      return null;
    }
  }, []);

  const authenticateWithIdToken = useCallback(async (idToken) => {
    setSigningIn(true);
    try {
      const { data } = await api.post('/auth/google', { idToken });
      await saveToken(data.token);
      setUser(data.user);
      return { ok: true };
    } catch (error) {
      return { ok: false, message: apiError(error, 'Não foi possível entrar com o Google.') };
    } finally {
      setSigningIn(false);
    }
  }, []);

  // Restaura a sessao salva no SecureStore ao abrir o app.
  useEffect(() => {
    let active = true;
    setUnauthorizedHandler(() => setUser(null));

    (async () => {
      const token = await loadToken();
      if (token) await refreshUser();
      if (active) setLoading(false);
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
    if (!request) {
      return { ok: false, message: 'Login do Google ainda não está pronto. Aguarde um instante.' };
    }
    const result = await promptAsync();
    if (result?.type !== 'success') {
      return { ok: false, message: result?.type === 'dismiss' ? null : 'Login cancelado.' };
    }
    return { ok: true };
  }, [request, promptAsync]);

  const value = useMemo(
    () => ({
      user,
      loading,
      signingIn,
      isAuthenticated: Boolean(user),
      isSeller: Boolean(user?.isSeller),
      signInWithGoogle,
      authenticateWithIdToken,
      signOut,
      refreshUser,
      setUser,
      googleReady: Boolean(request),
    }),
    [user, loading, signingIn, signInWithGoogle, authenticateWithIdToken, signOut, refreshUser, request]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth precisa estar dentro de <AuthProvider>.');
  return context;
}
