/**
 * Telas de lista: minhas compras, minhas vendas e minha lojinha.
 * São parecidas o bastante para morarem no mesmo arquivo.
 */

import React, { useCallback, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api } from '../api/cliente';
import type { Anuncio, Pedido } from '../api/tipos';
import { Botao, Cartao, Selo, TelaVazia } from '../componentes/base';
import { GradeDeProdutos } from '../componentes/produto';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { prazoDeTeste, quando, reais } from '../util/formato';

function Cabecalho({
  titulo,
  aoVoltar,
}: {
  titulo: string;
  aoVoltar: () => void;
}) {
  return (
    <View style={e.topo}>
      <Pressable onPress={aoVoltar} hitSlop={12} accessibilityLabel="voltar">
        <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
      </Pressable>
      <Text style={[fonte.secao, { marginLeft: espaco.md }]}>{titulo}</Text>
    </View>
  );
}

// ---------------------------------------------------------------------------
// minhas compras
// ---------------------------------------------------------------------------

export function TelaMinhasCompras({
  navigation,
}: NativeStackScreenProps<ParametrosApp, 'MinhasCompras'>) {
  const [pedidos, setPedidos] = useState<Pedido[]>([]);

  useFocusEffect(
    useCallback(() => {
      async function carregar() {
        try {
          const resposta = await api<{ itens: Pedido[] }>('/pedidos/compras');
          setPedidos(resposta.itens);
        } catch {
          setPedidos([]);
        }
      }
      void carregar();
    }, []),
  );

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <Cabecalho titulo="minhas compras" aoVoltar={navigation.goBack} />

      <ScrollView contentContainerStyle={{ padding: espaco.lg, flexGrow: 1 }}>
        {pedidos.length === 0 ? (
          <TelaVazia
            icone="bag-handle-outline"
            titulo="você ainda não comprou nada"
            descricao="dá uma olhada no que o pessoal está vendendo aqui em Itinga."
            acao={{ titulo: 'ver produtos', aoTocar: () => navigation.navigate('Busca', {}) }}
          />
        ) : (
          pedidos.map((pedido) => {
            const aviso = prazoDeTeste(pedido.diasRestantesParaTestar ?? null);
            return (
              <Cartao
                key={pedido.id}
                estilo={{ marginBottom: espaco.md }}
                aoTocar={() => navigation.navigate('Pedido', { id: pedido.id })}
              >
                <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                  <Text style={fonte.pequeno}>{pedido.codigo}</Text>
                  <Text style={fonte.pequeno}>{quando(pedido.criadoEm)}</Text>
                </View>

                <Text style={[fonte.rotulo, { marginTop: espaco.sm }]} numberOfLines={1}>
                  {pedido.anuncio.titulo.toLowerCase()}
                </Text>
                <Text style={fonte.corpo}>{reais(pedido.valorTotal)}</Text>

                <View style={{ flexDirection: 'row', gap: espaco.sm, marginTop: espaco.md, flexWrap: 'wrap' }}>
                  <Selo texto={pedido.estado.toLowerCase().replace(/_/g, ' ')} tom="cinza" />
                  {pedido.estado === 'ENTREGUE' && aviso ? (
                    <Selo
                      texto={aviso}
                      tom={(pedido.diasRestantesParaTestar ?? 0) <= 1 ? 'alerta' : 'verde'}
                    />
                  ) : null}
                </View>

                {pedido.podePedirReembolso ? (
                  <Botao
                    titulo="pedir devolução"
                    variante="vazado"
                    aoTocar={() => navigation.navigate('Reembolso', { pedidoId: pedido.id })}
                    estilo={{ marginTop: espaco.md }}
                  />
                ) : null}
              </Cartao>
            );
          })
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

// ---------------------------------------------------------------------------
// minhas vendas
// ---------------------------------------------------------------------------

interface Extrato {
  aReceber: number;
  recebido: number;
  diasParaLiberar: number;
  pedidos: Array<{
    id: string;
    codigo: string;
    estado: string;
    valorProduto: number;
    valorComissao: number;
    valorTarifa: number;
    taxaComissao: number;
    valorVendedor: number;
    modalidade: 'PLATAFORMA' | 'VENDEDOR';
    repassadoEm?: string | null;
    anuncio: { titulo: string };
  }>;
}

export function TelaMinhasVendas({
  navigation,
}: NativeStackScreenProps<ParametrosApp, 'MinhasVendas'>) {
  const [extrato, setExtrato] = useState<Extrato | null>(null);

  useFocusEffect(
    useCallback(() => {
      async function carregar() {
        try {
          setExtrato(await api<Extrato>('/pedidos/vendas'));
        } catch {
          setExtrato(null);
        }
      }
      void carregar();
    }, []),
  );

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <Cabecalho titulo="minhas vendas" aoVoltar={navigation.goBack} />

      <ScrollView contentContainerStyle={{ padding: espaco.lg, flexGrow: 1 }}>
        <View style={{ flexDirection: 'row', gap: espaco.md }}>
          <View style={[e.caixaResumo, { backgroundColor: cores.verdeSuave }]}>
            <Text style={{ fontSize: 12, color: cores.verdeProfundo }}>a receber</Text>
            <Text style={{ fontSize: 20, fontWeight: '700', color: cores.preto }}>
              {reais(extrato?.aReceber ?? 0)}
            </Text>
          </View>
          <View style={[e.caixaResumo, { backgroundColor: cores.fundoCinza }]}>
            <Text style={{ fontSize: 12, color: cores.textoSuave }}>já recebido</Text>
            <Text style={{ fontSize: 20, fontWeight: '700', color: cores.preto }}>
              {reais(extrato?.recebido ?? 0)}
            </Text>
          </View>
        </View>

        <Text style={[fonte.pequeno, { marginTop: espaco.md }]}>
          o valor de cada venda é liberado {extrato?.diasParaLiberar ?? 7} dias depois que o
          produto chega ao comprador — é o prazo dele para testar e pedir devolução.
        </Text>

        <View style={{ height: espaco.xl }} />

        {!extrato || extrato.pedidos.length === 0 ? (
          <TelaVazia
            icone="storefront-outline"
            titulo="nenhuma venda ainda"
            descricao="publique um anúncio e comece a vender aqui na cidade."
            acao={{ titulo: 'criar anúncio', aoTocar: () => navigation.navigate('NovoAnuncio') }}
          />
        ) : (
          extrato.pedidos.map((venda) => (
            <Cartao
              key={venda.id}
              estilo={{ marginBottom: espaco.md }}
              aoTocar={() => navigation.navigate('Pedido', { id: venda.id })}
            >
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <Text style={fonte.pequeno}>{venda.codigo}</Text>
                <Selo
                  texto={venda.repassadoEm ? 'pago' : 'a receber'}
                  tom={venda.repassadoEm ? 'cinza' : 'verde'}
                />
              </View>

              <Text style={[fonte.rotulo, { marginTop: espaco.sm }]} numberOfLines={1}>
                {venda.anuncio.titulo.toLowerCase()}
              </Text>

              <View style={{ marginTop: espaco.md }}>
                <LinhaValor rotulo="preço do produto" valor={reais(venda.valorProduto)} />
                <LinhaValor
                  rotulo={`comissão (${Math.round(venda.taxaComissao * 100)}%)`}
                  valor={`- ${reais(venda.valorComissao)}`}
                />
                {venda.valorTarifa > 0 ? (
                  <LinhaValor rotulo="tarifa da venda" valor={`- ${reais(venda.valorTarifa)}`} />
                ) : null}
                <LinhaValor rotulo="você recebe" valor={reais(venda.valorVendedor)} destaque />
              </View>

              {venda.modalidade === 'VENDEDOR' &&
              ['AGUARDANDO_ENTREGA_DO_VENDEDOR', 'ENTREGA_DECLARADA'].includes(venda.estado) ? (
                <Botao
                  titulo={
                    venda.estado === 'ENTREGA_DECLARADA'
                      ? 'confirmar com o código'
                      : 'registrar entrega'
                  }
                  aoTocar={() =>
                    navigation.navigate('EntregaDoVendedor', { pedidoId: venda.id })
                  }
                  estilo={{ marginTop: espaco.md }}
                />
              ) : null}
            </Cartao>
          ))
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function LinhaValor({
  rotulo,
  valor,
  destaque,
}: {
  rotulo: string;
  valor: string;
  destaque?: boolean;
}) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 2 }}>
      <Text style={destaque ? fonte.rotulo : fonte.pequeno}>{rotulo}</Text>
      <Text style={destaque ? fonte.rotulo : fonte.pequeno}>{valor}</Text>
    </View>
  );
}

// ---------------------------------------------------------------------------
// minha lojinha
// ---------------------------------------------------------------------------

export function TelaMinhaLoja({
  navigation,
}: NativeStackScreenProps<ParametrosApp, 'MinhaLoja'>) {
  const [anuncios, setAnuncios] = useState<Anuncio[]>([]);

  useFocusEffect(
    useCallback(() => {
      async function carregar() {
        try {
          const resposta = await api<{ itens: Anuncio[] }>('/anuncios/meus/lista');
          setAnuncios(resposta.itens);
        } catch {
          setAnuncios([]);
        }
      }
      void carregar();
    }, []),
  );

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <Cabecalho titulo="minha lojinha" aoVoltar={navigation.goBack} />

      {anuncios.length === 0 ? (
        <TelaVazia
          icone="pricetags-outline"
          titulo="sua lojinha está vazia"
          descricao="publique o primeiro anúncio e apareça na vitrine da cidade."
          acao={{ titulo: 'criar anúncio', aoTocar: () => navigation.navigate('NovoAnuncio') }}
        />
      ) : (
        <GradeDeProdutos
          anuncios={anuncios}
          aoTocarProduto={(id) => navigation.navigate('Produto', { id })}
          cabecalho={
            <Text style={[fonte.pequeno, { padding: espaco.lg }]}>
              {anuncios.length} {anuncios.length === 1 ? 'anúncio' : 'anúncios'} na sua loja
            </Text>
          }
          rodape={
            <View style={{ padding: espaco.lg }}>
              <Botao
                titulo="criar novo anúncio"
                icone="add"
                aoTocar={() => navigation.navigate('NovoAnuncio')}
              />
            </View>
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
    paddingBottom: espaco.md,
  },
  caixaResumo: {
    flex: 1,
    borderRadius: raio.lg,
    padding: espaco.lg,
  },
});
