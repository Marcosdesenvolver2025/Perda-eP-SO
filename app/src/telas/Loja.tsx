/**
 * Lojinha de outro vendedor: capa, nome, avaliação e os anúncios dele.
 */

import React, { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api } from '../api/cliente';
import type { Anuncio, Usuario } from '../api/tipos';
import { Avatar, Botao, Carregando, TelaVazia } from '../componentes/base';
import { GradeDeProdutos } from '../componentes/produto';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte } from '../tema';

type Props = NativeStackScreenProps<ParametrosApp, 'Loja'>;

export function TelaLoja({ navigation, route }: Props) {
  const [anuncios, setAnuncios] = useState<Anuncio[]>([]);
  const [perfil, setPerfil] = useState<Usuario | null>(null);
  const [carregando, setCarregando] = useState(true);

  useEffect(() => {
    async function carregar() {
      try {
        // a vitrine já filtra por vendedor via busca
        const [resposta, dono] = await Promise.all([
          api<{ itens: Anuncio[] }>(
            `/anuncios?porPagina=40&vendedor=${route.params.vendedorId}`,
            { publico: true },
          ),
          api<Usuario>(`/vendedores/${route.params.vendedorId}`, { publico: true }).catch(
            () => null,
          ),
        ]);
        setAnuncios(resposta.itens);
        setPerfil(dono);
      } catch {
        setAnuncios([]);
      } finally {
        setCarregando(false);
      }
    }
    void carregar();
  }, [route.params.vendedorId]);

  async function alternarSeguir() {
    if (!perfil) return;
    const antes = perfil;
    setPerfil({
      ...perfil,
      seguindo: !perfil.seguindo,
      seguidores: (perfil.seguidores ?? 0) + (perfil.seguindo ? -1 : 1),
    });
    try {
      await api(`/vendedores/${route.params.vendedorId}/seguir`, { metodo: 'POST' });
    } catch {
      setPerfil(antes);
    }
  }

  async function curtir(id: string) {
    setAnuncios((atuais) =>
      atuais.map((a) =>
        a.id === id
          ? { ...a, curtido: !a.curtido, curtidas: (a.curtidas ?? 0) + (a.curtido ? -1 : 1) }
          : a,
      ),
    );
    await api(`/anuncios/${id}/curtir`, { metodo: 'POST' }).catch(() => undefined);
  }

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
          aoCurtir={curtir}
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
                {perfil?.notaMedia
                  ? `⭐ ${perfil.notaMedia} · ${perfil.totalAvaliacoes} avaliações`
                  : 'sem avaliações ainda'}
                {vendedor?.bairro ? ` · ${vendedor.bairro.toLowerCase()}` : ''}
              </Text>

              <View style={e.linhaNumeros}>
                <Text style={e.numero}>
                  <Text style={e.numeroForte}>{anuncios.length}</Text>{' '}
                  {anuncios.length === 1 ? 'anúncio' : 'anúncios'}
                </Text>
                <Text style={e.numero}>
                  <Text style={e.numeroForte}>{perfil?.seguidores ?? 0}</Text>{' '}
                  {(perfil?.seguidores ?? 0) === 1 ? 'seguidor' : 'seguidores'}
                </Text>
              </View>

              {perfil ? (
                <Botao
                  titulo={perfil.seguindo ? 'seguindo' : 'seguir esta lojinha'}
                  variante={perfil.seguindo ? 'vazado' : 'cheio'}
                  icone={perfil.seguindo ? 'checkmark' : 'person-add-outline'}
                  aoTocar={alternarSeguir}
                  estilo={{ marginTop: espaco.md }}
                />
              ) : null}
            </View>
          }
        />
      )}
    </SafeAreaView>
  );
}

const e = StyleSheet.create({
  linhaNumeros: { flexDirection: 'row', gap: espaco.lg, marginTop: espaco.md },
  numero: { fontSize: 13, color: cores.textoSuave },
  numeroForte: { fontWeight: '700', color: cores.texto },
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
