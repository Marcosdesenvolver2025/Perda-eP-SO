/**
 * Aba de notificações, com as duas abas do vídeo: negociações e mensagens.
 */

import React, { useCallback, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api } from '../api/cliente';
import type { Pedido } from '../api/tipos';
import { Avatar, Selo, Separador, TelaVazia } from '../componentes/base';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte } from '../tema';
import { prazoDeTeste, quando, reais } from '../util/formato';

type Props = NativeStackScreenProps<ParametrosApp, 'Abas'>;

interface Conversa {
  id: string;
  texto: string;
  criadoEm: string;
  souOAutor: boolean;
  naoLida: boolean;
  autor: { nome: string; fotoUrl?: string | null };
  pedido?: { id: string; codigo: string; anuncio: { titulo: string } } | null;
}

export function TelaNotificacoes({ navigation }: Props) {
  const [aba, setAba] = useState<'negociacoes' | 'mensagens'>('negociacoes');
  const [pedidos, setPedidos] = useState<Pedido[]>([]);
  const [conversas, setConversas] = useState<Conversa[]>([]);

  useFocusEffect(
    useCallback(() => {
      async function carregar() {
        try {
          const [compras, mensagens] = await Promise.all([
            api<{ itens: Pedido[] }>('/pedidos/compras'),
            api<{ itens: Conversa[] }>('/mensagens'),
          ]);
          setPedidos(compras.itens);
          setConversas(mensagens.itens);
        } catch {
          setPedidos([]);
          setConversas([]);
        }
      }
      void carregar();
    }, []),
  );

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <Text style={[fonte.secao, { padding: espaco.lg }]}>notificações</Text>

      <View style={e.abas}>
        {(['negociacoes', 'mensagens'] as const).map((chave) => (
          <Pressable
            key={chave}
            onPress={() => setAba(chave)}
            style={[e.aba, aba === chave && { borderBottomColor: cores.verde }]}
            accessibilityRole="tab"
            accessibilityState={{ selected: aba === chave }}
          >
            <Text
              style={{
                fontWeight: aba === chave ? '700' : '400',
                color: aba === chave ? cores.verdeEscuro : cores.textoSuave,
              }}
            >
              {chave === 'negociacoes' ? 'negociações' : 'mensagens'}
            </Text>
          </Pressable>
        ))}
      </View>

      <ScrollView contentContainerStyle={{ flexGrow: 1 }}>
        {aba === 'negociacoes' ? (
          pedidos.length === 0 ? (
            <TelaVazia
              icone="receipt-outline"
              titulo="nenhuma negociação"
              descricao="quando você comprar ou vender, o andamento aparece aqui."
            />
          ) : (
            pedidos.map((pedido) => {
              const aviso = prazoDeTeste(pedido.diasRestantesParaTestar ?? null);
              return (
                <React.Fragment key={pedido.id}>
                  <Pressable
                    onPress={() => navigation.navigate('Pedido', { id: pedido.id })}
                    style={e.linha}
                    accessibilityRole="button"
                  >
                    <View style={e.miniatura}>
                      <Ionicons name="cube-outline" size={22} color={cores.textoFraco} />
                    </View>
                    <View style={{ flex: 1, marginLeft: espaco.md }}>
                      <Text style={fonte.rotulo} numberOfLines={1}>
                        {pedido.anuncio.titulo.toLowerCase()}
                      </Text>
                      <Text style={fonte.pequeno}>
                        {pedido.codigo} · {reais(pedido.valorTotal)} · {quando(pedido.criadoEm)}
                      </Text>
                      {pedido.estado === 'ENTREGUE' && aviso ? (
                        <View style={{ marginTop: 4 }}>
                          <Selo
                            texto={aviso}
                            tom={(pedido.diasRestantesParaTestar ?? 0) <= 1 ? 'alerta' : 'verde'}
                          />
                        </View>
                      ) : null}
                    </View>
                    <Ionicons name="chevron-forward" size={18} color={cores.textoFraco} />
                  </Pressable>
                  <Separador />
                </React.Fragment>
              );
            })
          )
        ) : conversas.length === 0 ? (
          <TelaVazia
            icone="chatbubbles-outline"
            titulo="nenhuma mensagem"
            descricao="fale com quem vende direto pela página do produto."
          />
        ) : (
          conversas.map((conversa) => (
            <React.Fragment key={conversa.id}>
              <Pressable
                onPress={() =>
                  conversa.pedido
                    ? navigation.navigate('Conversa', { pedidoId: conversa.pedido.id })
                    : undefined
                }
                style={e.linha}
                accessibilityRole="button"
              >
                <Avatar url={conversa.autor.fotoUrl} nome={conversa.autor.nome} tamanho={44} />
                <View style={{ flex: 1, marginLeft: espaco.md }}>
                  <Text style={[fonte.rotulo, conversa.naoLida && { color: cores.verdeProfundo }]}>
                    {conversa.souOAutor
                      ? 'você enviou uma mensagem'
                      : `${conversa.autor.nome.toLowerCase()} enviou uma mensagem`}
                  </Text>
                  <Text
                    style={[fonte.pequeno, conversa.naoLida && { color: cores.texto }]}
                    numberOfLines={2}
                  >
                    {conversa.texto}
                  </Text>
                </View>
                {conversa.naoLida ? <View style={e.pontoNaoLido} /> : null}
              </Pressable>
              <Separador />
            </React.Fragment>
          ))
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const e = StyleSheet.create({
  abas: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: cores.borda },
  aba: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: espaco.md,
    borderBottomWidth: 3,
    borderBottomColor: 'transparent',
  },
  linha: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: espaco.lg,
    paddingVertical: espaco.lg,
  },
  miniatura: {
    width: 44,
    height: 44,
    borderRadius: 8,
    backgroundColor: cores.fundoCinza,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pontoNaoLido: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: cores.verde,
  },
});
