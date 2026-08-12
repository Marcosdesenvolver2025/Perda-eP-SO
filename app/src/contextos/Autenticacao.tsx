/**
 * Login com conta Google.
 *
 * Fluxo: o SDK nativo do Google devolve um `idToken`; mandamos para a nossa
 * API, que valida com o Google e devolve o token de sessão. É o que faz as
 * compras, as vendas e os favoritos ficarem salvos na conta da pessoa.
 *
 * O Google Sign-In não funciona no Expo Go — precisa de development build
 * ou do APK/AAB gerado pelo EAS (ver documentos/publicar-na-play-store.md).
 */

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { api, guardarToken, lerToken, MODO_DEMONSTRACAO } from '../api/cliente';
import type { Usuario } from '../api/tipos';
import { carregarGoogleSignin, faltaPlayServices, foiCancelado } from './google';

interface RespostaLogin {
  token: string;
  usuario: Usuario;
}

interface ContextoAutenticacao {
  usuario: Usuario | null;
  carregando: boolean;
  entrando: boolean;
  entrarComGoogle: () => Promise<void>;
  sair: () => Promise<void>;
  atualizarPerfil: (dados: Partial<Usuario>) => Promise<void>;
  recarregar: () => Promise<void>;
  erro: string | null;
}

const Contexto = createContext<ContextoAutenticacao | null>(null);

export function ProvedorAutenticacao({ children }: { children: React.ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [entrando, setEntrando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (MODO_DEMONSTRACAO) return;
    void carregarGoogleSignin().then((google) =>
      google?.configure({
        // client ID do tipo "Web" do mesmo projeto no Google Cloud
        webClientId: process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID ?? '',
        offlineAccess: false,
        scopes: ['profile', 'email'],
      }),
    );
  }, []);

  const recarregar = useCallback(async () => {
    if (MODO_DEMONSTRACAO) {
      // o servidor falso responde /auth/eu sem exigir token
      setUsuario(await api<Usuario>('/auth/eu'));
      setCarregando(false);
      return;
    }
    try {
      const token = await lerToken();
      if (!token) {
        setUsuario(null);
        return;
      }
      setUsuario(await api<Usuario>('/auth/eu'));
    } catch {
      // token velho ou servidor fora: volta para visitante
      setUsuario(null);
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    void recarregar();
  }, [recarregar]);

  const entrarComGoogle = useCallback(async () => {
    setErro(null);

    if (MODO_DEMONSTRACAO) {
      const login = await api<RespostaLogin>('/auth/google', { metodo: 'POST', publico: true });
      setUsuario(login.usuario);
      return;
    }

    setEntrando(true);
    try {
      const google = await carregarGoogleSignin();
      if (!google) throw new Error('O login com Google não está disponível neste aparelho.');

      await google.hasPlayServices({ showPlayServicesUpdateDialog: true });
      const resposta = await google.signIn();

      const idToken =
        // a lib mudou o formato entre versões; aceitamos os dois
        (resposta as { idToken?: string }).idToken ??
        (resposta as { data?: { idToken?: string } }).data?.idToken;

      if (!idToken) throw new Error('O Google não devolveu o token de acesso.');

      const login = await api<RespostaLogin>('/auth/google', {
        metodo: 'POST',
        corpo: { idToken },
        publico: true,
      });

      await guardarToken(login.token);
      setUsuario(login.usuario);
    } catch (e) {
      const codigo = (e as { code?: string }).code;
      if (await foiCancelado(codigo)) {
        setErro(null); // a pessoa desistiu: não é erro
      } else if (await faltaPlayServices(codigo)) {
        setErro('Atualize o Google Play Services para entrar.');
      } else {
        setErro((e as Error).message ?? 'Não foi possível entrar. Tente de novo.');
      }
    } finally {
      setEntrando(false);
    }
  }, []);

  const sair = useCallback(async () => {
    if (!MODO_DEMONSTRACAO) {
      const google = await carregarGoogleSignin();
      await google?.signOut().catch(() => undefined);
      await guardarToken(null);
    }
    setUsuario(null);
  }, []);

  const atualizarPerfil = useCallback(async (dados: Partial<Usuario>) => {
    await api('/auth/eu', { metodo: 'PATCH', corpo: dados });
    setUsuario((atual) => (atual ? { ...atual, ...dados } : atual));
  }, []);

  const valor = useMemo(
    () => ({
      usuario,
      carregando,
      entrando,
      entrarComGoogle,
      sair,
      atualizarPerfil,
      recarregar,
      erro,
    }),
    [usuario, carregando, entrando, entrarComGoogle, sair, atualizarPerfil, recarregar, erro],
  );

  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useAutenticacao() {
  const contexto = useContext(Contexto);
  if (!contexto) {
    throw new Error('useAutenticacao precisa estar dentro de ProvedorAutenticacao');
  }
  return contexto;
}
