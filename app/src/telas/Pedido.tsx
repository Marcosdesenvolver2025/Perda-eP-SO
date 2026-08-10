/**
 * Detalhe do pedido: linha do tempo, código de confirmação da entrega e o
 * botão de devolução enquanto a janela de 4 dias estiver aberta.
 */

import React, { useCallback, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import type { EstadoPedido, Pedido } from '../api/tipos';
import { Aviso, Botao, Carregando, Cartao, Selo, Separador } from '../componentes/base';
import { pedidosDemo } from '../dados/exemplo';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { dataCurta, prazoDeTeste, reais } from '../util/formato';

type Props = NativeStackScreenProps<ParametrosApp, 'Pedido'>;

const etapas: { estado: EstadoPedido; rotulo: string; icone: keyof typeof Ionicons.glyphMap }[] = [
  { estado: 'PAGO', rotulo: 'pagamento confirmado', icone: 'card-outline' },
  { estado: 'A_CAMINHO', rotulo: 'a caminho', icone: 'bicycle-outline' },
  { estado: 'ENTREGUE', rotulo: 'entregue', icone: 'home-outline' },
  { estado: 'CONCLUIDO', rotulo: 'concluído', icone: 'checkmark-done-outline' },
];

const rotuloEstado: Record<EstadoPedido, string> = {
  AGUARDANDO_PAGAMENTO: 'aguardando pagamento',
  PAGO: 'pagamento confirmado',
  EM_SEPARACAO: 'vendedor preparando',
  A_CAMINHO: 'a caminho',
  ENTREGUE: 'entregue',
  CONCLUIDO: 'concluído',
  EM_DEVOLUCAO: 'devolução em análise',
  REEMBOLSADO: 'reembolsado',
  CANCELADO: 'cancelado',
};

export function TelaPedido({ navigation, route }: Props) {
  const [pedido, setPedido] = useState<Pedido | null>(null);
  const [carregando, setCarregando] = useState(true);

  useFocusEffect(
    useCallback(() => {
      async function carregar() {
        if (MODO_DEMONSTRACAO) {
          setPedido(pedidosDemo.find((p) => p.id === route.params.id) ?? pedidosDemo[0]!);
          setCarregando(false);
          return;
        }
        try {
          setPedido(await api<Pedido>(`/pedidos/${route.params.id}`));
        } catch {
          setPedido(null);
        } finally {
          setCarregando(false);
        }
      }
      void carregar();
    }, [route.params.id]),
  );

  if (carregando) return <Carregando />;
  if (!pedido) return null;

  const indiceAtual = etapas.findIndex((etapa) => etapa.estado === pedido.estado);
  const aviso = prazoDeTeste(pedido.diasRestantesParaTestar ?? null);

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <View style={{ marginLeft: espaco.md }}>
          <Text style={fonte.secao}>pedido {pedido.codigo}</Text>
          <Text style={fonte.pequeno}>{rotuloEstado[pedido.estado]}</Text>
        </View>
      </View>

      <ScrollView contentContainerStyle={{ padding: espaco.lg, paddingBottom: 120 }}>
        {route.params.pixQrCode ? (
          <Cartao estilo={{ marginBottom: espaco.lg, alignItems: 'center' }}>
            <Ionicons name="qr-code" size={40} color={cores.verdeEscuro} />
            <Text style={[fonte.rotulo, { marginTop: espaco.md }]}>pague com pix</Text>
            <Text style={[fonte.pequeno, { textAlign: 'center', marginTop: espaco.sm }]}>
              copie o código abaixo e cole no seu banco. assim que o pagamento cair, o
              vendedor é avisado.
            </Text>
            <View style={e.caixaPix}>
              <Text style={{ fontSize: 11, color: cores.texto }} selectable numberOfLines={4}>
                {route.params.pixQrCode}
              </Text>
            </View>
          </Cartao>
        ) : null}

        {aviso && pedido.estado === 'ENTREGUE' ? (
          <Aviso texto={aviso} tom={(pedido.diasRestantesParaTestar ?? 0) <= 1 ? 'alerta' : 'sucesso'} />
        ) : null}

        <Cartao>
          <Text style={fonte.rotulo}>{pedido.anuncio.titulo.toLowerCase()}</Text>
          {pedido.vendedor ? (
            <Text style={[fonte.pequeno, { marginTop: 2 }]}>
              vendido por {(pedido.vendedor.apelidoLoja ?? pedido.vendedor.nome).toLowerCase()}
            </Text>
          ) : null}

          <View style={{ height: espaco.lg }} />
          <Separador />
          <View style={{ height: espaco.md }} />

          <Linha rotulo="produto" valor={reais(pedido.valorProduto)} />
          {pedido.valorFrete > 0 ? <Linha rotulo="entrega" valor={reais(pedido.valorFrete)} /> : null}
          <Linha rotulo="total pago" valor={reais(pedido.valorTotal)} destaque />
        </Cartao>

        {pedido.entrega?.codigoConfirmacao && pedido.estado === 'A_CAMINHO' ? (
          <Cartao estilo={{ marginTop: espaco.lg, alignItems: 'center' }}>
            <Text style={fonte.pequeno}>mostre esse código pro entregador</Text>
            <Text style={e.codigo}>{pedido.entrega.codigoConfirmacao}</Text>
          </Cartao>
        ) : null}

        <View style={{ marginTop: espaco.xl }}>
          <Text style={[fonte.rotulo, { marginBottom: espaco.lg }]}>acompanhe</Text>
          {etapas.map((etapa, indice) => {
            const concluida = indiceAtual >= indice && indiceAtual !== -1;
            return (
              <View key={etapa.estado} style={{ flexDirection: 'row', marginBottom: espaco.lg }}>
                <View style={{ alignItems: 'center', width: 32 }}>
                  <View style={[e.bolinha, concluida && { backgroundColor: cores.verde }]}>
                    <Ionicons
                      name={etapa.icone}
                      size={14}
                      color={concluida ? cores.branco : cores.textoFraco}
                    />
                  </View>
                  {indice < etapas.length - 1 ? (
                    <View style={[e.linhaTempo, concluida && { backgroundColor: cores.verde }]} />
                  ) : null}
                </View>
                <View style={{ flex: 1, marginLeft: espaco.md }}>
                  <Text style={[fonte.corpo, !concluida && { color: cores.textoFraco }]}>
                    {etapa.rotulo}
                  </Text>
                  {etapa.estado === 'ENTREGUE' && pedido.entregueEm ? (
                    <Text style={fonte.pequeno}>{dataCurta(pedido.entregueEm)}</Text>
                  ) : null}
                </View>
              </View>
            );
          })}
        </View>

        {pedido.reembolso ? (
          <Cartao estilo={{ marginTop: espaco.lg }}>
            <Selo texto="devolução" tom="alerta" />
            <Text style={[fonte.corpo, { marginTop: espaco.sm }]}>
              seu pedido de devolução está {pedido.reembolso.estado.toLowerCase()}.
            </Text>
            <Text style={[fonte.pequeno, { marginTop: 4 }]}>
              valor a devolver: {reais(pedido.reembolso.valorReembolsado)}
            </Text>
          </Cartao>
        ) : null}
      </ScrollView>

      {pedido.podePedirReembolso ? (
        <View style={e.rodape}>
          <Botao
            titulo="pedir devolução"
            variante="vazado"
            aoTocar={() => navigation.navigate('Reembolso', { pedidoId: pedido.id })}
          />
        </View>
      ) : null}
    </SafeAreaView>
  );
}

function Linha({
  rotulo,
  valor,
  destaque,
}: {
  rotulo: string;
  valor: string;
  destaque?: boolean;
}) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 3 }}>
      <Text style={destaque ? fonte.rotulo : fonte.corpo}>{rotulo}</Text>
      <Text style={destaque ? fonte.rotulo : fonte.corpo}>{valor}</Text>
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
  caixaPix: {
    backgroundColor: cores.fundoCinza,
    borderRadius: raio.md,
    padding: espaco.md,
    marginTop: espaco.md,
    width: '100%',
  },
  codigo: {
    fontSize: 34,
    fontWeight: '700',
    letterSpacing: 6,
    color: cores.verdeEscuro,
    marginTop: espaco.sm,
  },
  bolinha: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: cores.fundoCinza,
    alignItems: 'center',
    justifyContent: 'center',
  },
  linhaTempo: {
    width: 2,
    flex: 1,
    minHeight: 20,
    backgroundColor: cores.borda,
    marginTop: 2,
  },
  rodape: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    padding: espaco.lg,
    backgroundColor: cores.branco,
    borderTopWidth: 1,
    borderTopColor: cores.borda,
  },
});
