/**
 * Carregamento do SDK do Google Sign-In.
 *
 * O módulo é nativo (Android/iOS) e não existe no navegador. Por isso ele é
 * carregado por `import()` e só quando a plataforma é nativa — assim o build
 * web compila e roda sem o pacote, em vez de quebrar já no import.
 *
 * Isso não muda o fluxo de login de produção: no aparelho, o SDK é o mesmo, e
 * os códigos de erro são lidos do próprio pacote (eles mudam entre Android e
 * iOS, então não dá para chutar valor fixo).
 */

import { Platform } from 'react-native';

interface Google {
  configure(opcoes: {
    webClientId: string;
    offlineAccess: boolean;
    scopes: string[];
  }): void;
  hasPlayServices(opcoes: { showPlayServicesUpdateDialog: boolean }): Promise<boolean>;
  signIn(): Promise<unknown>;
  signOut(): Promise<unknown>;
}

interface Codigos {
  SIGN_IN_CANCELLED: string;
  PLAY_SERVICES_NOT_AVAILABLE: string;
}

let sdk: { google: Google; codigos: Codigos } | null | undefined;

async function carregar() {
  if (sdk !== undefined) return sdk;

  if (Platform.OS === 'web') {
    sdk = null;
    return sdk;
  }

  try {
    const modulo = await import('@react-native-google-signin/google-signin');
    sdk = {
      google: modulo.GoogleSignin as unknown as Google,
      codigos: modulo.statusCodes as unknown as Codigos,
    };
  } catch {
    // pacote ausente (Expo Go, por exemplo): o app segue sem login social
    sdk = null;
  }
  return sdk;
}

/** Devolve o SDK, ou `null` onde ele não existe (navegador). */
export async function carregarGoogleSignin(): Promise<Google | null> {
  return (await carregar())?.google ?? null;
}

/** A pessoa fechou a janela do Google. Não é erro. */
export async function foiCancelado(codigo?: string): Promise<boolean> {
  return codigo != null && codigo === (await carregar())?.codigos.SIGN_IN_CANCELLED;
}

/** O aparelho está sem Play Services atualizado. */
export async function faltaPlayServices(codigo?: string): Promise<boolean> {
  return codigo != null && codigo === (await carregar())?.codigos.PLAY_SERVICES_NOT_AVAILABLE;
}
