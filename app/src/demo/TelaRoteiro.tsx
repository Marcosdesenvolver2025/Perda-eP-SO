/**
 * Roteiro da demonstração.
 *
 * Um índice clicável para as telas que não estão a um toque de distância na
 * navegação normal — área do entregador, painel do admin, as três telas da
 * confirmação do vendedor — e o botão que troca o papel do usuário, já que
 * o menu da conta esconde os atalhos de entregador e admin por papel.
 *
 * Existe só no modo demonstração. A tela é registrada em `navegacao/index.tsx`
 * atrás da mesma flag.
 */

import React, { useCallback, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api } from '../api/cliente';
import type { Papel, Pedido } from '../api/tipos';
import { Aviso, Cartao, Selo, Separador } from '../componentes/base';
import { useAutenticacao } from '../contextos/Autenticacao';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { reais } from '../util/formato';
import { calcularDescontos, TABELA_DE_TARIFAS } from '../regras/limites';
import { reiniciarDemonstracao } from './servidor';

type Props = NativeStackScreenProps<ParametrosApp, 'Roteiro'>;

const papeis: Array<{ chave: Papel; rotulo: string; explica: string }> = [
  { chave: 'CLIENTE', rotulo: 'cliente', explica: 'compra, vende e acompanha pedidos' },
  { chave: 'ENTREGADOR', rotulo: 'entregador', explica: 'libera a área de corridas' },
  { chave: 'ADMIN', rotulo: 'admin', explica: 'libera o painel da operação' },
];

/** Os pares de preço que mostram o degrau da tabela de tarifas. */
const degraus: Array<[number, number]> = [
  [2_499, 2_500],
  [4_999, 5_000],
  [9_999, 10_000],
  [19_999, 20_000],
  [49_999, 50_000],
];

