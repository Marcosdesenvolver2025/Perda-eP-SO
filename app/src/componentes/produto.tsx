/**
 * Cards de produto e carrossel da home.
 *
 * O formato copia o do vídeo: foto quadrada, título curto embaixo e, no
 * carrossel, um botão vazado fechando a seção.
 */

import React from 'react';
import {
  FlatList,
  Image,
  Pressable,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import type { Anuncio } from '../api/tipos';
import { cores, espaco, fonte, raio, sombra } from '../tema';
import { precoCurto } from '../util/formato';
import { Botao, Selo } from './base';

const rotuloCondicao: Record<string, string> = {
  NOVO: 'novo',
  SEMINOVO: 'seminovo',
  USADO: 'usado',
};

export function CardProduto({
  anuncio,
  aoTocar,
  aoCurtir,
  largura,
}: {
  anuncio: Anuncio;
  aoTocar: (id: string) => void;
  /** Quando informado, o card mostra o coração de curtir. */
  aoCurtir?: (id: string) => void;
  largura?: number;
}) {
  const foto = anuncio.fotos?.[0]?.url;
  const desconto =
    anuncio.precoOriginal && anuncio.precoOriginal > anuncio.preco
      ? Math.round((1 - anuncio.preco / anuncio.precoOriginal) * 100)
      : null;

  return (
    <Pressable
      onPress={() => aoTocar(anuncio.id)}
      accessibilityRole="button"
      accessibilityLabel={`${anuncio.titulo}, ${precoCurto(anuncio.preco)}`}
      style={({ pressed }) => [
        { width: largura ?? 168 },
        pressed && { transform: [{ scale: 0.97 }] },
      ]}
    >
      <View
        style={[
          e.foto,
          { width: largura ?? 168, height: Math.round((largura ?? 168) * 1.15) },
        ]}
      >
        {foto ? (
          <Image source={{ uri: foto }} style={e.imagem} accessibilityIgnoresInvertColors />
        ) : (
          <Ionicons name="image-outline" size={28} color={cores.textoFraco} />
        )}
        {desconto ? (
          <View style={e.desconto}>
            <Text style={e.descontoTexto}>-{desconto}%</Text>
          </View>
        ) : null}

        {aoCurtir ? (
          <Pressable
            onPress={() => aoCurtir(anuncio.id)}
            style={e.coracao}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel={anuncio.curtido ? 'descurtir' : 'curtir'}
            accessibilityState={{ selected: !!anuncio.curtido }}
          >
            <Ionicons
              name={anuncio.curtido ? 'heart' : 'heart-outline'}
              size={18}
              color={anuncio.curtido ? cores.coral : cores.texto}
            />
          </Pressable>
        ) : null}
      </View>

      {/* preço primeiro, título depois: em vitrine de usados a decisão
          começa no valor, e o olho procura o número antes do nome */}
      <View style={e.linhaPreco}>
        <Text style={fonte.preco}>{precoCurto(anuncio.preco)}</Text>
        {anuncio.precoOriginal && anuncio.precoOriginal > anuncio.preco ? (
          <Text style={e.precoAntigo}>{precoCurto(anuncio.precoOriginal)}</Text>
        ) : null}
      </View>

      <Text numberOfLines={1} style={e.tituloCard}>
        {anuncio.titulo.toLowerCase()}
      </Text>

      <View style={e.linhaMeta}>
        <View style={e.pontoCondicao} />
        <Text style={e.meta} numberOfLines={1}>
          {rotuloCondicao[anuncio.condicao] ?? 'usado'}
          {anuncio.vendedor?.bairro ? ` · ${anuncio.vendedor.bairro.toLowerCase()}` : ''}
        </Text>
      </View>
    </Pressable>
  );
}

/** Seção horizontal com título, subtítulo, cards e botão de ver tudo. */
export function CarrosselDeProdutos({
  aoCurtir,
  titulo,
  subtitulo,
  anuncios,
  textoDoBotao,
  aoTocarProduto,
  aoVerTudo,
}: {
  titulo: string;
  subtitulo?: string;
  anuncios: Anuncio[];
  textoDoBotao: string;
  aoTocarProduto: (id: string) => void;
  aoCurtir?: (id: string) => void;
  aoVerTudo: () => void;
}) {
  if (anuncios.length === 0) return null;

  return (
    <View style={{ marginBottom: espaco.xxl }}>
      <View style={{ paddingHorizontal: espaco.lg, marginBottom: espaco.md }}>
        <Text style={fonte.secao}>{titulo}</Text>
        {subtitulo ? <Text style={[fonte.subtitulo, { marginTop: 2 }]}>{subtitulo}</Text> : null}
      </View>

      <FlatList
        horizontal
        data={anuncios}
        keyExtractor={(item) => item.id}
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ paddingHorizontal: espaco.lg, gap: espaco.md }}
        renderItem={({ item }) => (
          <CardProduto anuncio={item} aoTocar={aoTocarProduto} aoCurtir={aoCurtir} />
        )}
      />

      <Botao
        titulo={textoDoBotao}
        variante="vazado"
        aoTocar={aoVerTudo}
        estilo={{ marginHorizontal: espaco.lg, marginTop: espaco.lg }}
      />
    </View>
  );
}

