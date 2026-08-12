import React from 'react';
import { View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { MODO_DEMONSTRACAO } from './src/api/cliente';
import { ProvedorAutenticacao } from './src/contextos/Autenticacao';
import { FaixaDeDemonstracao } from './src/demo/Faixa';
import { Navegacao } from './src/navegacao';

export default function App() {
  return (
    <SafeAreaProvider>
      <ProvedorAutenticacao>
        <StatusBar style="dark" />
        <View style={{ flex: 1 }}>
          {MODO_DEMONSTRACAO ? <FaixaDeDemonstracao /> : null}
          <Navegacao />
        </View>
      </ProvedorAutenticacao>
    </SafeAreaProvider>
  );
}
