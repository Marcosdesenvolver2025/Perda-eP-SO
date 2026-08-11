/**
 * Painel administrativo.
 *
 * Duas frentes: a fila de entregas esperando entregador (onde o admin escolhe
 * quem faz cada corrida) e a fila de devoluções a aprovar. O resumo do topo
 * mostra o que precisa de atenção agora.
 */

import React, { useCallback, useState } from 'react';
import {
  Alert,
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
import type { Candidato, Corrida, ResumoAdmin, SolicitacaoDeDevolucao } from '../api/tipos';
import {
  Aviso,
  Botao,
  Campo,
  Carregando,
  Cartao,
  Selo,
  TelaVazia,
} from '../componentes/base';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { quando, reais } from '../util/formato';

type Props = NativeStackScreenProps<ParametrosApp, 'PainelAdmin'>;

export function TelaPainelAdmin({ navigation }: Props) {
  const [aba, setAba] = useState<'entregas' | 'devolucoes'>('entregas');
  const [resumo, setResumo] = useState<ResumoAdmin | null>(null);
  const [fila, setFila] = useState<Corrida[]>([]);
  const [devolucoes, setDevolucoes] = useState<SolicitacaoDeDevolucao[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [atualizando, setAtualizando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async () => {
    setErro(null);

    if (MODO_DEMONSTRACAO) {
      setErro('Modo demonstração: conecte o servidor para operar o painel.');
      setCarregando(false);
      setAtualizando(false);
      return;
    }

    try {
      const [r, f, d] = await Promise.all([
        api<ResumoAdmin>('/admin/resumo'),
        api<{ itens: Corrida[] }>('/admin/entregas/fila'),
        api<{ itens: SolicitacaoDeDevolucao[] }>('/admin/reembolsos'),
      ]);
      setResumo(r);
      setFila(f.itens);
      setDevolucoes(d.itens);
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setCarregando(false);
      setAtualizando(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void carregar();
    }, [carregar]),
  );

  function aprovarDevolucao(item: SolicitacaoDeDevolucao) {
    Alert.alert(
      'aprovar devolução?',
      'Um entregador vai buscar o produto no comprador e levar de volta ao vendedor. O dinheiro só volta quando o vendedor receber o produto.',
      [
        { text: 'cancelar', style: 'cancel' },
        {
          text: 'aprovar',
          onPress: async () => {
            try {
              await api(`/admin/reembolsos/${item.id}/aprovar`, { metodo: 'POST' });
              await carregar();
            } catch (e) {
              Alert.alert('não deu', (e as Error).message);
            }
          },
        },
      ],
    );
  }

  if (carregando) return <Carregando texto="carregando o painel..." />;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>painel</Text>
      </View>

      <ScrollView
        contentContainerStyle={{ paddingBottom: espaco.xxl }}
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
        {erro ? (
          <View style={{ paddingHorizontal: espaco.lg }}>
            <Aviso texto={erro} tom="alerta" />
          </View>
        ) : null}

        {resumo ? (
          <View style={e.grade}>
            <Indicador
              rotulo="esperando entregador"
              valor={String(resumo.aguardandoAtribuicao)}
              destaque={resumo.aguardandoAtribuicao > 0}
            />
            <Indicador rotulo="em rota" valor={String(resumo.corridasEmRota)} />
            <Indicador
              rotulo="devoluções abertas"
              valor={String(resumo.devolucoesAbertas)}
              destaque={resumo.devolucoesAbertas > 0}
            />
            <Indicador rotulo="pedidos ativos" valor={String(resumo.pedidosEmAndamento)} />
            <Indicador rotulo="comissão acumulada" valor={reais(resumo.comissaoAcumulada)} />
            <Indicador rotulo="tarifas acumuladas" valor={reais(resumo.tarifasAcumuladas)} />
            <Indicador rotulo="custo com entregas" valor={reais(resumo.custoComEntregas)} />
            <Indicador rotulo="receita líquida" valor={reais(resumo.receitaLiquida)} />
          </View>
        ) : null}

        <View style={e.abas}>
          {(['entregas', 'devolucoes'] as const).map((chave) => (
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
                {chave === 'entregas'
                  ? `fila de entregas (${fila.length})`
                  : `devoluções (${devolucoes.length})`}
              </Text>
            </Pressable>
          ))}
        </View>

        <View style={{ padding: espaco.lg }}>
          {aba === 'entregas' ? (
            fila.length === 0 ? (
              <TelaVazia
                icone="checkmark-done-outline"
                titulo="fila vazia"
                descricao="nenhuma entrega esperando entregador agora."
              />
            ) : (
              fila.map((corrida) => (
                <CorridaNaFila
                  key={corrida.id}
                  corrida={corrida}
                  aoAtribuir={() =>
                    navigation.navigate('EscolherEntregador', { entregaId: corrida.id })
                  }
                  aoRecolocarNaFila={async () => {
                    try {
                      await api(`/admin/entregas/${corrida.id}/devolver-para-fila`, {
                        metodo: 'POST',
                      });
                      await carregar();
                    } catch (err) {
                      Alert.alert('não deu', (err as Error).message);
                    }
                  }}
                />
              ))
            )
          ) : devolucoes.length === 0 ? (
            <TelaVazia
              icone="checkmark-done-outline"
              titulo="nenhuma devolução aberta"
              descricao="quando alguém pedir devolução, ela aparece aqui."
            />
          ) : (
            devolucoes.map((item) => (
              <Cartao key={item.id} estilo={{ marginBottom: espaco.md }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                  <Selo texto={item.estado.toLowerCase()} tom="alerta" />
                  <Text style={fonte.rotulo}>{reais(item.valorReembolsado)}</Text>
                </View>

                <Text style={[fonte.rotulo, { marginTop: espaco.sm }]}>
                  {item.pedido.anuncio.titulo.toLowerCase()}
                </Text>
                <Text style={fonte.pequeno}>
                  {item.pedido.codigo} · pedido por {item.pedido.comprador.nome}
                </Text>

                <Text style={[fonte.corpo, { marginTop: espaco.md }]}>{item.motivo}</Text>
                {item.descricao ? (
                  <Text style={[fonte.pequeno, { marginTop: 4 }]}>{item.descricao}</Text>
                ) : null}

                {item.estado === 'APROVADO' ? (
                  <View style={{ marginTop: espaco.md }}>
                    <Aviso
                      texto="aprovada. a coleta reversa está na fila de entregas; o estorno sai quando o vendedor receber o produto."
                      tom="informacao"
                    />
                  </View>
                ) : (
                  <View style={{ flexDirection: 'row', gap: espaco.sm, marginTop: espaco.lg }}>
                    <Botao
                      titulo="aprovar"
                      aoTocar={() => aprovarDevolucao(item)}
                      estilo={{ flex: 2 }}
                    />
                    <Botao
                      titulo="recusar"
                      variante="vazado"
                      aoTocar={() =>
                        navigation.navigate('RecusarDevolucao', { reembolsoId: item.id })
                      }
                      estilo={{ flex: 1 }}
                    />
                  </View>
                )}
              </Cartao>
            ))
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function CorridaNaFila({
  corrida,
  aoAtribuir,
  aoRecolocarNaFila,
}: {
  corrida: Corrida;
  aoAtribuir: () => void;
  aoRecolocarNaFila: () => void;
}) {
  const atribuida = corrida.estado === 'ATRIBUIDA';

  return (
    <Cartao estilo={{ marginBottom: espaco.md }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
        <View style={{ flexDirection: 'row', gap: espaco.sm }}>
          <Selo
            texto={atribuida ? 'esperando aceite' : 'sem entregador'}
            tom={atribuida ? 'cinza' : 'verde'}
          />
          {corrida.tipo === 'DEVOLUCAO' ? <Selo texto="devolução" tom="alerta" /> : null}
        </View>
        <Text style={fonte.pequeno}>{quando(corrida.criadoEm)}</Text>
      </View>

      <Text style={[fonte.rotulo, { marginTop: espaco.sm }]}>
        {corrida.pedido.anuncio.titulo.toLowerCase()}
      </Text>
      <Text style={fonte.pequeno}>pedido {corrida.pedido.codigo}</Text>

      <Text style={[fonte.pequeno, { marginTop: espaco.sm }]} numberOfLines={1}>
        de: {corrida.coletaEndereco}
      </Text>
      <Text style={fonte.pequeno} numberOfLines={1}>
        para: {corrida.entregaEndereco}
      </Text>

      {corrida.entregador ? (
        <Text style={[fonte.pequeno, { marginTop: espaco.sm, color: cores.verdeProfundo }]}>
          com {corrida.entregador.nome}, aguardando aceite
        </Text>
      ) : null}

      {corrida.recusas && corrida.recusas.length > 0 ? (
        <View style={{ marginTop: espaco.sm }}>
          <Text style={[fonte.pequeno, { color: cores.alerta }]}>
            {corrida.recusas.length}{' '}
            {corrida.recusas.length === 1 ? 'recusa' : 'recusas'}:{' '}
            {corrida.recusas.map((r) => `${r.entregador.nome} (${r.motivo})`).join('; ')}
          </Text>
        </View>
      ) : null}

      <View style={{ flexDirection: 'row', gap: espaco.sm, marginTop: espaco.lg }}>
        <Botao
          titulo={atribuida ? 'trocar entregador' : 'escolher entregador'}
          aoTocar={aoAtribuir}
          estilo={{ flex: 1 }}
        />
        {atribuida ? (
          <Botao titulo="tirar" variante="vazado" aoTocar={aoRecolocarNaFila} />
        ) : null}
      </View>
    </Cartao>
  );
}

function Indicador({
  rotulo,
  valor,
  destaque,
}: {
  rotulo: string;
  valor: string;
  destaque?: boolean;
}) {
  return (
    <View style={[e.indicador, destaque && { backgroundColor: cores.verdeSuave }]}>
      <Text style={{ fontSize: 20, fontWeight: '700', color: cores.preto }}>{valor}</Text>
      <Text style={fonte.pequeno}>{rotulo}</Text>
    </View>
  );
}

/** Tela de escolha: lista os candidatos já ordenados pelo servidor. */
export function TelaEscolherEntregador({
  navigation,
  route,
}: NativeStackScreenProps<ParametrosApp, 'EscolherEntregador'>) {
  const [candidatos, setCandidatos] = useState<Candidato[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      async function carregar() {
        try {
          const r = await api<{ itens: Candidato[] }>(
            `/admin/entregas/${route.params.entregaId}/candidatos`,
          );
          setCandidatos(r.itens);
        } catch (e) {
          setErro((e as Error).message);
        } finally {
          setCarregando(false);
        }
      }
      void carregar();
    }, [route.params.entregaId]),
  );

  async function atribuir(entregadorId: string) {
    try {
      await api(`/admin/entregas/${route.params.entregaId}/atribuir`, {
        metodo: 'POST',
        corpo: { entregadorId },
      });
      navigation.goBack();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  if (carregando) return <Carregando />;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>escolher entregador</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: espaco.lg, flexGrow: 1 }}>
        {erro ? <Aviso texto={erro} tom="alerta" /> : null}

        {candidatos.length === 0 ? (
          <TelaVazia
            icone="people-outline"
            titulo="ninguém disponível agora"
            descricao="nenhum entregador está livre, ou todos já recusaram esta corrida."
          />
        ) : (
          <>
            <Text style={[fonte.pequeno, { marginBottom: espaco.md }]}>
              ordenados por quem tem menos corrida no momento
            </Text>
            {candidatos.map((c) => (
              <Pressable
                key={c.id}
                onPress={() => atribuir(c.id)}
                style={e.candidato}
                accessibilityRole="button"
              >
                <View style={{ flex: 1 }}>
                  <Text style={fonte.rotulo}>{c.nome}</Text>
                  <Text style={fonte.pequeno}>
                    {c.corridasAtivas} de {c.capacidade}{' '}
                    {c.capacidade === 1 ? 'corrida' : 'corridas'} em andamento
                  </Text>
                </View>
                <Ionicons name="chevron-forward" size={20} color={cores.verdeEscuro} />
              </Pressable>
            ))}
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

/** Recusa de devolução: exige justificativa, que o comprador vai ler. */
export function TelaRecusarDevolucao({
  navigation,
  route,
}: NativeStackScreenProps<ParametrosApp, 'RecusarDevolucao'>) {
  const [motivo, setMotivo] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function enviar() {
    setErro(null);
    setEnviando(true);
    try {
      await api(`/admin/reembolsos/${route.params.reembolsoId}/recusar`, {
        metodo: 'POST',
        corpo: { motivo: motivo.trim() },
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
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="close" size={26} color={cores.texto} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>recusar devolução</Text>
      </View>

      <View style={{ padding: espaco.lg, flex: 1 }}>
        {erro ? <Aviso texto={erro} tom="alerta" /> : null}

        <Aviso
          texto="o comprador vai ler esta justificativa. Recusar dentro do prazo de 7 dias contraria o Código de Defesa do Consumidor — use só quando houver motivo concreto, como produto adulterado."
          tom="alerta"
        />

        <View style={{ marginTop: espaco.md }}>
          <Campo
            rotulo="motivo da recusa"
            valor={motivo}
            aoMudar={setMotivo}
            dica="explique com clareza o que foi verificado"
            multilinha
            maxLength={500}
          />
        </View>

        <Botao
          titulo="recusar devolução"
          aoTocar={enviar}
          carregando={enviando}
          desabilitado={motivo.trim().length < 5}
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
    paddingBottom: espaco.md,
  },
  grade: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: espaco.sm,
    paddingHorizontal: espaco.lg,
    paddingBottom: espaco.lg,
  },
  indicador: {
    flexGrow: 1,
    flexBasis: '47%',
    backgroundColor: cores.fundoCinza,
    borderRadius: raio.md,
    padding: espaco.md,
  },
  abas: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: cores.borda },
  aba: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: espaco.md,
    borderBottomWidth: 3,
    borderBottomColor: 'transparent',
  },
  candidato: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: cores.borda,
    borderRadius: raio.md,
    padding: espaco.lg,
    marginBottom: espaco.sm,
  },
});
