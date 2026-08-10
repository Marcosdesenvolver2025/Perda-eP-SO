/**
 * Aba "vendas": a lojinha de quem vende.
 *
 * Mesma organização do vídeo — capa e nome da loja no topo, cartões de atalho,
 * blocos de dica e o botão fixo de criar anúncio no rodapé.
 */

import React, { useCallback, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import { Avatar, Aviso, Botao, Cartao, Selo } from '../componentes/base';
import { useAutenticacao } from '../contextos/Autenticacao';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { reais } from '../util/formato';

type Props = NativeStackScreenProps<ParametrosApp, 'Abas'>;

interface Extrato {
  aReceber: number;
  recebido: number;
  diasParaLiberar: number;
  pedidos: unknown[];
}

export function TelaVendas({ navigation }: Props) {
  const { usuario } = useAutenticacao();
  const [extrato, setExtrato] = useState<Extrato | null>(null);
  const [anunciosAtivos, setAnunciosAtivos] = useState(0);

  useFocusEffect(
    useCallback(() => {
      async function carregar() {
        if (MODO_DEMONSTRACAO) {
          setExtrato({ aReceber: 9_840, recebido: 24_500, diasParaLiberar: 4, pedidos: [] });
          setAnunciosAtivos(3);
          return;
        }
        try {
          const [dados, meus] = await Promise.all([
            api<Extrato>('/pedidos/vendas'),
            api<{ itens: unknown[] }>('/anuncios/meus/lista'),
          ]);
          setExtrato(dados);
          setAnunciosAtivos(meus.itens.length);
        } catch {
          // sem sessão ou servidor fora: a tela ainda mostra os atalhos
        }
      }
      void carregar();
    }, []),
  );

  const precisaCadastrarConta = usuario?.recebedor !== 'ATIVO';

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <ScrollView contentContainerStyle={{ paddingBottom: 120 }}>
        <View style={e.capa} />

        <View style={{ paddingHorizontal: espaco.lg, marginTop: -28 }}>
          <View style={e.avatarBorda}>
            <Avatar url={usuario?.fotoUrl} nome={usuario?.nome} tamanho={64} />
          </View>

          <Pressable
            onPress={() => navigation.navigate('MinhaLoja')}
            style={{ flexDirection: 'row', alignItems: 'center', marginTop: espaco.md }}
            accessibilityRole="button"
          >
            <View style={{ flex: 1 }}>
              <Text style={fonte.secao}>
                {(usuario?.apelidoLoja ?? 'minha lojinha').toLowerCase()}
              </Text>
              <Text style={fonte.pequeno}>
                {anunciosAtivos} {anunciosAtivos === 1 ? 'anúncio' : 'anúncios'} · Itinga
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={22} color={cores.textoFraco} />
          </Pressable>
        </View>

        {precisaCadastrarConta ? (
          <View style={{ paddingHorizontal: espaco.lg, marginTop: espaco.lg }}>
            <Cartao aoTocar={() => navigation.navigate('ContaDeRecebimento')}>
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <Ionicons name="wallet-outline" size={24} color={cores.verdeEscuro} />
                <View style={{ flex: 1, marginLeft: espaco.md }}>
                  <Text style={fonte.rotulo}>cadastre sua conta pra receber</Text>
                  <Text style={fonte.pequeno}>
                    sem isso a gente não consegue depositar o valor das suas vendas
                  </Text>
                </View>
                <Ionicons name="chevron-forward" size={20} color={cores.textoFraco} />
              </View>
            </Cartao>
          </View>
        ) : null}

        <View style={e.saldo}>
          <View style={{ flex: 1 }}>
            <Text style={{ fontSize: 13, color: cores.verdeProfundo }}>a receber</Text>
            <Text style={{ fontSize: 26, fontWeight: '700', color: cores.preto }}>
              {reais(extrato?.aReceber ?? 0)}
            </Text>
            <Text style={{ fontSize: 12, color: cores.verdeProfundo, marginTop: 2 }}>
              liberado {extrato?.diasParaLiberar ?? 4} dias após a entrega
            </Text>
          </View>
          <Ionicons name="cash-outline" size={44} color={cores.verdeProfundo} style={{ opacity: 0.4 }} />
        </View>

        <View style={e.atalhos}>
          <CartaoAtalho
            titulo={'produtos\nà venda'}
            descricao="bota pra vender o que não usa mais"
            aoTocar={() => navigation.navigate('MinhaLoja')}
          />
          <CartaoAtalho
            titulo={'minhas\nvendas'}
            descricao="acompanhe e receba"
            aoTocar={() => navigation.navigate('MinhasVendas')}
          />
        </View>

        <View style={{ backgroundColor: cores.fundoCinza, paddingVertical: espaco.xl, marginTop: espaco.lg }}>
          <Text style={[fonte.secao, { paddingHorizontal: espaco.lg, marginBottom: espaco.md }]}>
            pra bombar sua loja
          </Text>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={{ paddingHorizontal: espaco.lg, gap: espaco.md }}
          >
            <CartaoDica
              icone="camera-outline"
              titulo="boas fotos"
              texto="fundo limpo e luz do dia vendem mais rápido"
            />
            <CartaoDica
              icone="pricetag-outline"
              titulo="preço justo"
              texto="pesquise quanto está valendo aqui na cidade"
            />
            <CartaoDica
              icone="chatbubble-ellipses-outline"
              titulo="responda rápido"
              texto="quem responde no mesmo dia vende mais"
            />
          </ScrollView>
        </View>

        <View style={{ padding: espaco.lg }}>
          <View style={e.faixaTaxas}>
            <Text style={{ color: cores.branco, fontSize: 17, fontWeight: '700' }}>
              quanto a gente cobra
            </Text>
            <Text style={{ color: cores.branco, marginTop: espaco.sm, fontSize: 14 }}>
              16% por venda quando você mesmo entrega
            </Text>
            <Text style={{ color: cores.branco, fontSize: 14 }}>
              18% quando o entregador do vendas itinga leva
            </Text>
            <Text style={{ color: cores.branco, marginTop: espaco.sm, fontSize: 12, opacity: 0.9 }}>
              já inclui o pagamento, a entrega e o suporte. sem mensalidade.
            </Text>
          </View>

          <View style={{ marginTop: espaco.lg }}>
            <Aviso
              texto="limite dos nossos entregadores: até 20 kg e 60 cm em qualquer lado do pacote."
              tom="informacao"
            />
          </View>

          <Cartao
            aoTocar={() => navigation.navigate('ComoFunciona')}
            estilo={{ marginTop: espaco.sm }}
          >
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
              <Ionicons name="help-circle-outline" size={22} color={cores.verdeEscuro} />
              <View style={{ flex: 1, marginLeft: espaco.md }}>
                <Text style={fonte.rotulo}>central de ajuda</Text>
                <Text style={fonte.pequeno}>como vender, receber e devolver</Text>
              </View>
              <Ionicons name="chevron-forward" size={20} color={cores.textoFraco} />
            </View>
          </Cartao>
        </View>
      </ScrollView>

      <View style={e.rodape}>
        <Botao
          titulo="criar novo anúncio"
          icone="add"
          aoTocar={() => navigation.navigate('NovoAnuncio')}
        />
      </View>
    </SafeAreaView>
  );
}

