/**
 * Área do entregador.
 *
 * Duas abas: as corridas abertas (pra aceitar) e as minhas (pra tocar o
 * serviço). Cada entrega vai de aceitar -> coletei -> entreguei, e a
 * confirmação da entrega abre a janela de 7 dias do comprador.
 */

import React, { useCallback, useState } from 'react';
import {
  Alert,
  Linking,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import type { Entrega, EstadoEntrega } from '../api/tipos';
import { Aviso, Botao, Cartao, Selo, TelaVazia } from '../componentes/base';
import { entregasDemo } from '../dados/exemplo';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { peso, quando, reais } from '../util/formato';

type Props = NativeStackScreenProps<ParametrosApp, 'AreaDoEntregador'>;

const rotuloEstado: Record<EstadoEntrega, string> = {
  AGUARDANDO_ENTREGADOR: 'disponível',
  ACEITA: 'aceita — vá buscar',
  COLETADA: 'com você — entregue',
  ENTREGUE: 'entregue',
  DEVOLVENDO: 'devolvendo ao vendedor',
  DEVOLVIDA: 'devolvida',
  CANCELADA: 'cancelada',
};

export function TelaAreaDoEntregador({ navigation }: Props) {
  const [aba, setAba] = useState<'disponiveis' | 'minhas'>('disponiveis');
  const [disponiveis, setDisponiveis] = useState<Entrega[]>([]);
  const [minhas, setMinhas] = useState<Entrega[]>([]);
  const [atualizando, setAtualizando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async () => {
    if (MODO_DEMONSTRACAO) {
      setDisponiveis(entregasDemo.filter((e) => e.estado === 'AGUARDANDO_ENTREGADOR'));
      setMinhas(entregasDemo.filter((e) => e.estado !== 'AGUARDANDO_ENTREGADOR'));
      setAtualizando(false);
      return;
    }
    try {
      const [abertas, aceitas] = await Promise.all([
        api<{ itens: Entrega[] }>('/entregas/disponiveis'),
        api<{ itens: Entrega[] }>('/entregas/minhas'),
      ]);
      setDisponiveis(abertas.itens);
      setMinhas(aceitas.itens);
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setAtualizando(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void carregar();
    }, [carregar]),
  );

  async function aceitar(entrega: Entrega) {
    try {
      await api(`/entregas/${entrega.id}/aceitar`, { metodo: 'POST' });
      await carregar();
      setAba('minhas');
    } catch (e) {
      // outro entregador pode ter pegado antes: a mensagem do servidor explica
      Alert.alert('não deu', (e as Error).message);
    }
  }

  async function confirmarColeta(entrega: Entrega) {
    try {
      await api(`/entregas/${entrega.id}/coletei`, { metodo: 'POST' });
      await carregar();
    } catch (e) {
      Alert.alert('não deu', (e as Error).message);
    }
  }

  function pedirCodigo(entrega: Entrega) {
    Alert.prompt?.(
      'código do comprador',
      'peça o código de 4 dígitos que aparece no app dele',
      async (codigo) => {
        try {
          await api(`/entregas/${entrega.id}/entreguei`, {
            metodo: 'POST',
            corpo: { codigo },
          });
          await carregar();
        } catch (e) {
          Alert.alert('não deu', (e as Error).message);
        }
      },
      'plain-text',
      '',
      'number-pad',
    );

    // Alert.prompt só existe no iOS; no Android abrimos a tela dedicada
    if (!Alert.prompt) {
      navigation.navigate('ConfirmarEntrega', { entregaId: entrega.id });
    }
  }

  const lista = aba === 'disponiveis' ? disponiveis : minhas;
  const ganhosDoDia = minhas
    .filter((e) => e.estado === 'ENTREGUE')
    .reduce((acc, e) => acc + e.valorEntregador, 0);

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>área do entregador</Text>
      </View>

      <View style={e.ganhos}>
        <View style={{ flex: 1 }}>
          <Text style={{ fontSize: 13, color: cores.verdeProfundo }}>já entregue hoje</Text>
          <Text style={{ fontSize: 24, fontWeight: '700', color: cores.preto }}>
            {reais(ganhosDoDia)}
          </Text>
        </View>
        <Ionicons name="bicycle" size={40} color={cores.verdeProfundo} style={{ opacity: 0.4 }} />
      </View>

      <View style={e.abas}>
        {(['disponiveis', 'minhas'] as const).map((chave) => (
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
              {chave === 'disponiveis'
                ? `disponíveis (${disponiveis.length})`
                : `minhas (${minhas.length})`}
            </Text>
          </Pressable>
        ))}
      </View>

      <ScrollView
        contentContainerStyle={{ padding: espaco.lg, flexGrow: 1 }}
        refreshControl={
          <RefreshControl
            refreshing={atualizando}
            onRefresh={() => {
              setAtualizando(true);
              void carregar();
            }}
            colors={[cores.verde]}
          />
        }
      >
        {erro ? <Aviso texto={erro} tom="alerta" /> : null}

        {lista.length === 0 ? (
          <TelaVazia
            icone="bicycle-outline"
            titulo={aba === 'disponiveis' ? 'nenhuma entrega agora' : 'você não tem corrida ativa'}
            descricao={
              aba === 'disponiveis'
                ? 'assim que alguém comprar com entrega, aparece aqui.'
                : 'aceite uma entrega na aba ao lado.'
            }
          />
        ) : (
          lista.map((entrega) => (
            <Cartao key={entrega.id} estilo={{ marginBottom: espaco.md }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <Selo
                  texto={rotuloEstado[entrega.estado]}
                  tom={entrega.estado === 'AGUARDANDO_ENTREGADOR' ? 'verde' : 'cinza'}
                />
                <Text style={fonte.rotulo}>{reais(entrega.valorEntregador)}</Text>
              </View>

              <Text style={[fonte.rotulo, { marginTop: espaco.md }]}>
                {entrega.pedido.anuncio.titulo.toLowerCase()}
              </Text>
              <Text style={fonte.pequeno}>
                pedido {entrega.pedido.codigo} · {quando(entrega.criadoEm)}
              </Text>

              {entrega.pedido.anuncio.pesoG ? (
                <Text style={[fonte.pequeno, { marginTop: 4 }]}>
                  {peso(entrega.pedido.anuncio.pesoG)} ·{' '}
                  {entrega.pedido.anuncio.comprimentoCm}×{entrega.pedido.anuncio.larguraCm}×
                  {entrega.pedido.anuncio.alturaCm} cm
                </Text>
              ) : null}

              <View style={{ marginTop: espaco.md, gap: espaco.sm }}>
                <LinhaEndereco icone="arrow-up-circle-outline" rotulo="coleta" texto={entrega.coletaEndereco} />
                <LinhaEndereco icone="arrow-down-circle-outline" rotulo="entrega" texto={entrega.entregaEndereco} />
              </View>

              {entrega.observacoes ? (
                <Text style={[fonte.pequeno, { marginTop: espaco.sm, fontStyle: 'italic' }]}>
                  {entrega.observacoes}
                </Text>
              ) : null}

              <View style={{ flexDirection: 'row', gap: espaco.sm, marginTop: espaco.lg }}>
                {entrega.estado === 'AGUARDANDO_ENTREGADOR' ? (
                  <Botao titulo="aceitar entrega" aoTocar={() => aceitar(entrega)} estilo={{ flex: 1 }} />
                ) : null}

                {entrega.estado === 'ACEITA' ? (
                  <>
                    <Botao
                      titulo="peguei o produto"
                      aoTocar={() => confirmarColeta(entrega)}
                      estilo={{ flex: 1 }}
                    />
                    {entrega.pedido.vendedor?.telefone ? (
                      <Pressable
                        onPress={() => Linking.openURL(`tel:${entrega.pedido.vendedor!.telefone}`)}
                        style={e.botaoIcone}
                        accessibilityLabel="ligar para o vendedor"
                      >
                        <Ionicons name="call-outline" size={20} color={cores.verdeEscuro} />
                      </Pressable>
                    ) : null}
                  </>
                ) : null}

                {entrega.estado === 'COLETADA' ? (
                  <>
                    <Botao
                      titulo="entreguei"
                      aoTocar={() => pedirCodigo(entrega)}
                      estilo={{ flex: 1 }}
                    />
                    {entrega.pedido.comprador?.telefone ? (
                      <Pressable
                        onPress={() => Linking.openURL(`tel:${entrega.pedido.comprador!.telefone}`)}
                        style={e.botaoIcone}
                        accessibilityLabel="ligar para o comprador"
                      >
                        <Ionicons name="call-outline" size={20} color={cores.verdeEscuro} />
                      </Pressable>
                    ) : null}
                  </>
                ) : null}
              </View>
            </Cartao>
          ))
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function LinhaEndereco({
  icone,
  rotulo,
  texto,
}: {
  icone: keyof typeof Ionicons.glyphMap;
  rotulo: string;
  texto: string;
}) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'flex-start' }}>
      <Ionicons name={icone} size={18} color={cores.verdeEscuro} style={{ marginTop: 1 }} />
      <View style={{ flex: 1, marginLeft: espaco.sm }}>
        <Text style={fonte.pequeno}>{rotulo}</Text>
        <Text style={fonte.corpo}>{texto}</Text>
      </View>
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
  ganhos: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: cores.verdeSuave,
    borderRadius: raio.lg,
    padding: espaco.lg,
    marginHorizontal: espaco.lg,
    marginBottom: espaco.md,
  },
  abas: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: cores.borda,
  },
  aba: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: espaco.md,
    borderBottomWidth: 3,
    borderBottomColor: 'transparent',
  },
  botaoIcone: {
    width: 48,
    height: 48,
    borderRadius: raio.pilula,
    borderWidth: 1.5,
    borderColor: cores.verdeEscuro,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