export function TelaRoteiro({ navigation }: Props) {
  const { usuario, atualizarPerfil, recarregar } = useAutenticacao();
  const [pedidos, setPedidos] = useState<Pedido[]>([]);

  useFocusEffect(
    useCallback(() => {
      async function carregar() {
        try {
          const [compras, vendas] = await Promise.all([
            api<{ itens: Pedido[] }>('/pedidos/compras'),
            api<{ pedidos: Pedido[] }>('/pedidos/vendas'),
          ]);
          setPedidos([...compras.itens, ...vendas.pedidos]);
        } catch {
          setPedidos([]);
        }
      }
      void carregar();
    }, []),
  );

  const acharPorEstado = (estado: Pedido['estado']) => pedidos.find((p) => p.estado === estado);

  const pedidoComCodigo = acharPorEstado('AGUARDANDO_ENTREGA_DO_VENDEDOR');
  const pedidoDeclarado = acharPorEstado('ENTREGA_DECLARADA');
  const pedidoParaDevolver = pedidos.find((p) => p.podePedirReembolso);
  const pedidoParaAvaliar = pedidos.find((p) => p.podeAvaliar);

  async function recomecar() {
    reiniciarDemonstracao();
    await recarregar();
    navigation.navigate('Abas', { screen: 'Home' });
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md, flex: 1 }]}>roteiro da demonstração</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: espaco.lg, paddingBottom: espaco.xxl }}>
        <Aviso
          tom="informacao"
          texto="Tudo aqui roda na memória do navegador. Nenhuma cobrança, nenhum split e nenhum repasse acontece — o que muda some quando você recarrega a página."
        />

        {/* ---------------- papel ---------------- */}
        <Text style={[fonte.secao, { marginTop: espaco.xl }]}>1. escolha o papel</Text>
        <Text style={e.explica}>
          o menu da conta mostra atalhos diferentes para cada papel. troque aqui para ver todos.
        </Text>

        <View style={{ flexDirection: 'row', gap: espaco.sm, marginTop: espaco.md }}>
          {papeis.map((p) => {
            const ativo = usuario?.papel === p.chave;
            return (
              <Pressable
                key={p.chave}
                onPress={() => void atualizarPerfil({ papel: p.chave })}
                style={[e.papel, ativo && e.papelAtivo]}
                accessibilityRole="button"
                accessibilityState={{ selected: ativo }}
              >
                <Text style={[e.papelTexto, ativo && { color: cores.preto }]}>{p.rotulo}</Text>
              </Pressable>
            );
          })}
        </View>
        <Text style={[e.explica, { marginTop: espaco.sm }]}>
          {papeis.find((p) => p.chave === usuario?.papel)?.explica ?? ''}
        </Text>

        {/* ---------------- telas ---------------- */}
        <Text style={[fonte.secao, { marginTop: espaco.xl }]}>2. telas novas</Text>

        <Cartao estilo={{ marginTop: espaco.md }}>
          <Atalho
            icone="pricetag-outline"
            titulo="criar anúncio"
            descricao="o comparativo das duas modalidades, lado a lado, com o líquido de cada uma"
            aoTocar={() => navigation.navigate('NovoAnuncio')}
          />
          <Separador />
          <Atalho
            icone="bicycle-outline"
            titulo="área do entregador"
            descricao="corridas passo a passo; precisa do papel entregador"
            aoTocar={() => navigation.navigate('AreaDoEntregador')}
          />
          <Separador />
          <Atalho
            icone="speedometer-outline"
            titulo="painel do admin"
            descricao="fila de entregas, atribuição e devoluções; precisa do papel admin"
            aoTocar={() => navigation.navigate('PainelAdmin')}
          />
        </Cartao>

        <Text style={[fonte.secao, { marginTop: espaco.xl }]}>3. negociar, curtir, seguir</Text>
        <Text style={e.explica}>
          o que faz um brechó parecer brechó: pechinchar, guardar o que gostou e acompanhar
          quem vende bem.
        </Text>

        <Cartao estilo={{ marginTop: espaco.md }}>
          <Atalho
            icone="pricetags-outline"
            titulo="negociações"
            descricao="ofertas dos dois lados; tem uma esperando você responder"
            aoTocar={() => navigation.navigate('Ofertas')}
          />
          <Separador />
          <Atalho
            icone="heart-outline"
            titulo="o que eu curti"
            descricao="a lista de desejos, com o coração em cada anúncio"
            aoTocar={() => navigation.navigate('Curtidos')}
          />
          <Separador />
          <Atalho
            icone="storefront-outline"
            titulo="lojinha da Rubia"
            descricao="seguir, seguidores e nota do vendedor"
            aoTocar={() => navigation.navigate('Loja', { vendedorId: 'v-rubia' })}
          />
          <Separador />
          <Atalho
            icone="star-outline"
            titulo="avaliar uma compra"
            descricao={
              pedidoParaAvaliar
                ? `pedido ${pedidoParaAvaliar.codigo} — cinco estrelas e um comentário`
                : 'nenhum pedido concluído aguardando avaliação'
            }
            desabilitado={!pedidoParaAvaliar}
            aoTocar={() =>
              pedidoParaAvaliar &&
              navigation.navigate('Avaliar', {
                pedidoId: pedidoParaAvaliar.id,
                vendedor: pedidoParaAvaliar.vendedor?.nome ?? 'vendedor',
              })
            }
          />
        </Cartao>

        <Text style={[fonte.secao, { marginTop: espaco.xl }]}>4. entrega pelo vendedor</Text>
        <Text style={e.explica}>
          as três telas do fluxo sem entregador. o código é o mesmo pedido visto dos dois lados.
        </Text>

        <Cartao estilo={{ marginTop: espaco.md }}>
          <Atalho
            icone="keypad-outline"
            titulo="código do comprador"
            descricao={
              pedidoComCodigo
                ? `pedido ${pedidoComCodigo.codigo} — ${pedidoComCodigo.anuncio.titulo}`
                : 'nenhum pedido aguardando entrega agora'
            }
            desabilitado={!pedidoComCodigo}
            aoTocar={() =>
              pedidoComCodigo &&
              navigation.navigate('CodigoDeConfirmacao', { pedidoId: pedidoComCodigo.id })
            }
          />
          <Separador />
          <Atalho
            icone="checkmark-circle-outline"
            titulo="vendedor digita o código"
            descricao={
              pedidoComCodigo
                ? 'erre o código de propósito para ver a recusa; ou declare sem código'
                : 'nenhum pedido aguardando entrega agora'
            }
            desabilitado={!pedidoComCodigo}
            aoTocar={() =>
              pedidoComCodigo &&
              navigation.navigate('EntregaDoVendedor', { pedidoId: pedidoComCodigo.id })
            }
          />
          <Separador />
          <Atalho
            icone="time-outline"
            titulo="entrega declarada, 3 dias correndo"
            descricao={
              pedidoDeclarado
                ? `pedido ${pedidoDeclarado.codigo} — confirmação automática no prazo`
                : 'nenhuma entrega declarada agora'
            }
            desabilitado={!pedidoDeclarado}
            aoTocar={() =>
              pedidoDeclarado && navigation.navigate('Pedido', { id: pedidoDeclarado.id })
            }
          />
          <Separador />
          <Atalho
            icone="return-down-back-outline"
            titulo="pedir devolução"
            descricao={
              pedidoParaDevolver
                ? `pedido ${pedidoParaDevolver.codigo} — depois aprove no painel do admin`
                : 'nenhum pedido dentro dos 7 dias agora'
            }
            desabilitado={!pedidoParaDevolver}
            aoTocar={() =>
              pedidoParaDevolver &&
              navigation.navigate('Reembolso', { pedidoId: pedidoParaDevolver.id })
            }
          />
        </Cartao>

        {/* ---------------- limites ---------------- */}
        <Text style={[fonte.secao, { marginTop: espaco.xl }]}>5. o bloqueio por tamanho</Text>
        <Text style={e.explica}>
          em “criar anúncio”, digite 25 kg, ou 120 cm de largura, ou 120 cm de altura. a opção
          “entrega pela plataforma” trava e o app explica o motivo; a do vendedor continua livre.
        </Text>
        <View style={e.limites}>
          <Limite rotulo="peso" valor="20 kg" />
          <Limite rotulo="largura" valor="100 cm" />
          <Limite rotulo="altura" valor="100 cm" />
        </View>

        {/* ---------------- degraus ---------------- */}
        <Text style={[fonte.secao, { marginTop: espaco.xl }]}>6. os degraus da tarifa</Text>
        <Text style={e.explica}>
          cada par abaixo está anunciado na vitrine com um centavo de diferença. o líquido do
          vendedor é o da entrega pela plataforma.
        </Text>

        <View style={e.tabela}>
          <View style={[e.linha, e.cabecalho]}>
            <Text style={[e.celula, e.cabecalhoTexto, { flex: 1.2 }]}>preço</Text>
            <Text style={[e.celula, e.cabecalhoTexto]}>tarifa</Text>
            <Text style={[e.celula, e.cabecalhoTexto]}>líquido</Text>
            <Text style={[e.celula, e.cabecalhoTexto, { flex: 0.8 }]}>degrau</Text>
          </View>
          {degraus.map(([antes, depois]) => {
            const a = calcularDescontos(antes, 'PLATAFORMA');
            const d = calcularDescontos(depois, 'PLATAFORMA');
            return (
              <React.Fragment key={antes}>
                <View style={e.linha}>
                  <Text style={[e.celula, { flex: 1.2 }]}>{reais(antes)}</Text>
                  <Text style={e.celula}>{reais(a.tarifa)}</Text>
                  <Text style={e.celula}>{reais(a.vendedor)}</Text>
                  <Text style={[e.celula, { flex: 0.8 }]} />
                </View>
                <View style={[e.linha, e.linhaDestaque]}>
                  <Text style={[e.celula, { flex: 1.2 }]}>{reais(depois)}</Text>
                  <Text style={e.celula}>{reais(d.tarifa)}</Text>
                  <Text style={e.celula}>{reais(d.vendedor)}</Text>
                  <Text style={[e.celula, e.degrau, { flex: 0.8 }]}>
                    −{reais(a.vendedor - d.vendedor)}
                  </Text>
                </View>
              </React.Fragment>
            );
          })}
        </View>

        <Text style={[e.explica, { marginTop: espaco.md }]}>
          a tabela tem {TABELA_DE_TARIFAS.length} faixas. o maior degrau é o de R$ 500,00, onde o
          vendedor recebe R$ 4,00 a menos por vender um centavo mais caro.
        </Text>

        {/* ---------------- recomeçar ---------------- */}
        <Pressable onPress={recomecar} style={e.recomecar} accessibilityRole="button">
          <Ionicons name="refresh-outline" size={18} color={cores.preto} />
          <Text style={{ fontWeight: '700', color: cores.preto }}>recomeçar a demonstração</Text>
        </Pressable>

        <Text style={[fonte.pequeno, { textAlign: 'center', marginTop: espaco.lg }]}>
          o que não funciona no navegador — câmera, upload de foto e notificação — mostra um aviso
          na tela em vez de quebrar.
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

