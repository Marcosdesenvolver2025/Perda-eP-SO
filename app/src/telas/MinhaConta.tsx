/**
 * Aba "minha conta" — equivale ao "meu enjoei" do vídeo.
 *
 * Cabeçalho com a saudação, cartão verde de destaque e a lista de atalhos.
 */

import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { Avatar, Botao, ItemDeMenu, Separador } from '../componentes/base';
import { useAutenticacao } from '../contextos/Autenticacao';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';

type Props = NativeStackScreenProps<ParametrosApp, 'Abas'>;

/** Saudação que muda a cada abertura, como no app de referência. */
function saudacao(nome: string): string {
  const primeiro = nome.split(' ')[0]?.toLowerCase() ?? 'você';
  const opcoes = [`oi, ${primeiro}!`, `fala, ${primeiro}!`, `${primeiro}, tá bom?`];
  return opcoes[Math.floor(Date.now() / 60_000) % opcoes.length]!;
}

export function TelaMinhaConta({ navigation }: Props) {
  const { usuario, sair } = useAutenticacao();

  if (!usuario) {
    return (
      <SafeAreaView style={{ flex: 1, justifyContent: 'center', padding: espaco.xl }}>
        <Text style={[fonte.secao, { textAlign: 'center' }]}>entre pra ver sua conta</Text>
        <Text style={[fonte.subtitulo, { textAlign: 'center', marginTop: espaco.sm }]}>
          suas compras, vendas e favoritos ficam salvos na sua conta google.
        </Text>
        <Botao
          titulo="entrar"
          aoTocar={() => navigation.navigate('Entrar')}
          estilo={{ marginTop: espaco.xl }}
        />
      </SafeAreaView>
    );
  }

  const ehEntregador = usuario.papel === 'ENTREGADOR' || usuario.papel === 'ADMIN';

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <ScrollView contentContainerStyle={{ paddingBottom: espaco.xxl }}>
        <View style={{ padding: espaco.lg }}>
          <Avatar url={usuario.fotoUrl} nome={usuario.nome} tamanho={72} />

          <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: espaco.md }}>
            <View style={{ flex: 1 }}>
              <Text style={fonte.titulo}>{saudacao(usuario.nome)}</Text>
              <Text style={fonte.subtitulo}>
                no vendas itinga desde {new Date().getFullYear()}
              </Text>
            </View>
            <Pressable
              onPress={() => navigation.navigate('MinhaLoja')}
              style={e.botaoLoja}
              accessibilityRole="button"
            >
              <Text style={{ color: cores.verdeEscuro, fontWeight: '700', fontSize: 13 }}>
                minha loja
              </Text>
            </Pressable>
          </View>
        </View>

        <Pressable
          onPress={() => navigation.navigate('MinhasVendas')}
          style={e.cartaoVerde}
          accessibilityRole="button"
        >
          <View style={{ flex: 1 }}>
            <Text style={{ fontSize: 15, fontWeight: '700', color: cores.preto }}>
              acompanhe o que você tem a receber
            </Text>
            <Text style={{ fontSize: 13, color: cores.verdeProfundo, marginTop: espaco.sm }}>
              o dinheiro cai 4 dias depois que o produto chega
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={22} color={cores.verdeProfundo} />
        </Pressable>

        <View style={{ marginTop: espaco.lg }}>
          <ItemDeMenu
            titulo="minhas compras"
            icone="bag-handle-outline"
            aoTocar={() => navigation.navigate('MinhasCompras')}
          />
          <Separador />
          <ItemDeMenu
            titulo="minhas vendas"
            icone="storefront-outline"
            aoTocar={() => navigation.navigate('MinhasVendas')}
          />
          <Separador />
          <ItemDeMenu
            titulo="minha lojinha"
            icone="pricetags-outline"
            aoTocar={() => navigation.navigate('MinhaLoja')}
          />
          <Separador />
          <ItemDeMenu
            titulo="conta pra receber"
            descricao={
              usuario.recebedor === 'ATIVO'
                ? 'tudo certo, você já pode vender'
                : usuario.recebedor === 'PENDENTE'
                  ? 'em análise pela pagar.me'
                  : 'cadastre para receber suas vendas'
            }
            icone="wallet-outline"
            aoTocar={() => navigation.navigate('ContaDeRecebimento')}
          />
          <Separador />
          <ItemDeMenu
            titulo="endereços"
            icone="location-outline"
            aoTocar={() => navigation.navigate('Enderecos')}
          />

          {ehEntregador ? (
            <>
              <Separador />
              <ItemDeMenu
                titulo="área do entregador"
                descricao="entregas disponíveis e as suas corridas"
                icone="bicycle-outline"
                aoTocar={() => navigation.navigate('AreaDoEntregador')}
              />
            </>
          ) : null}

          <Separador />
          <ItemDeMenu
            titulo="como funciona"
            icone="help-circle-outline"
            aoTocar={() => navigation.navigate('ComoFunciona')}
          />
          <Separador />
          <ItemDeMenu
            titulo="configurações"
            icone="settings-outline"
            aoTocar={() => navigation.navigate('Configuracoes')}
          />
        </View>

        <Botao
          titulo="sair da conta"
          variante="texto"
          aoTocar={sair}
          estilo={{ marginTop: espaco.xl }}
        />

        <Text style={[fonte.pequeno, { textAlign: 'center', marginTop: espaco.lg }]}>
          vendas itinga · versão 1.0.0
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const e = StyleSheet.create({
  botaoLoja: {
    borderWidth: 1.5,
    borderColor: cores.verdeEscuro,
    borderRadius: raio.pilula,
    paddingHorizontal: espaco.lg,
    paddingVertical: espaco.sm,
  },
  cartaoVerde: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: cores.verdeSuave,
    borderRadius: raio.lg,
    padding: espaco.lg,
    marginHorizontal: espaco.lg,
  },
});
