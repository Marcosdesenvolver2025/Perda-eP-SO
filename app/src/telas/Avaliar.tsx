/**
 * Avaliar quem vendeu, depois do pedido concluído.
 *
 * A nota é o que sustenta a confiança em marketplace de gente desconhecida:
 * sem avaliação, comprar de alguém que você não conhece é aposta. Por isso a
 * tela é curta — cinco estrelas e um comentário opcional. Formulário longo
 * aqui é formulário que ninguém preenche.
 */

import React, { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api } from '../api/cliente';
import { Aviso, Botao, Campo } from '../componentes/base';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio, sombraFlutuante } from '../tema';

type Props = NativeStackScreenProps<ParametrosApp, 'Avaliar'>;

/** O que cada nota quer dizer, para a pessoa não ter que adivinhar. */
const legendas: Record<number, string> = {
  1: 'péssimo — não recomendo',
  2: 'ruim, deu problema',
  3: 'ok, mas dava pra ser melhor',
  4: 'bom, compraria de novo',
  5: 'excelente, recomendo demais',
};

export function TelaAvaliar({ navigation, route }: Props) {
  const { pedidoId, vendedor } = route.params;

  const [nota, setNota] = useState(0);
  const [comentario, setComentario] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function enviar() {
    setErro(null);
    setEnviando(true);
    try {
      await api(`/pedidos/${pedidoId}/avaliar`, {
        metodo: 'POST',
        corpo: { nota, comentario: comentario.trim() || undefined },
      });
      navigation.goBack();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="fechar">
          <Ionicons name="close" size={26} color={cores.texto} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md, flex: 1 }]}>como foi a compra?</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: espaco.lg, paddingBottom: espaco.xxl }}>
        <Text style={fonte.corpo}>
          sua avaliação ajuda quem vai comprar de{' '}
          <Text style={{ fontWeight: '700' }}>{vendedor.toLowerCase()}</Text> depois de você.
        </Text>

        <View style={e.estrelas}>
          {[1, 2, 3, 4, 5].map((n) => (
            <Pressable
              key={n}
              onPress={() => setNota(n)}
              hitSlop={6}
              accessibilityRole="button"
              accessibilityLabel={`${n} ${n === 1 ? 'estrela' : 'estrelas'}`}
              accessibilityState={{ selected: nota === n }}
            >
              <Ionicons
                name={n <= nota ? 'star' : 'star-outline'}
                size={40}
                color={n <= nota ? cores.ambar : cores.borda}
              />
            </Pressable>
          ))}
        </View>

        <Text style={e.legenda}>{nota > 0 ? legendas[nota] : 'toque nas estrelas'}</Text>

        <View style={{ marginTop: espaco.xl }}>
          <Campo
            rotulo="quer contar como foi? (opcional)"
            valor={comentario}
            aoMudar={setComentario}
            dica="o produto era como no anúncio? a entrega foi tranquila?"
            multilinha
            maxLength={500}
          />
        </View>

        {erro ? <Aviso tom="alerta" texto={erro} /> : null}

        <Aviso
          tom="informacao"
          texto="A avaliação fica pública na lojinha e não pode ser apagada depois. Escreva pensando em quem vai ler para decidir se compra."
        />
      </ScrollView>

      <View style={e.rodape}>
        <Botao
          titulo="enviar avaliação"
          aoTocar={enviar}
          carregando={enviando}
          desabilitado={nota === 0}
        />
      </View>
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
  estrelas: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: espaco.md,
    marginTop: espaco.xl,
  },
  legenda: {
    textAlign: 'center',
    marginTop: espaco.md,
    fontSize: 14,
    fontWeight: '600',
    color: cores.textoSuave,
    minHeight: 20,
  },
  rodape: {
    padding: espaco.lg,
    paddingBottom: espaco.xl,
    backgroundColor: cores.fundo,
    // sombra em vez de linha: a barra passa a flutuar sobre o conteúdo, e
    // fica claro que ela é a ação principal e não o fim da página
    ...(sombraFlutuante as object),
  },
});