function Atalho({
  icone,
  titulo,
  descricao,
  aoTocar,
  desabilitado,
}: {
  icone: keyof typeof Ionicons.glyphMap;
  titulo: string;
  descricao: string;
  aoTocar: () => void;
  desabilitado?: boolean;
}) {
  return (
    <Pressable
      onPress={aoTocar}
      disabled={desabilitado}
      style={{ flexDirection: 'row', alignItems: 'center', gap: espaco.md, paddingVertical: espaco.md, opacity: desabilitado ? 0.45 : 1 }}
      accessibilityRole="button"
      accessibilityState={{ disabled: !!desabilitado }}
    >
      <Ionicons name={icone} size={22} color={cores.verdeEscuro} />
      <View style={{ flex: 1 }}>
        <Text style={{ fontWeight: '600', color: cores.texto }}>{titulo}</Text>
        <Text style={{ fontSize: 12, color: cores.textoSuave, marginTop: 2 }}>{descricao}</Text>
      </View>
      {desabilitado ? null : (
        <Ionicons name="chevron-forward" size={18} color={cores.textoFraco} />
      )}
    </Pressable>
  );
}

function Limite({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <View style={e.limite}>
      <Text style={{ fontSize: 11, color: cores.textoSuave }}>{rotulo}</Text>
      <Text style={{ fontSize: 16, fontWeight: '700', color: cores.verdeProfundo }}>{valor}</Text>
    </View>
  );
}

