/**
 * Busca com filtros. Equivale à aba "buscar" do vídeo.
 */

import React, { useCallback, useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import type { Anuncio, CondicaoProduto } from '../api/tipos';
import { BarraDeBusca, Carregando, TelaVazia } from '../componentes/base';
import { GradeDeProdutos } from '../componentes/produto';
import { anunciosDemo } from '../dados/exemplo';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';

type Props = NativeStackScreenProps<ParametrosApp, 'Busca'>;

type Ordem = 'recentes' | 'menor_preco' | 'maior_preco';

const ordens: { chave: Ordem; rotulo: string }[] = [
  { chave: 'recentes', rotulo: 'mais recentes' },
  { chave: 'menor_preco', rotulo: 'menor preço' },
  { chave: 'maior_preco', rotulo: 'maior preço' },
];

const condicoes: { chave: CondicaoProduto; rotulo: string }[] = [
  { chave: 'NOVO', rotulo: 'novo' },
  { chave: 'SEMINOVO', rotulo: 'seminovo' },
  { chave: 'USADO', rotulo: 'usado' },
];

export function TelaBusca({ navigation, route }: Props) {
  const [termo, setTermo] = useState(route.params?.termo ?? '');
  const [ordem, setOrdem] = useState<Ordem>('recentes');
  const [condicao, setCondicao] = useState<CondicaoProduto | null>(null);
  const [itens, setItens] = useState<Anuncio[]>([]);
  const [carregando, setCarregando] = useState(true);

  const buscar = useCallback(async () => {
    setCarregando(true);

    if (MODO_DEMONSTRACAO) {
      const filtrados = anunciosDemo
        .filter((a) => (condicao ? a.condicao === condicao : true))
        .filter((a) => a.titulo.toLowerCase().includes(termo.trim().toLowerCase()))
        .sort((a, b) =>
          ordem === 'menor_preco'
            ? a.preco - b.preco
            : ordem === 'maior_preco'
              ? b.preco - a.preco
              : 0,
        );
      setItens(filtrados);
      setCarregando(false);
      return;
    }

    try {
      const parametros = new URLSearchParams({ ordem, porPagina: '40' });
      if (termo.trim()) parametros.set('busca', termo.trim());
      if (condicao) parametros.set('condicao', condicao);

      const resposta = await api<{ itens: Anuncio[] }>(`/anuncios?${parametros}`, {
        publico: true,
      });
      setItens(resposta.itens);
    } catch {
      setItens([]);
    } finally {
      setCarregando(false);
    }
  }, [termo, ordem, condicao]);

  useEffect(() => {
    void buscar();
  }, [ordem, condicao]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable
          onPress={() => navigation.goBack()}
          hitSlop={12}
          accessibilityRole="button"
          accessibilityLabel="voltar"
        >
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <View style={{ flex: 1 }}>
          <BarraDeBusca valor={termo} aoMudar={setTermo} aoEnviar={buscar} />
        </View>
      </View>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={e.filtros}
        style={{ flexGrow: 0 }}
      >
        {ordens.map((o) => (
          <Pressable
            key={o.chave}
            onPress={() => setOrdem(o.chave)}
            style={[e.chip, ordem === o.chave && e.chipAtivo]}
            accessibilityRole="button"
            accessibilityState={{ selected: ordem === o.chave }}
          >
            <Text style={[e.chipTexto, ordem === o.chave && e.chipTextoAtivo]}>{o.rotulo}</Text>
          </Pressable>
        ))}

        <View style={e.divisor} />

        {condicoes.map((c) => (
          <Pressable
            key={c.chave}
            onPress={() => setCondicao(condicao === c.chave ? null : c.chave)}
            style={[e.chip, condicao === c.chave && e.chipAtivo]}
            accessibilityRole="button"
            accessibilityState={{ selected: condicao === c.chave }}
          >
            <Text style={[e.chipTexto, condicao === c.chave && e.chipTextoAtivo]}>
              {c.rotulo}
            </Text>
          </Pressable>
        ))}
      </ScrollView>

      {carregando ? (
        <Carregando texto="procurando..." />
      ) : itens.length === 0 ? (
        <TelaVazia
          icone="search-outline"
          titulo="nada encontrado"
          descricao={
            termo
              ? `ninguém está vendendo "${termo}" agora. que tal anunciar você?`
              : 'tente outra palavra ou tire os filtros.'
          }
        />
      ) : (
        <GradeDeProdutos
          anuncios={itens}
          aoTocarProduto={(id) => navigation.navigate('Produto', { id })}
          cabecalho={
            <Text style={[fonte.pequeno, { paddingHorizontal: espaco.lg, paddingVertical: espaco.md }]}>
              {itens.length} {itens.length === 1 ? 'anúncio' : 'anúncios'} em Itinga
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
    paddingLeft: espaco.md,
    paddingBottom: espaco.sm,
  },
  filtros: {
    paddingHorizontal: espaco.lg,
    gap: espaco.sm,
    paddingVertical: espaco.md,
    alignItems: 'center',
  },
  chip: {
    paddingHorizontal: espaco.lg,
    paddingVertical: espaco.sm,
    borderRadius: raio.pilula,
    backgroundColor: cores.fundoCinza,
  },
  chipAtivo: { backgroundColor: cores.verde },
  chipTexto: { fontSize: 13, color: cores.textoSuave, fontWeight: '600' },
  chipTextoAtivo: { color: cores.branco },
  divisor: { width: 1, height: 20, backgroundColor: cores.borda, marginHorizontal: espaco.sm },
});
