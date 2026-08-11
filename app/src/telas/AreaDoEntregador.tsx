/**
 * Área do entregador.
 *
 * Três abas: corridas oferecidas (aceitar ou recusar), as que estou tocando
 * agora e o histórico. Cada corrida em andamento vira um cartão com UM botão
 * principal — o próximo passo do fluxo — para não ter dúvida na rua.
 */

import React, { useCallback, useState } from 'react';
import {
  Linking,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import type { Corrida, EstadoEntrega } from '../api/tipos';
import { Aviso, Botao, Cartao, Selo, TelaVazia } from '../componentes/base';
import { corridasDemo } from '../dados/exemplo';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { peso, quando, reais } from '../util/formato';

type Props = NativeStackScreenProps<ParametrosApp, 'AreaDoEntregador'>;

const rotuloEstado: Record<EstadoEntrega, string> = {
  AGUARDANDO_ATRIBUICAO: 'na fila',
  ATRIBUIDA: 'esperando você aceitar',
  RECUSADA: 'recusada',
  ACEITA: 'aceita — vá buscar',
  A_CAMINHO_DA_COLETA: 'indo buscar',
  CHEGOU_NA_COLETA: 'no local da coleta',
  PRODUTO_COLETADO: 'produto com você',
  EM_ROTA_PARA_ENTREGA: 'levando ao comprador',
  CHEGOU_NA_ENTREGA: 'no local da entrega',
  ENTREGUE: 'entregue',
  CANCELADA: 'cancelada',
};

/** O próximo passo de cada estado: rótulo do botão e rota da API. */
const PROXIMO_PASSO: Partial<
  Record<EstadoEntrega, { titulo: string; caminho: string; abreTela?: boolean }>
> = {
  ACEITA: { titulo: 'sair para a coleta', caminho: 'a-caminho' },
  A_CAMINHO_DA_COLETA: { titulo: 'cheguei na coleta', caminho: 'cheguei-na-coleta' },
  CHEGOU_NA_COLETA: { titulo: 'peguei o produto', caminho: 'coletei', abreTela: true },
  PRODUTO_COLETADO: { titulo: 'sair para entrega', caminho: 'sair-para-entrega' },
  EM_ROTA_PARA_ENTREGA: { titulo: 'cheguei no comprador', caminho: 'cheguei-na-entrega' },
  CHEGOU_NA_ENTREGA: { titulo: 'confirmar entrega', caminho: 'entreguei', abreTela: true },
};

export function TelaAreaDoEntregador({ navigation }: Props) {
  const [aba, setAba] = useState<'oferecidas' | 'minhas' | 'historico'>('oferecidas');
  const [oferecidas, setOferecidas] = useState<Corrida[]>([]);
  const [minhas, setMinhas] = useState<Corrida[]>([]);
  const [historico, setHistorico] = useState<Corrida[]>([]);
  const [extrato, setExtrato] = useState<{ ganhoHoje: number; ganhoTotal: number } | null>(null);
  const [disponivel, setDisponivel] = useState(true);
  const [atualizando, setAtualizando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async () => {
    setErro(null);

    if (MODO_DEMONSTRACAO) {
      setOferecidas(corridasDemo.filter((c) => c.estado === 'ATRIBUIDA'));
      setMinhas(corridasDemo.filter((c) => !['ATRIBUIDA', 'ENTREGUE'].includes(c.estado)));
      setHistorico(corridasDemo.filter((c) => c.estado === 'ENTREGUE'));
      setExtrato({ ganhoHoje: 1_500, ganhoTotal: 12_500 });
      setAtualizando(false);
      return;
    }

    try {
      const [a, b, c] = await Promise.all([
        api<{ itens: Corrida[] }>('/entregas/oferecidas'),
        api<{ itens: Corrida[] }>('/entregas/minhas'),
        api<{ itens: Corrida[]; extrato: { ganhoHoje: number; ganhoTotal: number } }>(
          '/entregas/historico',
        ),
      ]);
      setOferecidas(a.itens);
      setMinhas(b.itens);
      setHistorico(c.itens);
      setExtrato(c.extrato);
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

  async function alternarDisponibilidade(valor: boolean) {
    setDisponivel(valor);
    if (MODO_DEMONSTRACAO) return;
    try {
      await api('/entregas/disponibilidade', { metodo: 'POST', corpo: { disponivel: valor } });
    } catch {
      setDisponivel(!valor); // desfaz se o servidor recusou
    }
  }

  async function avancar(corrida: Corrida) {
    const passo = PROXIMO_PASSO[corrida.estado];
    if (!passo) return;

    // passos que precisam de foto, volumes ou código abrem tela própria
    if (passo.abreTela) {
      navigation.navigate('PassoDaEntrega', {
        entregaId: corrida.id,
        passo: corrida.estado === 'CHEGOU_NA_COLETA' ? 'coleta' : 'entrega',
      });
      return;
    }

    try {
      await api(`/entregas/${corrida.id}/${passo.caminho}`, { metodo: 'POST' });
      await carregar();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  async function aceitar(corrida: Corrida) {
    try {
      await api(`/entregas/${corrida.id}/aceitar`, { metodo: 'POST' });
      await carregar();
      setAba('minhas');
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  const lista = aba === 'oferecidas' ? oferecidas : aba === 'minhas' ? minhas : historico;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md, flex: 1 }]}>entregas</Text>
        <Switch
          value={disponivel}
          onValueChange={alternarDisponibilidade}
          trackColor={{ true: cores.verdeSuave, false: cores.borda }}
          thumbColor={disponivel ? cores.verde : cores.textoFraco}
          accessibilityLabel="disponível para receber corridas"
        />
      </View>

      <View style={e.ganhos}>
        <View style={{ flex: 1 }}>
          <Text style={{ fontSize: 13, color: cores.verdeProfundo }}>
            {disponivel ? 'você está disponível' : 'você está indisponível'}
          </Text>
          <Text style={{ fontSize: 24, fontWeight: '700', color: cores.preto }}>
            {reais(extrato?.ganhoHoje ?? 0)}
          </Text>
          <Text style={{ fontSize: 12, color: cores.verdeProfundo }}>ganho hoje</Text>
        </View>
        <Ionicons name="bicycle" size={40} color={cores.verdeProfundo} style={{ opacity: 0.4 }} />
      </View>

      <View style={e.abas}>
        {(['oferecidas', 'minhas', 'historico'] as const).map((chave) => (
          <Pressable
            key={chave}
            onPress={() => setAba(chave)}
            style={[e.aba, aba === chave && { borderBottomColor: cores.verde }]}
            accessibilityRole="tab"
            accessibilityState={{ selected: aba === chave }}
          >
            <Text
              style={{
                fontSize: 13,
                fontWeight: aba === chave ? '700' : '400',
                color: aba === chave ? cores.verdeEscuro : cores.textoSuave,
              }}
            >
              {chave === 'oferecidas'
                ? `novas (${oferecidas.length})`
                : chave === 'minhas'
                  ? `em andamento (${minhas.length})`
                  : 'histórico'}
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
            titulo={
              aba === 'oferecidas'
                ? 'nenhuma entrega nova'
                : aba === 'minhas'
                  ? 'você não tem corrida ativa'
                  : 'nada entregue ainda'
            }
            descricao={
              aba === 'oferecidas'
                ? disponivel
                  ? 'quando o administrador te escolher para uma entrega, ela aparece aqui.'
                  : 'ligue sua disponibilidade no botão lá em cima para receber entregas.'
                : aba === 'minhas'
                  ? 'aceite uma entrega na aba "novas".'
                  : 'suas entregas concluídas ficam guardadas aqui.'
            }
          />
        ) : (
          lista.map((corrida) => (
            <CartaoDaCorrida
              key={corrida.id}
              corrida={corrida}
              aba={aba}
              aoAceitar={() => aceitar(corrida)}
              aoRecusar={() =>
                navigation.navigate('PassoDaEntrega', {
                  entregaId: corrida.id,
                  passo: 'recusa',
                })
              }
              aoAvancar={() => avancar(corrida)}
            />
          ))
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function CartaoDaCorrida({
  corrida,
  aba,
  aoAceitar,
  aoRecusar,
  aoAvancar,
}: {
  corrida: Corrida;
  aba: 'oferecidas' | 'minhas' | 'historico';
  aoAceitar: () => void;
  aoRecusar: () => void;
  aoAvancar: () => void;
}) {
  const passo = PROXIMO_PASSO[corrida.estado];
  const devolucao = corrida.tipo === 'DEVOLUCAO';
  // antes de coletar o foco é a coleta; depois, a entrega
  const antesDaColeta = ['ATRIBUIDA', 'ACEITA', 'A_CAMINHO_DA_COLETA', 'CHEGOU_NA_COLETA'].includes(
    corrida.estado,
  );
  const telefone = antesDaColeta ? corrida.coletaTelefone : corrida.entregaTelefone;

  return (
    <Cartao estilo={{ marginBottom: espaco.md }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <View style={{ flexDirection: 'row', gap: espaco.sm }}>
          <Selo
            texto={rotuloEstado[corrida.estado]}
            tom={corrida.estado === 'ATRIBUIDA' ? 'verde' : 'cinza'}
          />
          {devolucao ? <Selo texto="devolução" tom="alerta" /> : null}
        </View>
        <Text style={fonte.rotulo}>{reais(corrida.valorEntregador)}</Text>
      </View>

      <Text style={[fonte.rotulo, { marginTop: espaco.md }]}>
        {corrida.pedido.anuncio.titulo.toLowerCase()}
      </Text>
      <Text style={fonte.pequeno}>
        pedido {corrida.pedido.codigo} · {quando(corrida.criadoEm)}
      </Text>

      {corrida.pedido.anuncio.pesoG ? (
        <Text style={[fonte.pequeno, { marginTop: 4 }]}>
          {peso(corrida.pedido.anuncio.pesoG)} · {corrida.pedido.anuncio.comprimentoCm}×
          {corrida.pedido.anuncio.larguraCm}×{corrida.pedido.anuncio.alturaCm} cm
        </Text>
      ) : null}

      <View style={{ marginTop: espaco.md, gap: espaco.sm }}>
        <LinhaEndereco
          icone="arrow-up-circle-outline"
          rotulo={`coleta · ${corrida.coletaContato ?? ''}`}
          texto={corrida.coletaEndereco}
          referencia={corrida.coletaReferencia}
          destacado={antesDaColeta}
        />
        <LinhaEndereco
          icone="arrow-down-circle-outline"
          rotulo={`entrega · ${corrida.entregaContato ?? ''}`}
          texto={corrida.entregaEndereco}
          referencia={corrida.entregaReferencia}
          destacado={!antesDaColeta}
        />
      </View>

      {corrida.observacoes ? (
        <Text style={[fonte.pequeno, { marginTop: espaco.sm, fontStyle: 'italic' }]}>
          {corrida.observacoes}
        </Text>
      ) : null}

      {!corrida.telefoneLiberado && aba === 'oferecidas' ? (
        <Text style={[fonte.pequeno, { marginTop: espaco.sm }]}>
          o telefone aparece quando você aceitar a corrida
        </Text>
      ) : null}

      {aba === 'oferecidas' ? (
        <View style={{ flexDirection: 'row', gap: espaco.sm, marginTop: espaco.lg }}>
          <Botao titulo="aceitar" aoTocar={aoAceitar} estilo={{ flex: 2 }} />
          <Botao titulo="recusar" variante="vazado" aoTocar={aoRecusar} estilo={{ flex: 1 }} />
        </View>
      ) : aba === 'minhas' && passo ? (
        <View style={{ flexDirection: 'row', gap: espaco.sm, marginTop: espaco.lg }}>
          <Botao titulo={passo.titulo} aoTocar={aoAvancar} estilo={{ flex: 1 }} />
          {telefone ? (
            <Pressable
              onPress={() => Linking.openURL(`tel:${telefone.replace(/\D/g, '')}`)}
              style={e.botaoIcone}
              accessibilityLabel="ligar"
            >
              <Ionicons name="call-outline" size={20} color={cores.verdeEscuro} />
            </Pressable>
          ) : null}
        </View>
      ) : null}
    </Cartao>
  );
}

function LinhaEndereco({
  icone,
  rotulo,
  texto,
  referencia,
  destacado,
}: {
  icone: keyof typeof Ionicons.glyphMap;
  rotulo: string;
  texto: string;
  referencia?: string | null;
  destacado?: boolean;
}) {
  return (
    <View
      style={[
        { flexDirection: 'row', alignItems: 'flex-start', padding: espaco.sm, borderRadius: raio.sm },
        destacado && { backgroundColor: cores.verdeClaro },
      ]}
    >
      <Ionicons name={icone} size={18} color={cores.verdeEscuro} style={{ marginTop: 1 }} />
      <View style={{ flex: 1, marginLeft: espaco.sm }}>
        <Text style={fonte.pequeno}>{rotulo}</Text>
        <Text style={fonte.corpo}>{texto}</Text>
        {referencia ? (
          <Text style={[fonte.pequeno, { fontStyle: 'italic' }]}>{referencia}</Text>
        ) : null}
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
  abas: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: cores.borda },
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
