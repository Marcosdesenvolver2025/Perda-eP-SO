/**
 * Conversa dentro de um pedido: comprador e vendedor combinam entrega,
 * tiram dúvida e resolvem problema antes de abrir devolução.
 */

import React, { useCallback, useRef, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import { TelaVazia } from '../componentes/base';
import { useAutenticacao } from '../contextos/Autenticacao';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';

type Props = NativeStackScreenProps<ParametrosApp, 'Conversa'>;

interface Mensagem {
  id: string;
  texto: string;
  autorId: string;
  criadoEm: string;
  autor?: { id: string; nome: string };
}

export function TelaConversa({ navigation, route }: Props) {
  const { usuario } = useAutenticacao();
  const [mensagens, setMensagens] = useState<Mensagem[]>([]);
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const rolagem = useRef<ScrollView>(null);

  const carregar = useCallback(async () => {
    if (MODO_DEMONSTRACAO) return;
    try {
      const resposta = await api<{ itens: Mensagem[] }>(
        `/mensagens/pedido/${route.params.pedidoId}`,
      );
      setMensagens(resposta.itens);
    } catch {
      setMensagens([]);
    }
  }, [route.params.pedidoId]);

  useFocusEffect(
    useCallback(() => {
      void carregar();
    }, [carregar]),
  );

  async function enviar() {
    const conteudo = texto.trim();
    if (!conteudo) return;

    setEnviando(true);
    setTexto('');
    try {
      await api('/mensagens', {
        metodo: 'POST',
        corpo: { pedidoId: route.params.pedidoId, texto: conteudo },
      });
      await carregar();
      rolagem.current?.scrollToEnd({ animated: true });
    } catch {
      // devolve o texto para a pessoa não perder o que escreveu
      setTexto(conteudo);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>conversa</Text>
      </View>

      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={80}
      >
        <ScrollView
          ref={rolagem}
          contentContainerStyle={{ padding: espaco.lg, flexGrow: 1 }}
          onContentSizeChange={() => rolagem.current?.scrollToEnd({ animated: false })}
        >
          {mensagens.length === 0 ? (
            <TelaVazia
              icone="chatbubble-ellipses-outline"
              titulo="comece a conversa"
              descricao="combine a entrega, tire dúvidas sobre o produto."
            />
          ) : (
            mensagens.map((mensagem) => {
              const minha = mensagem.autorId === usuario?.id;
              return (
                <View
                  key={mensagem.id}
                  style={[
                    e.balao,
                    minha
                      ? { alignSelf: 'flex-end', backgroundColor: cores.verdeClaro }
                      : { alignSelf: 'flex-start', backgroundColor: cores.fundoCinza },
                  ]}
                >
                  <Text style={fonte.corpo}>{mensagem.texto}</Text>
                </View>
              );
            })
          )}
        </ScrollView>

        <View style={e.barraEnvio}>
          <TextInput
            value={texto}
            onChangeText={setTexto}
            placeholder="escreva sua mensagem"
            placeholderTextColor={cores.textoFraco}
            style={e.entrada}
            multiline
            accessibilityLabel="mensagem"
          />
          <Pressable
            onPress={enviar}
            disabled={enviando || !texto.trim()}
            style={[e.enviar, (!texto.trim() || enviando) && { opacity: 0.4 }]}
            accessibilityRole="button"
            accessibilityLabel="enviar mensagem"
          >
            <Ionicons name="send" size={18} color={cores.branco} />
          </Pressable>
        </View>
      </KeyboardAvoidingView>
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
  balao: {
    maxWidth: '80%',
    borderRadius: raio.lg,
    padding: espaco.md,
    marginBottom: espaco.sm,
  },
  barraEnvio: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    padding: espaco.md,
    gap: espaco.sm,
    borderTopWidth: 1,
    borderTopColor: cores.borda,
  },
  entrada: {
    flex: 1,
    maxHeight: 120,
    backgroundColor: cores.fundoCinza,
    borderRadius: raio.lg,
    paddingHorizontal: espaco.lg,
    paddingVertical: espaco.md,
    fontSize: 15,
    color: cores.texto,
  },
  enviar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: cores.verde,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
