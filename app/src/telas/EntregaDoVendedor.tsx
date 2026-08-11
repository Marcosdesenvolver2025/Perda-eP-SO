/**
 * Confirmação da entrega — tela do VENDEDOR, modalidade "entrega por sua conta".
 *
 * O caminho normal é digitar o código que o comprador mostra. Quando o
 * comprador some e o código não sai, o vendedor pode declarar a entrega: isso
 * abre um prazo de 3 dias para o comprador se manifestar e, passado o prazo, o
 * sistema confirma sozinho.
 */

import React, { useCallback, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import type { Pedido } from '../api/tipos';
import { Aviso, Botao, Campo, Carregando, Cartao } from '../componentes/base';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { reais } from '../util/formato';
import { DIAS_PARA_CONFIRMACAO_AUTOMATICA } from '../regras/limites';

type Props = NativeStackScreenProps<ParametrosApp, 'EntregaDoVendedor'>;

export function TelaEntregaDoVendedor({ navigation, route }: Props) {
  const [pedido, setPedido] = useState<Pedido | null>(null);
  const [codigo, setCodigo] = useState('');
  const [carregando, setCarregando] = useState(true);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      async function carregar() {
        if (MODO_DEMONSTRACAO) {
          setCarregando(false);
          return;
        }
        try {
          setPedido(await api<Pedido>(`/pedidos/${route.params.pedidoId}`));
        } catch (e) {
          setErro((e as Error).message);
        } finally {
          setCarregando(false);
        }
      }
      void carregar();
    }, [route.params.pedidoId]),
  );

  async function confirmarComCodigo() {
    setErro(null);
    setEnviando(true);
    try {
      await api(`/pedidos/${route.params.pedidoId}/entreguei`, {
        metodo: 'POST',
        corpo: { codigo: codigo.trim() },
      });
      navigation.goBack();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  function declararSemCodigo() {
    Alert.alert(
      'declarar entrega sem o código?',
      `Use isto só quando não conseguir o código com o comprador. Ele vai ter ${DIAS_PARA_CONFIRMACAO_AUTOMATICA} dias para confirmar ou abrir devolução; se não fizer nada, a entrega é confirmada automaticamente.`,
      [
        { text: 'cancelar', style: 'cancel' },
        {
          text: 'declarar entrega',
          onPress: async () => {
            setErro(null);
            setEnviando(true);
            try {
              await api(`/pedidos/${route.params.pedidoId}/declarar-entrega`, {
                metodo: 'POST',
              });
              navigation.goBack();
            } catch (e) {
              setErro((e as Error).message);
            } finally {
              setEnviando(false);
            }
          },
        },
      ],
    );
  }

  if (carregando) return <Carregando />;

  const jaDeclarou = pedido?.estado === 'ENTREGA_DECLARADA';

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="close" size={26} color={cores.texto} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>confirmar entrega</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: espaco.lg, paddingBottom: 140 }}>
        {erro ? <Aviso texto={erro} tom="alerta" /> : null}

        {pedido ? (
          <Cartao estilo={{ marginBottom: espaco.lg }}>
            <Text style={fonte.rotulo}>{pedido.anuncio.titulo.toLowerCase()}</Text>
            <Text style={fonte.pequeno}>
              pedido {pedido.codigo} · você recebe {reais(pedido.valorVendedor)}
            </Text>
          </Cartao>
        ) : null}

        {jaDeclarou ? (
          <Aviso
            texto={`Você já declarou a entrega. O comprador tem ${DIAS_PARA_CONFIRMACAO_AUTOMATICA} dias para confirmar; passado o prazo, a gente confirma sozinho. Se ele te passar o código antes, dá para confirmar aqui na hora.`}
            tom="informacao"
          />
        ) : (
          <Text style={[fonte.corpo, { marginBottom: espaco.lg }]}>
            Na hora da entrega, peça ao comprador o código de 4 dígitos que aparece no app
            dele e digite aqui.
          </Text>
        )}

        <Campo
          rotulo="código do comprador"
          valor={codigo}
          aoMudar={setCodigo}
          dica="0000"
          teclado="numeric"
          maxLength={6}
        />

        <Botao
          titulo="confirmar entrega"
          aoTocar={confirmarComCodigo}
          carregando={enviando}
          desabilitado={codigo.trim().length < 4}
        />

        {!jaDeclarou ? (
          <View style={e.semCodigo}>
            <Text style={fonte.rotulo}>não conseguiu o código?</Text>
            <Text style={[fonte.pequeno, { marginTop: 4, marginBottom: espaco.md }]}>
              se o comprador sumiu depois de receber, declare a entrega. Ele terá{' '}
              {DIAS_PARA_CONFIRMACAO_AUTOMATICA} dias para responder e, sem resposta, a
              entrega é confirmada automaticamente.
            </Text>
            <Botao
              titulo="declarar entrega sem código"
              variante="vazado"
              aoTocar={declararSemCodigo}
            />
          </View>
        ) : null}

        <Text style={[fonte.pequeno, { marginTop: espaco.xl }]}>
          depois da confirmação, o comprador ainda tem 7 dias para pedir devolução. Passado
          esse prazo, o valor da venda cai na sua conta.
        </Text>
      </ScrollView>
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
  semCodigo: {
    marginTop: espaco.xxl,
    padding: espaco.lg,
    backgroundColor: cores.fundoCinza,
    borderRadius: raio.lg,
  },
});