const e = StyleSheet.create({
  topo: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: espaco.lg,
    paddingVertical: espaco.md,
  },
  explica: { fontSize: 13, color: cores.textoSuave, marginTop: espaco.xs, lineHeight: 19 },
  papel: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: espaco.sm,
    borderRadius: raio.pilula,
    borderWidth: 1,
    borderColor: cores.borda,
  },
  papelAtivo: { backgroundColor: cores.verde, borderColor: cores.verde },
  papelTexto: { fontSize: 13, fontWeight: '600', color: cores.textoSuave },
  limites: { flexDirection: 'row', gap: espaco.sm, marginTop: espaco.md },
  limite: {
    flex: 1,
    backgroundColor: cores.verdeClaro,
    borderRadius: raio.md,
    padding: espaco.md,
    alignItems: 'center',
  },
  tabela: {
    marginTop: espaco.md,
    borderWidth: 1,
    borderColor: cores.borda,
    borderRadius: raio.md,
    overflow: 'hidden',
  },
  linha: { flexDirection: 'row', paddingVertical: 8, paddingHorizontal: espaco.sm },
  linhaDestaque: { backgroundColor: cores.fundoCinza },
  cabecalho: { backgroundColor: cores.verdeClaro },
  cabecalhoTexto: { fontWeight: '700', color: cores.verdeProfundo, fontSize: 11 },
  celula: { flex: 1, fontSize: 12, color: cores.texto },
  degrau: { color: cores.alerta, fontWeight: '700', textAlign: 'right' },
  recomecar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: espaco.sm,
    marginTop: espaco.xl,
    paddingVertical: espaco.md,
    borderRadius: raio.pilula,
    backgroundColor: cores.verdeSuave,
  },
});
