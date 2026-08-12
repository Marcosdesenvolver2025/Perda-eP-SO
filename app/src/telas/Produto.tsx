/**
 * Página do produto: fotos, preço, vendedor, entrega e o botão de comprar.
 */

import React, { useEffect, useState } from 'react';
import {
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api } from '../api/cliente';
import type { Anuncio } from '../api/tipos';
import { Avatar, Botao, Carregando, Selo, Separador, TelaVazia } from '../componentes/base';
import { LinhaDeMedidas, SeloGarantia } from '../componentes/produto';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { parcelamento, precoCurto, quando } from '../util/formato';

type Props = NativeStackScreenProps<ParametrosApp, 'Produto'>;

const rotuloCondicao: Record<string, string> = {
  NOVO: 'novo, sem uso',
  SEMINOVO: 'seminovo',
  USADO: 'usado',
};

export function TelaProduto({ navigation, route }: Props) {
  const { width } = useWindowDimensions();
  const [anuncio, setAnuncio] = useState<Anuncio | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [fotoAtual, setFotoAtual] = useState(0);

  useEffect(() => {
    async function carregar() {
      try {
        setAnuncio(await api<Anuncio>(`/anuncios/${route.params.id}`, { publico: true }));
      } catch {
        setAnuncio(null);
      } finally {
        setCarregando(false);
      }
    }
    void carregar();
  }, [route.params.id]);

  if (carregando) return <Carregando />;
  if (!anuncio) {
    return (
      <TelaVazia
        icone="alert-circle-outline"
        titulo="anúncio indisponível"
        descricao="ele pode ter sido vendido ou tirado do ar."
        acao={{ titulo: 'voltar', aoTocar: () => navigation.goBack() }}
      />
    );
  }

  const diasParaTestar = anuncio.entrega?.diasParaTestar ?? 7;
  const parcelas = parcelamento(anuncio.preco);

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <ScrollView contentContainerStyle={{ paddingBottom: 120 }}>
        <View>
          <ScrollView
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            onMomentumScrollEnd={(evento) =>
              setFotoAtual(Math.round(evento.nativeEvent.contentOffset.x / width))
            }
          >
            {(anuncio.fotos.length ? anuncio.fotos : [{ id: 'vazia', url: '', ordem: 0 }]).map(
              (foto) => (
                <View key={foto.id} style={[e.foto, { width, height: width }]}>
                  {foto.url ? (
                    <Image
                      source={{ uri: foto.url }}
                      style={{ width, height: width }}
                      accessibilityIgnoresInvertColors
                    />
                  ) : (
                    <Ionicons name="image-outline" size={48} color={cores.textoFraco} />
                  )}
                </View>
              ),
            )}
          </ScrollView>

          <Pressable
            onPress={() => navigation.goBack()}
            style={e.voltar}
            hitSlop={10}
            accessibilityRole="button"
            accessibilityLabel="voltar"
          >
            <Ionicons name="chevron-back" size={24} color={cores.texto} />
          </Pressable>

          {anuncio.fotos.length > 1 ? (
            <View style={e.pontos}>
              {anuncio.fotos.map((foto, indice) => (
                <View
                  key={foto.id}
                  style={[e.ponto, indice === fotoAtual && { backgroundColor: cores.verde }]}
                />
              ))}
            </View>
          ) : null}
        </View>

        <View style={{ padding: espaco.lg }}>
          <Text style={[fonte.titulo, { fontSize: 20 }]}>{anuncio.titulo.toLowerCase()}</Text>

          <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: espaco.sm, marginTop: espaco.sm }}>
            <Text style={{ fontSize: 26, fontWeight: '700', color: cores.texto }}>
              {precoCurto(anuncio.preco)}
            </Text>
            {anuncio.precoOriginal && anuncio.precoOriginal > anuncio.preco ? (
              <Text style={e.precoAntigo}>{precoCurto(anuncio.precoOriginal)}</Text>
            ) : null}
          </View>
          {parcelas ? <Text style={fonte.pequeno}>{parcelas} no cartão</Text> : null}

          <View style={{ flexDirection: 'row', gap: espaco.sm, marginTop: espaco.md, flexWrap: 'wrap' }}>
            <Selo texto={rotuloCondicao[anuncio.condicao] ?? 'usado'} />
            {anuncio.marca ? <Selo texto={anuncio.marca.toLowerCase()} tom="cinza" /> : null}
            {anuncio.tamanho ? <Selo texto={`tam ${anuncio.tamanho}`} tom="cinza" /> : null}
          </View>
        </View>

        <Separador />

        <Pressable
          style={e.vendedor}
          onPress={() => navigation.navigate('Loja', { vendedorId: anuncio.vendedor.id })}
          accessibilityRole="button"
        >
          <Avatar url={anuncio.vendedor.fotoUrl} nome={anuncio.vendedor.nome} tamanho={44} />
          <View style={{ flex: 1, marginLeft: espaco.md }}>
            <Text style={fonte.rotulo}>
              {(anuncio.vendedor.apelidoLoja ?? anuncio.vendedor.nome).toLowerCase()}
            </Text>
            <Text style={fonte.pequeno}>
              {anuncio.vendedor.notaMedia
                ? `⭐ ${anuncio.vendedor.notaMedia} · ${anuncio.vendedor.totalAvaliacoes} avaliações`
                : 'sem avaliações ainda'}
              {anuncio.vendedor.bairro ? ` · ${anuncio.vendedor.bairro.toLowerCase()}` : ''}
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color={cores.textoFraco} />
        </Pressable>

        <Separador />

        <View style={{ padding: espaco.lg, gap: espaco.lg }}>
          <SeloGarantia dias={diasParaTestar} />

          <View>
            <Text style={[fonte.rotulo, { marginBottom: espaco.sm }]}>entrega</Text>
            {anuncio.modalidadeEntrega === 'PLATAFORMA' ? (
              <View style={e.linhaEntrega}>
                <Ionicons name="bicycle-outline" size={20} color={cores.verdeEscuro} />
                <View style={{ flex: 1, marginLeft: espaco.md }}>
                  <Text style={fonte.corpo}>entregador do vendas itinga</Text>
                  <Text style={fonte.pequeno}>buscamos com quem vende e levamos até você</Text>
                </View>
                <Text style={[fonte.rotulo, { color: cores.verdeEscuro }]}>incluída</Text>
              </View>
            ) : (
              <View style={e.linhaEntrega}>
                <Ionicons name="walk-outline" size={20} color={cores.verdeEscuro} />
                <View style={{ flex: 1, marginLeft: espaco.md }}>
                  <Text style={fonte.corpo}>quem vende entrega</Text>
                  <Text style={fonte.pequeno}>vocês combinam onde e quando</Text>
                </View>
                <Text style={[fonte.rotulo, { color: cores.verdeEscuro }]}>sem frete</Text>
              </View>
            )}
          </View>

          <View>
            <Text style={[fonte.rotulo, { marginBottom: espaco.sm }]}>tamanho e peso</Text>
            <LinhaDeMedidas
              pesoG={anuncio.pesoG}
              comprimentoCm={anuncio.comprimentoCm}
              larguraCm={anuncio.larguraCm}
              alturaCm={anuncio.alturaCm}
            />
          </View>

          <View>
            <Text style={[fonte.rotulo, { marginBottom: espaco.sm }]}>descrição</Text>
            <Text style={fonte.corpo}>{anuncio.descricao}</Text>
            <Text style={[fonte.pequeno, { marginTop: espaco.md }]}>
              anunciado {quando(anuncio.criadoEm)}
            </Text>
          </View>
        </View>
      </ScrollView>

      <View style={e.rodape}>
        <Botao
          titulo="comprar agora"
          aoTocar={() => navigation.navigate('Checkout', { anuncioId: anuncio.id })}
        />
      </View>
    </SafeAreaView>
  );
}

const e = StyleSheet.create({
  foto: {
    backgroundColor: cores.fundoCinza,
    alignItems: 'center',
    justifyContent: 'center',
  },
  voltar: {
    position: 'absolute',
    top: espaco.md,
    left: espaco.md,
    backgroundColor: cores.branco,
    borderRadius: raio.pilula,
    padding: espaco.sm,
  },
  pontos: {
    position: 'absolute',
    bottom: espaco.md,
    alignSelf: 'center',
    flexDirection: 'row',
    gap: 6,
  },
  ponto: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: cores.branco,
    opacity: 0.9,
  },
  precoAntigo: {
    fontSize: 15,
    color: cores.textoFraco,
    textDecorationLine: 'line-through',
  },
  vendedor: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: espaco.lg,
  },
  linhaEntrega: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: espaco.md,
    borderBottomWidth: 1,
    borderBottomColor: cores.borda,
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