function CartaoAtalho({
  titulo,
  descricao,
  aoTocar,
}: {
  titulo: string;
  descricao: string;
  aoTocar: () => void;
}) {
  return (
    <Pressable onPress={aoTocar} style={e.atalho} accessibilityRole="button">
      <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
        <Text style={[fonte.rotulo, { fontSize: 15, flex: 1 }]}>{titulo}</Text>
        <Ionicons name="chevron-forward" size={18} color={cores.verdeEscuro} />
      </View>
      <Text style={[fonte.pequeno, { marginTop: espaco.xl }]}>{descricao}</Text>
    </Pressable>
  );
}

function CartaoDica({
  icone,
  titulo,
  texto,
}: {
  icone: keyof typeof Ionicons.glyphMap;
  titulo: string;
  texto: string;
}) {
  return (
    <View style={e.dica}>
      <Ionicons name={icone} size={26} color={cores.verdeEscuro} />
      <Text style={[fonte.rotulo, { marginTop: espaco.md }]}>{titulo}</Text>
      <Text style={[fonte.pequeno, { marginTop: 4 }]}>{texto}</Text>
    </View>
  );
}

const e = StyleSheet.create({
  capa: { height: 96, backgroundColor: cores.verde },
  avatarBorda: {
    borderWidth: 3,
    borderColor: cores.branco,
    borderRadius: 40,
    alignSelf: 'flex-start',
  },
  saldo: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: cores.verdeSuave,
    borderRadius: raio.lg,
    padding: espaco.lg,
    marginHorizontal: espaco.lg,
    marginTop: espaco.lg,
  },
  atalhos: {
    flexDirection: 'row',
    gap: espaco.md,
    paddingHorizontal: espaco.lg,
    marginTop: espaco.lg,
  },
  atalho: {
    flex: 1,
    backgroundColor: cores.fundoCinza,
    borderRadius: raio.lg,
    padding: espaco.lg,
    minHeight: 120,
  },
  dica: {
    width: 180,
    backgroundColor: cores.branco,
    borderRadius: raio.lg,
    padding: espaco.lg,
  },
  faixaTaxas: {
    backgroundColor: cores.verdeEscuro,
    borderRadius: raio.lg,
    padding: espaco.lg,
  },
  rodape: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    padding: espaco.lg,
    backgroundColor: cores.branco,
    borderTopWidth: 1,
    borderTopColor: cores.borda,
  },
});
