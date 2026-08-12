/**
 * Código de confirmação — tela do COMPRADOR.
 *
 * É a prova de que o produto chegou na mão dele. Vale nas duas modalidades:
 * na entrega pela plataforma quem digita é o entregador, na entrega pelo
 * vendedor quem digita é o vendedor.
 *
 * O aviso de "só informe ao receber" é a parte mais importante da tela: quem
 * entrega o código antes de ter o produto perde a proteção.
 */

import React, { useCallback, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api } from '../api/cliente';
import type { ModalidadeEntrega } from '../api/tipos';
import { Aviso, Botao, Carregando, Cartao } from '../componentes/base';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { DIAS_PARA_TESTAR } from '../regras/limites';

type Props = NativeStackScreenProps<ParametrosApp, 'CodigoDeConfirmacao'>;

interface RespostaCodigo {
  codigo: string | null;
  modalidade: ModalidadeEntrega;
  estado: string;
  paraQuem: string;
}

export function TelaCodigoDeConfirmacao({ navigation, route }: Props) {
  const [dados, setDados] = useState<RespostaCodigo | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [confirmando, setConfirmando] = useState(false);

  useFocusEffect(
    useCallback(() => {
      async function carregar() {
        try {
          setDados(await api<RespostaCodigo>(`/pedidos/${route.params.pedidoId}/codigo`));
        } catch (e) {
          setErro((e as Error).message);
        } finally {
          setCarregando(false);
        }
      }
      void carregar();
    }, [route.params.pedidoId]),
  );

  /** Atalho para quem já recebeu e quer fechar sem esperar o prazo. */
  async function confirmarRecebimento() {
    setErro(null);
    setConfirmando(true);
    try {
      await api(`/pedidos/${route.params.pedidoId}/recebi`, { metodo: 'POST' });
      navigation.goBack();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setConfirmando(false);
    }
  }

  if (carregando) return <Carregando />;

  const podeConfirmarSozinho =
    dados?.modalidade === 'VENDEDOR' &&
    ['AGUARDANDO_ENTREGA_DO_VENDEDOR', 'ENTREGA_DECLARADA'].includes(dados.estado);

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>código de confirmação</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: espaco.lg }}>
        {erro ? <Aviso texto={erro} tom="alerta" /> : null}

        {dados?.codigo ? (
          <>
            <View style={e.caixaCodigo}>
              <Text style={fonte.pequeno}>seu código</Text>
              <Text style={e.codigo} accessibilityLabel={`código ${dados.codigo.split('').join(' ')}`}>
                {dados.codigo}
              </Text>
            </View>

            <View style={{ marginTop: espaco.xl }}>
              <Aviso
                texto="só informe este código quando o produto estiver na sua mão. Passar o código antes é abrir mão da sua proteção."
                tom="alerta"
              />
            </View>

            <Cartao estilo={{ marginTop: espaco.lg }}>
              <Text style={fonte.rotulo}>como funciona</Text>
              <Passo
                numero={1}
                texto={
                  dados.modalidade === 'VENDEDOR'
                    ? 'combine com o vendedor onde e quando receber'
                    : 'espere o entregador chegar no seu endereço'
                }
              />
              <Passo numero={2} texto="confira o produto na hora da entrega" />
              <Passo
                numero={3}
                texto={
                  dados.modalidade === 'VENDEDOR'
                    ? 'aí sim, informe o código ao vendedor'
                    : 'aí sim, informe o código ao entregador'
                }
              />
              <Passo
                numero={4}
                texto={`a partir daí você tem ${DIAS_PARA_TESTAR} dias para testar e pedir devolução`}
              />
            </Cartao>

            {podeConfirmarSozinho ? (
              <View style={{ marginTop: espaco.xl }}>
                <Text style={[fonte.pequeno, { marginBottom: espaco.sm }]}>
                  já recebeu e prefere confirmar por aqui?
                </Text>
                <Botao
                  titulo="já recebi o produto"
                  variante="vazado"
                  aoTocar={confirmarRecebimento}
                  carregando={confirmando}
                />
              </View>
            ) : null}
          </>
        ) : (
          <Cartao>
            <Text style={fonte.corpo}>
              O código aparece assim que o pagamento for confirmado.
            </Text>
          </Cartao>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function Passo({ numero, texto }: { numero: number; texto: string }) {
  return (
    <View style={{ flexDirection: 'row', marginTop: espaco.md, alignItems: 'flex-start' }}>
      <View style={e.bolinha}>
        <Text style={{ color: cores.branco, fontSize: 12, fontWeight: '700' }}>{numero}</Text>
      </View>
      <Text style={[fonte.corpo, { flex: 1, marginLeft: espaco.md }]}>{texto}</Text>
    </View>
  );
}

const e = StyleSheet.create({
  topo: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: espaco.lg,
    paddingBottom: espaco.md,
  },
  caixaCodigo: {
    alignItems: 'center',
    backgroundColor: cores.verdeClaro,
    borderRadius: raio.lg,
    paddingVertical: espaco.xxl,
  },
  codigo: {
    fontSize: 56,
    fontWeight: '700',
    letterSpacing: 10,
    color: cores.verdeProfundo,
    marginTop: espaco.sm,
  },
  bolinha: {
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: cores.verde,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
