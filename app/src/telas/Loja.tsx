/**
 * Lojinha de outro vendedor: capa, nome, avaliação e os anúncios dele.
 */

import React, { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import type { Anuncio } from '../api/tipos';
import { Avatar, Carregando, TelaVazia } from '../componentes/base';
import { GradeDeProdutos } from '../componentes/produto';
import { anunciosDemo } from '../dados/exemplo';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte } from '../tema';

type Props = NativeStackScreenProps<ParametrosApp, 'Loja'>;

export function TelaLoja({ navigation, route }: Props) {
  const [anuncios, setAnuncios] = useState<Anuncio[]>([]);
  const [carregando, setCarregando] = useState(true);

  useEffect(() => {
    async function carregar() {
      if (MODO_DEMONSTRACAO) {
        setAnuncios(anunciosDemo.slice(0, 4));
        setCarregando(false);
        return;
      }
      try {
        // a vitrine já filtra por vendedor via busca
        const resposta = await api<{ itens: Anuncio[] }>(
          `/anuncios?porPagina=40&vendedor=${route.params.vendedorId}`,
          { publico: true },
        );
        setAnuncios(resposta.itens);
      } catch {
        setAnuncios([]);
      } finally {
        setCarregando(false);
      }
    }
    void carregar();
  }, [route.params.vendedorId]);

  if (carregando) return <Carregando />;

  const vendedor = anuncios[0]?.vendedor;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>
          {(vendedor?.apelidoLoja ?? vendedor?.nome ?? 'lojinha').toLowerCase()}
        </Text>
      </View>

      {anuncios.length === 0 ? (
        <TelaVazia
          icone="storefront-outline"
          titulo="essa loja está sem anúncios"
          descricao="volte outro dia — pode ser que apareça coisa nova."
        />
      ) : (
        <GradeDeProdutos
          anuncios={anuncios}
          aoTocarProduto={(id) => navigation.navigate('Produto', { id })}
          cabecalho={
            <View style={{ padding: espaco.lg }}>
              <View style={e.capa} />
              <View style={{ marginTop: -24, marginLeft: espaco.xs }}>
                <Avatar url={vendedor?.fotoUrl} nome={vendedor?.nome} tamanho={56} />
              </View>
              <Text style={[fonte.secao, { marginTop: espaco.md }]}>
                {(vendedor?.apelidoLoja ?? vendedor?.nome ?? '').toLowerCase()}
              </Text>
              <Text style={fonte.pequeno}>
                {vendedor?.notaMedia
                  ? `⭐ ${vendedor.notaMedia} · ${vendedor.totalAvaliacoes} avaliações`
                  : 'sem avaliações ainda'}
                {vendedor?.bairro ? ` · ${vendedor.bairro.toLowerCase()}` : ''}
              </Text>
              <Text style={[fonte.pequeno, { marginTop: espaco.md }]}>
                {anuncios.length} {anuncios.length === 1 ? 'anúncio' : 'anúncios'}
              </Text>
            </View>
          }
        />
      )}
    </SafeAreaView>
  );
}

const e = StyleSheet.create({
  topo: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: espaco.lg,
    paddingBottom: espaco.md,
  },
  capa: {
    height: 80,
    borderRadius: 12,
    backgroundColor: cores.verde,
  },
});
