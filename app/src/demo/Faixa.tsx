/**
 * Faixa fixa do modo demonstração.
 *
 * Fica acima de tudo para deixar claro que nada ali é real, e dá o atalho
 * para o roteiro de telas. Só é montada quando `MODO_DEMONSTRACAO` está ligado
 * (ver `App.tsx`), então em produção não existe.
 */

import React from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';

import { cores, espaco } from '../tema';
import { irPara } from './navegacao';

export function FaixaDeDemonstracao() {
  return (
    <View style={e.faixa}>
      <View style={{ flex: 1 }}>
        <Text style={e.titulo}>modo demonstração</Text>
        <Text style={e.legenda}>dados de exemplo · nada é cobrado</Text>
      </View>
      <Pressable
        onPress={() => irPara('Roteiro')}
        style={e.botao}
        accessibilityRole="button"
        accessibilityLabel="abrir o roteiro da demonstração"
      >
        <Text style={e.textoDoBotao}>roteiro</Text>
      </Pressable>
    </View>
  );
}

const e = StyleSheet.create({
  faixa: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: espaco.md,
    backgroundColor: cores.preto,
    paddingHorizontal: espaco.lg,
    paddingVertical: 8,
    // no navegador a faixa fica colada no topo da janela
    ...Platform.select({ web: { paddingTop: 10 }, default: {} }),
  },
  titulo: { color: '#fff', fontSize: 12, fontWeight: '700', letterSpacing: 0.3 },
  legenda: { color: 'rgba(255,255,255,0.7)', fontSize: 11 },
  botao: {
    backgroundColor: cores.verde,
    paddingHorizontal: espaco.md,
    paddingVertical: 6,
    borderRadius: 999,
  },
  textoDoBotao: { color: cores.preto, fontSize: 12, fontWeight: '700' },
});
