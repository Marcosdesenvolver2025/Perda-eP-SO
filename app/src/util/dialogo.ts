/**
 * Diálogos que funcionam nas duas plataformas.
 *
 * `Alert.alert` do React Native não faz nada no navegador — a chamada é
 * ignorada em silêncio. Como as confirmações destrutivas do app dependem
 * dela (declarar entrega, recusar devolução, excluir conta), aqui vai a ponte:
 * nativo usa `Alert`, web usa `window.confirm` / `window.alert`.
 */

import { Alert, Platform } from 'react-native';

/** Aviso simples, só com "ok". */
export function avisar(titulo: string, mensagem?: string) {
  if (Platform.OS === 'web') {
    window.alert(mensagem ? `${titulo}\n\n${mensagem}` : titulo);
    return;
  }
  Alert.alert(titulo, mensagem);
}

interface OpcoesConfirmacao {
  titulo: string;
  mensagem?: string;
  /** Rótulo do botão que confirma. */
  confirmar?: string;
  cancelar?: string;
  /** Pinta o botão de confirmação de vermelho no nativo. */
  destrutivo?: boolean;
}

/** Pergunta sim/não. Resolve `true` quando a pessoa confirma. */
export function confirmar(opcoes: OpcoesConfirmacao): Promise<boolean> {
  const { titulo, mensagem, confirmar: sim = 'confirmar', cancelar = 'cancelar', destrutivo } = opcoes;

  if (Platform.OS === 'web') {
    return Promise.resolve(window.confirm(mensagem ? `${titulo}\n\n${mensagem}` : titulo));
  }

  return new Promise((resolver) => {
    Alert.alert(titulo, mensagem, [
      { text: cancelar, style: 'cancel', onPress: () => resolver(false) },
      {
        text: sim,
        style: destrutivo ? 'destructive' : 'default',
        onPress: () => resolver(true),
      },
    ]);
  });
}
