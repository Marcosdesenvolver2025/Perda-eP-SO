/**
 * Home: a vitrine.
 *
 * Mesma estrutura do vídeo — topo com a marca, sacola e avatar; barra de
 * busca; abas de categoria; e uma sequência de carrosséis com título,
 * subtítulo e botão vazado.
 */

import React, { useCallback, useEffect, useState } from 'react';
import {
  Image,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import type { Anuncio } from '../api/tipos';
import {
  AbasDeCategoria,
  Avatar,
  BarraDeBusca,
  Carregando,
  TelaVazia,
} from '../componentes/base';
import { CarrosselDeProdutos, FaixaDestaque } from '../componentes/produto';
import { useAutenticacao } from '../contextos/Autenticacao';
import { anunciosDemo, categoriasDemo } from '../dados/exemplo';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte } from '../tema';

type Props = NativeStackScreenProps<ParametrosApp, 'Abas'>;

export function TelaHome({ navigation }: Props) {
  const { usuario } = useAutenticacao();
  const [categoria, setCategoria] = useState('todos');
  const [anuncios, setAnuncios] = useState<Anuncio[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [atualizando, setAtualizando] = useState(false);

  const carregar = useCallback(async () => {
    if (MODO_DEMONSTRACAO) {
      setAnuncios(anunciosDemo);
      setCarregando(false);
      return;
    }
    try {
      const resposta = await api<{ itens: Anuncio[] }>(
        `/anuncios?porPagina=40${categoria === 'todos' ? '' : `&categoria=${categoria}`}`,
        { publico: true },
      );
      setAnuncios(resposta.itens);
    } catch {
      setAnuncios([]);
    } finally {
      setCarregando(false);
      setAtualizando(false);
    }
  }, [categoria]);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  const abrirProduto = (id: string) => navigation.navigate('Produto', { id });
  const abrirBusca = (filtro?: string) => navigation.navigate('Busca', { termo: filtro });

  // as seções são recortes do mesmo catálogo — em Itinga o estoque é pequeno,
  // então repetir produto em seções diferentes é esperado.
  const novidades = anuncios.slice(0, 8);
  const abaixoDeCem = anuncios.filter((a) => a.preco <= 10_000).slice(0, 8);
  const semiNovos = anuncios.filter((a) => a.condicao !== 'USADO').slice(0, 8);
  const perto = anuncios.filter((a) => a.vendedor?.bairro).slice(0, 8);

  if (carregando) return <Carregando />;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <View style={e.marca}>
          <Image
            source={require('../../assets/logo-horizontal.png')}
            style={{ width: 132, height: 40 }}
            resizeMode="contain"
            accessibilityLabel="Vendas Itinga"
          />
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: espaco.lg }}>
          <Pressable
            onPress={() => navigation.navigate('MinhasCompras')}
            accessibilityRole="button"
            accessibilityLabel="minhas compras"
          >
            <Ionicons name="bag-handle-outline" size={26} color={cores.verdeEscuro} />
          </Pressable>
          <Pressable
            onPress={() => navigation.navigate('Abas', { screen: 'MinhaConta' })}
            accessibilityRole="button"
            accessibilityLabel="minha conta"
          >
            <Avatar url={usuario?.fotoUrl} nome={usuario?.nome} tamanho={34} />
          </Pressable>
        </View>
      </View>

      <BarraDeBusca somenteLeitura aoTocar={() => abrirBusca()} />

      <View style={{ marginTop: espaco.md }}>
        <AbasDeCategoria itens={categoriasDemo} ativa={categoria} aoTrocar={setCategoria} />
      </View>

      <ScrollView
        contentContainerStyle={{ paddingTop: espaco.xl, paddingBottom: espaco.xxl }}
        refreshControl={
          <RefreshControl
            refreshing={atualizando}
            onRefresh={() => {
              setAtualizando(true);
              void carregar();
            }}
            colors={[cores.verde]}
            tintColor={cores.verde}
          />
        }
      >
        {anuncios.length === 0 ? (
          <TelaVazia
            icone="storefront-outline"
            titulo="ainda não tem nada por aqui"
            descricao="seja a primeira pessoa a anunciar em Itinga."
            acao={{
              titulo: 'criar meu anúncio',
              aoTocar: () => navigation.navigate('NovoAnuncio'),
            }}
          />
        ) : (
          <>
            <CarrosselDeProdutos
              titulo="chegou agora"
              subtitulo="o que o pessoal acabou de anunciar"
              anuncios={novidades}
              textoDoBotao="ver tudo"
              aoTocarProduto={abrirProduto}
              aoVerTudo={() => abrirBusca()}
            />

            <FaixaDestaque
              titulo="a gente entrega pra você"
              descricao="até 20 kg e 60 cm, com 7 dias pra testar em casa"
              textoDoBotao="como funciona"
              aoTocar={() => navigation.navigate('ComoFunciona')}
              icone="bicycle"
            />

            <CarrosselDeProdutos
              titulo="até R$ 100"
              subtitulo="achados que cabem no bolso"
              anuncios={abaixoDeCem}
              textoDoBotao="quero ver"
              aoTocarProduto={abrirProduto}
              aoVerTudo={() => abrirBusca()}
            />

            <CarrosselDeProdutos
              titulo="novos e seminovos"
              subtitulo="quase saindo da caixa"
              anuncios={semiNovos}
              textoDoBotao="espia só"
              aoTocarProduto={abrirProduto}
              aoVerTudo={() => abrirBusca()}
            />

            <FaixaDestaque
              titulo="venda o que não usa mais"
              descricao="tire foto, anuncie e receba direto na sua conta"
              textoDoBotao="anunciar agora"
              aoTocar={() => navigation.navigate('NovoAnuncio')}
              icone="pricetag"
            />

            <CarrosselDeProdutos
              titulo="pertinho de você"
              subtitulo="tudo dentro de Itinga"
              anuncios={perto}
              textoDoBotao="ver a cidade toda"
              aoTocarProduto={abrirProduto}
              aoVerTudo={() => abrirBusca()}
            />

            <Text style={[fonte.pequeno, { textAlign: 'center', marginTop: espaco.lg }]}>
              vendas itinga · compre e venda na sua cidade
            </Text>
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const e = StyleSheet.create({
  topo: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: espaco.lg,
    paddingBottom: espaco.md,
  },
  marca: { flexDirection: 'row', alignItems: 'center' },
});
