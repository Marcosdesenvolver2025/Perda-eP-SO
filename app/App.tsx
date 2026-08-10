import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { ProvedorAutenticacao } from './src/contextos/Autenticacao';
import { Navegacao } from './src/navegacao';

export default function App() {
  return (
    <SafeAreaProvider>
      <ProvedorAutenticacao>
        <StatusBar style="dark" />
        <Navegacao />
      </ProvedorAutenticacao>
    </SafeAreaProvider>
  );
}