/** Grade de dois por linha, usada na busca e na lojinha. */
export function GradeDeProdutos({
  aoCurtir,
  anuncios,
  aoTocarProduto,
  cabecalho,
  rodape,
  aoFinalDaLista,
}: {
  anuncios: Anuncio[];
  aoTocarProduto: (id: string) => void;
  aoCurtir?: (id: string) => void;
  cabecalho?: React.ReactElement;
  rodape?: React.ReactElement;
  aoFinalDaLista?: () => void;
}) {
  const { width } = useWindowDimensions();
  const larguraCard = (width - espaco.lg * 2 - espaco.md) / 2;

  return (
    <FlatList
      data={anuncios}
      keyExtractor={(item) => item.id}
      numColumns={2}
      ListHeaderComponent={cabecalho}
      ListFooterComponent={rodape}
      columnWrapperStyle={{ gap: espaco.md, paddingHorizontal: espaco.lg }}
      contentContainerStyle={{ paddingBottom: espaco.xxl, gap: espaco.xl }}
      onEndReached={aoFinalDaLista}
      onEndReachedThreshold={0.4}
      renderItem={({ item }) => (
        <CardProduto
          anuncio={item}
          aoTocar={aoTocarProduto}
          aoCurtir={aoCurtir}
          largura={larguraCard}
        />
      )}
    />
  );
}

/** Faixa colorida da home, no lugar dos banners promocionais do vídeo. */
export function FaixaDestaque({
  titulo,
  descricao,
  textoDoBotao,
  aoTocar,
  icone = 'sparkles',
}: {
  titulo: string;
  descricao: string;
  textoDoBotao: string;
  aoTocar: () => void;
  icone?: keyof typeof Ionicons.glyphMap;
}) {
  return (
    <View style={e.faixa}>
      <View style={{ flex: 1 }}>
        <Text style={{ fontSize: 18, fontWeight: '700', color: cores.branco }}>{titulo}</Text>
        <Text style={{ fontSize: 13, color: cores.branco, marginTop: 4, opacity: 0.95 }}>
          {descricao}
        </Text>
        <Pressable onPress={aoTocar} style={e.faixaBotao} accessibilityRole="button">
          <Text style={{ color: cores.verdeProfundo, fontWeight: '700', fontSize: 13 }}>
            {textoDoBotao}
          </Text>
        </Pressable>
      </View>
      <Ionicons name={icone} size={54} color={cores.branco} style={{ opacity: 0.35 }} />
    </View>
  );
}

/** Selo de "7 dias pra testar" usado na página do produto e no pedido. */
export function SeloGarantia({ dias }: { dias: number }) {
  return (
    <View style={e.garantia}>
      <Ionicons name="shield-checkmark" size={20} color={cores.verdeProfundo} />
      <View style={{ flex: 1, marginLeft: espaco.md }}>
        <Text style={fonte.rotulo}>{dias} dias pra testar</Text>
        <Text style={fonte.pequeno}>
          contando da chegada do produto. não gostou? peça o reembolso pelo app.
        </Text>
      </View>
    </View>
  );
}

export function LinhaDeMedidas({
  pesoG,
  comprimentoCm,
  larguraCm,
  alturaCm,
}: {
  pesoG: number;
  comprimentoCm: number;
  larguraCm: number;
  alturaCm: number;
}) {
  return (
    <View style={{ flexDirection: 'row', gap: espaco.sm, flexWrap: 'wrap' }}>
      <Selo texto={`${(pesoG / 1000).toFixed(pesoG % 1000 === 0 ? 0 : 1)} kg`} tom="cinza" />
      <Selo texto={`${comprimentoCm}×${larguraCm}×${alturaCm} cm`} tom="cinza" />
    </View>
  );
}

const e = StyleSheet.create({
  foto: {
    backgroundColor: cores.fundoCinza,
    // foto bem mais arredondada que o resto: é o elemento que mais se repete
    // na tela, e o canto redondo é o que dá o ar de app novo
    borderRadius: raio.cartao,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: espaco.md,
  },
  imagem: { width: '100%', height: '100%' },
  coracao: {
    position: 'absolute',
    top: espaco.sm,
    right: espaco.sm,
    width: 34,
    height: 34,
    borderRadius: raio.pilula,
    backgroundColor: 'rgba(255,255,255,0.94)',
    alignItems: 'center',
    justifyContent: 'center',
    ...(sombra as object),
  },
  desconto: {
    position: 'absolute',
    top: espaco.sm,
    left: espaco.sm,
    // desconto em âmbar, não em verde: verde aqui competiria com o botão de
    // comprar e diluiria o significado da cor da marca
    backgroundColor: cores.ambar,
    paddingHorizontal: 9,
    paddingVertical: 3,
    borderRadius: raio.pilula,
  },
  descontoTexto: { color: cores.preto, fontSize: 11, fontWeight: '800' },
  linhaPreco: { flexDirection: 'row', alignItems: 'baseline', gap: 6 },
  precoAntigo: {
    fontSize: 12,
    color: cores.textoFraco,
    textDecorationLine: 'line-through',
  },
  tituloCard: {
    fontSize: 13,
    fontWeight: '500',
    color: cores.textoSuave,
    marginTop: 1,
  },
  linhaMeta: { flexDirection: 'row', alignItems: 'center', gap: 5, marginTop: 5 },
  pontoCondicao: {
    width: 5,
    height: 5,
    borderRadius: 3,
    backgroundColor: cores.verde,
  },
  meta: { flex: 1, fontSize: 11, fontWeight: '600', color: cores.textoFraco },
  faixa: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: cores.verde,
    borderRadius: raio.lg,
    padding: espaco.lg,
    marginHorizontal: espaco.lg,
    marginBottom: espaco.xxl,
  },
  faixaBotao: {
    backgroundColor: cores.branco,
    alignSelf: 'flex-start',
    paddingHorizontal: espaco.lg,
    paddingVertical: espaco.sm,
    borderRadius: raio.pilula,
    marginTop: espaco.md,
  },
  garantia: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: cores.verdeClaro,
    borderRadius: raio.md,
    padding: espaco.md,
  },
});
