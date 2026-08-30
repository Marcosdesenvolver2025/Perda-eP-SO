/**
 * Lista de desejos: os anúncios que eu curti.
 *
 * Descurtir daqui tira o item da lista na hora, sem esperar a rede — se o
 * servidor recusar, ele volta. Lista de desejos que trava a cada toque é
 * lista de desejos que ninguém usa.
 */

import React, { useCallback, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api } from '../api/cliente';
import type { Anuncio } from '../api/tipos';
import { Carregando, TelaVazia } from '../componentes/base';
import { GradeDeProdutos } from '../componentes/produto';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte } from '../tema';

type Props = NativeStackScreenProps<ParametrosApp, 'Curtidos'>;

export function TelaCurtidos({ navigation }: Props) {
  const [itens, setItens] = useState<Anuncio[]>([]);
  const [carregando, setCarregando] = useState(true);

  const carregar = useCallback(async () => {
    try {
      const r = await api<{ itens: Anuncio[] }>('/anuncios/curtidos');
      setItens(r.itens);
    } catch {
      setItens([]);
    } finally {
      setCarregando(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void carregar();
    }, [carregar]),
  );

  async function descurtir(id: string) {
    const antes = itens;
    setItens((atuais) => atuais.filter((a) => a.id !== id)); // some na hora
    try {
      await api(`/anuncios/${id}/curtir`, { metodo: 'POST' });
    } catch {
      setItens(antes); // o servidor recusou: devolve para a lista
    }
  }

  if (carregando) return <Carregando />;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md, flex: 1 }]}>o que eu curti</Text>
      </View>

      {itens.length === 0 ? (
        <TelaVazia
          icone="heart-outline"
          titulo="sua lista está vazia"
          descricao="toque no coração dos anúncios que você gostou. eles ficam guardados aqui."
          acao={{ titulo: 'ver o que tem à venda', aoTocar: () => navigation.navigate('Busca', {}) }}
        />
      ) : (
        <GradeDeProdutos
          anuncios={itens}
          aoTocarProduto={(id) => navigation.navigate('Produto', { id })}
          aoCurtir={descurtir}
          cabecalho={
            <Text
              style={[fonte.pequeno, { paddingHorizontal: espaco.lg, paddingVertical: espaco.md }]}
            >
              {itens.length} {itens.length === 1 ? 'anúncio guardado' : 'anúncios guardados'}
            </Text>
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
    paddingVertical: espaco.md,
  },
});
