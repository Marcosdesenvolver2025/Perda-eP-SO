/**
 * Entrada com conta Google.
 *
 * É o único jeito de entrar: sem senha para lembrar e sem cadastro comprido —
 * do mesmo jeito que o app do vídeo faz.
 */

import React from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import * as Linking from 'expo-linking';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { Aviso } from '../componentes/base';
import { useAutenticacao } from '../contextos/Autenticacao';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';

type Props = NativeStackScreenProps<ParametrosApp, 'Entrar'>;

export function TelaEntrar({ navigation }: Props) {
  const { entrarComGoogle, entrando, erro } = useAutenticacao();

  async function entrar() {
    await entrarComGoogle();
    if (navigation.canGoBack()) navigation.goBack();
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.branco }}>
      <View style={e.conteudo}>
        <Image
          source={require('../../assets/logo-horizontal.png')}
          style={{ width: 220, height: 66 }}
          resizeMode="contain"
          accessibilityLabel="Vendas Itinga"
        />

        <Text style={[fonte.titulo, { marginTop: espaco.xxl, textAlign: 'center' }]}>
          compre e venda dentro de Itinga
        </Text>
        <Text style={[fonte.subtitulo, { marginTop: espaco.sm, textAlign: 'center' }]}>
          entre com sua conta google. suas compras, vendas e favoritos ficam salvos.
        </Text>

        <View style={{ marginTop: espaco.xxl, width: '100%' }}>
          {erro ? <Aviso texto={erro} tom="alerta" /> : null}

          <Pressable
            onPress={entrar}
            disabled={entrando}
            style={({ pressed }) => [e.botaoGoogle, pressed && { opacity: 0.85 }]}
            accessibilityRole="button"
            accessibilityLabel="entrar com google"
          >
            <Ionicons name="logo-google" size={20} color={cores.texto} />
            <Text style={e.textoGoogle}>
              {entrando ? 'entrando...' : 'entrar com google'}
            </Text>
          </Pressable>

          <Pressable
            onPress={() => navigation.navigate('Abas', { screen: 'Home' })}
            style={{ marginTop: espaco.lg, alignItems: 'center' }}
            accessibilityRole="button"
          >
            <Text style={{ color: cores.verdeEscuro, fontWeight: '600' }}>
              só dar uma olhada primeiro
            </Text>
          </Pressable>
        </View>
      </View>

      <View style={{ padding: espaco.xl }}>
        <Text style={[fonte.pequeno, { textAlign: 'center' }]}>
          ao entrar você aceita os{' '}
          <Text
            style={{ color: cores.verdeEscuro, fontWeight: '600' }}
            onPress={() => Linking.openURL('https://vendasitinga.com.br/termos')}
          >
            termos de uso
          </Text>{' '}
          e a{' '}
          <Text
            style={{ color: cores.verdeEscuro, fontWeight: '600' }}
            onPress={() => Linking.openURL('https://vendasitinga.com.br/privacidade')}
          >
            política de privacidade
          </Text>
          .
        </Text>
      </View>
    </SafeAreaView>
  );
}

const e = StyleSheet.create({
  conteudo: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: espaco.xl,
  },
  botaoGoogle: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
    borderColor: cores.borda,
    borderRadius: raio.pilula,
    paddingVertical: 15,
    backgroundColor: cores.branco,
  },
  textoGoogle: {
    marginLeft: espaco.md,
    fontSize: 15,
    fontWeight: '700',
    color: cores.texto,
  },
});
