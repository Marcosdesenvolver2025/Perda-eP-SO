/**
 * Configurações e exclusão de conta.
 *
 * A exclusão precisa existir dentro do app: a Google Play exige um caminho
 * claro para o usuário apagar a conta e os dados dele.
 */

import React, { useState } from 'react';
import { Alert, Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import * as Linking from 'expo-linking';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import { Aviso, Botao, ItemDeMenu, Separador } from '../componentes/base';
import { useAutenticacao } from '../contextos/Autenticacao';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte } from '../tema';

type Props = NativeStackScreenProps<ParametrosApp, 'Configuracoes'>;

const URL_PRIVACIDADE = 'https://vendasitinga.com.br/privacidade';
const URL_TERMOS = 'https://vendasitinga.com.br/termos';

export function TelaConfiguracoes({ navigation }: Props) {
  const { usuario, sair } = useAutenticacao();
  const [excluindo, setExcluindo] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  function confirmarExclusao() {
    Alert.alert(
      'excluir sua conta?',
      'seus dados pessoais, anúncios e endereços serão apagados. os registros de vendas já concluídas ficam guardados por exigência fiscal, mas sem seus dados pessoais. essa ação não tem volta.',
      [
        { text: 'cancelar', style: 'cancel' },
        { text: 'excluir minha conta', style: 'destructive', onPress: excluir },
      ],
    );
  }

  async function excluir() {
    setErro(null);

    if (MODO_DEMONSTRACAO) {
      setErro('Modo demonstração: conecte o servidor para excluir a conta de verdade.');
      return;
    }

    setExcluindo(true);
    try {
      await api('/conta', { metodo: 'DELETE' });
      await sair();
      navigation.navigate('Abas', { screen: 'Home' });
    } catch (e) {
      // o servidor recusa se houver pedido em andamento — a mensagem explica
      setErro((e as Error).message);
    } finally {
      setExcluindo(false);
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={{ flexDirection: 'row', alignItems: 'center', padding: espaco.lg }}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>configurações</Text>
      </View>

      <ScrollView contentContainerStyle={{ paddingBottom: espaco.xxl }}>
        {erro ? (
          <View style={{ paddingHorizontal: espaco.lg }}>
            <Aviso texto={erro} tom="alerta" />
          </View>
        ) : null}

        <ItemDeMenu
          titulo="minha loja"
          descricao={usuario?.apelidoLoja ?? 'defina o nome da sua lojinha'}
          icone="storefront-outline"
          aoTocar={() => navigation.navigate('MinhaLoja')}
        />
        <Separador />
        <ItemDeMenu
          titulo="dados pessoais"
          descricao={usuario?.email}
          icone="person-outline"
          aoTocar={() => navigation.navigate('DadosPessoais')}
        />
        <Separador />
        <ItemDeMenu
          titulo="conta pra receber"
          icone="wallet-outline"
          aoTocar={() => navigation.navigate('ContaDeRecebimento')}
        />
        <Separador />
        <ItemDeMenu
          titulo="endereços"
          icone="location-outline"
          aoTocar={() => navigation.navigate('Enderecos')}
        />
        <Separador />
        <ItemDeMenu
          titulo="notificações"
          icone="notifications-outline"
          aoTocar={() => Linking.openSettings()}
        />
        <Separador />
        <ItemDeMenu
          titulo="política de privacidade"
          icone="shield-checkmark-outline"
          aoTocar={() => Linking.openURL(URL_PRIVACIDADE)}
        />
        <Separador />
        <ItemDeMenu
          titulo="termos de uso"
          icone="document-text-outline"
          aoTocar={() => Linking.openURL(URL_TERMOS)}
        />
        <Separador />

        <View style={{ marginTop: espaco.xxl }}>
          <ItemDeMenu
            titulo="excluir minha conta"
            descricao="apaga seus dados pessoais deste app"
            icone="trash-outline"
            destaque
            aoTocar={confirmarExclusao}
          />
        </View>

        {excluindo ? (
          <Botao titulo="excluindo..." aoTocar={() => undefined} carregando estilo={{ margin: espaco.lg }} />
        ) : null}

        <Text style={[fonte.pequeno, { textAlign: 'center', marginTop: espaco.xl, paddingHorizontal: espaco.xl }]}>
          você também pode pedir a exclusão pelo site vendasitinga.com.br/excluir-conta
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}
