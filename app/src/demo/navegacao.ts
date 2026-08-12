/**
 * Referência de navegação para a faixa de demonstração.
 *
 * A faixa fica fora do `NavigationContainer` (ela é irmã dele, não filha),
 * então não recebe a prop `navigation`. A referência resolve isso sem mexer
 * na árvore de telas do app.
 */

import { createNavigationContainerRef } from '@react-navigation/native';

import type { ParametrosApp } from '../navegacao/tipos';

export const referenciaDeNavegacao = createNavigationContainerRef<ParametrosApp>();

export function irPara(tela: keyof ParametrosApp, parametros?: object) {
  if (referenciaDeNavegacao.isReady()) {
    // @ts-expect-error a assinatura varia por tela; a chamada é sempre válida aqui
    referenciaDeNavegacao.navigate(tela, parametros);
  }
}
