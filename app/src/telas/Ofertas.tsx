/**
 * As negociações, dos dois lados.
 *
 * A mesma tela serve para quem propôs e para quem recebeu proposta — o que
 * muda é de quem é a vez. Por isso o cartão sempre diz, em uma linha, o que
 * está esperando de você: "responda", "aguardando o vendedor", "fechado".
 *
 * Aceitar uma oferta não cobra nada: o valor combinado passa a valer e o
 * comprador segue para o checkout normal, que é onde o dinheiro se move.
 */

import React, { useCallback, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api } from '../api/cliente';
import type { EstadoOferta, Oferta } from '../api/tipos';
import { Aviso, Botao, Campo, Carregando, Selo, TelaVazia } from '../componentes/base';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { quando, reais } from '../util/formato';
import { confirmar } from '../util/dialogo';

type Props = NativeStackScreenProps<ParametrosApp, 'Ofertas'>;

const rotuloEstado: Record<EstadoOferta, string> = {
  ABERTA: 'esperando resposta',
  CONTRAPROPOSTA: 'contraproposta na mesa',
  ACEITA: 'fechado',
  RECUSADA: 'recusada',
  EXPIRADA: 'passou do prazo',
  CANCELADA: 'cancelada',
};

function tomDoEstado(estado: EstadoOferta): 'verde' | 'cinza' | 'ambar' {
  if (estado === 'ACEITA') return 'verde';
  if (estado === 'ABERTA' || estado === 'CONTRAPROPOSTA') return 'ambar';
  return 'cinza';
}

export function TelaOfertas({ navigation }: Props) {
  const [ofertas, setOfertas] = useState<Oferta[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [aba, setAba] = useState<'ativas' | 'encerradas'>('ativas');
  const [contrapropondo, setContrapropondo] = useState<string | null>(null);
  const [valorContra, setValorContra] = useState('');
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async () => {
    try {
      const r = await api<{ itens: Oferta[] }>('/ofertas');
      setOfertas(r.itens);
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setCarregando(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void carregar();
    }, [carregar]),
  );

  async function agir(id: string, acao: 'aceitar' | 'recusar' | 'cancelar') {
    setErro(null);
    try {
      await api(`/ofertas/${id}/${acao}`, { metodo: 'POST' });
      await carregar();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  async function recusar(o: Oferta) {
    const certeza = await confirmar({
      titulo: 'recusar esta oferta?',
      mensagem: `A negociação de ${reais(o.valorAtual)} será encerrada. A pessoa pode fazer outra proposta depois.`,
      confirmar: 'recusar',
      destrutivo: true,
    });
    if (certeza) await agir(o.id, 'recusar');
  }

  async function enviarContraproposta(o: Oferta) {
    setErro(null);
    const valor = Number(valorContra.replace(/\D/g, ''));
    try {
      await api(`/ofertas/${o.id}/contrapropor`, { metodo: 'POST', corpo: { valor } });
      setContrapropondo(null);
      setValorContra('');
      await carregar();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  const ativas = ofertas.filter((o) => o.estado === 'ABERTA' || o.estado === 'CONTRAPROPOSTA');
  const encerradas = ofertas.filter(
    (o) => o.estado !== 'ABERTA' && o.estado !== 'CONTRAPROPOSTA',
  );
  const lista = aba === 'ativas' ? ativas : encerradas;

  if (carregando) return <Carregando texto="carregando negociações..." />;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md, flex: 1 }]}>negociações</Text>
      </View>

      <View style={e.abas}>
        {(['ativas', 'encerradas'] as const).map((chave) => {
          const ativa = aba === chave;
          const n = chave === 'ativas' ? ativas.length : encerradas.length;
          return (
            <Pressable
              key={chave}
              onPress={() => setAba(chave)}
              style={[e.aba, ativa && e.abaAtiva]}
              accessibilityRole="button"
              accessibilityState={{ selected: ativa }}
            >
              <Text style={[e.abaTexto, ativa && e.abaTextoAtivo]}>
                {chave} ({n})
              </Text>
            </Pressable>
          );
        })}
      </View>

      <ScrollView contentContainerStyle={{ padding: espaco.lg, flexGrow: 1 }}>
        {erro ? <Aviso tom="alerta" texto={erro} /> : null}

        {lista.length === 0 ? (
          <TelaVazia
            icone="pricetags-outline"
            titulo={aba === 'ativas' ? 'nenhuma negociação aberta' : 'nada encerrado ainda'}
            descricao={
              aba === 'ativas'
                ? 'quando você fizer uma oferta, ou receber uma, ela aparece aqui.'
                : 'ofertas aceitas, recusadas ou vencidas ficam guardadas aqui.'
            }
          />
        ) : (
          lista.map((o) => {
            const souComprador = o.meuPapel === 'COMPRADOR';
            const outraPessoa = souComprador
              ? (o.vendedor.apelidoLoja ?? o.vendedor.nome)
              : o.comprador.nome;

            return (
              <View key={o.id} style={e.cartao}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: espaco.sm }}>
                  <Selo texto={rotuloEstado[o.estado]} tom={tomDoEstado(o.estado)} />
                  <Text style={[fonte.pequeno, { flex: 1, textAlign: 'right' }]}>
                    {quando(o.lanceEm)}
                  </Text>
                </View>

                <Text style={[fonte.rotulo, { marginTop: espaco.md }]} numberOfLines={2}>
                  {o.anuncio.titulo.toLowerCase()}
                </Text>
                <Text style={fonte.pequeno}>
                  {souComprador ? 'de' : 'para'} {outraPessoa.toLowerCase()}
                </Text>

                <View style={e.valores}>
                  <View style={{ flex: 1 }}>
                    <Text style={e.rotuloValor}>anunciado</Text>
                    <Text style={e.precoRiscado}>{reais(o.precoAnunciado)}</Text>
                  </View>
                  <Ionicons name="arrow-forward" size={16} color={cores.textoFraco} />
                  <View style={{ flex: 1, alignItems: 'flex-end' }}>
                    <Text style={e.rotuloValor}>
                      {o.ultimoLancePor === 'COMPRADOR' ? 'oferta' : 'contraproposta'}
                    </Text>
                    <Text style={e.precoAtual}>{reais(o.valorAtual)}</Text>
                  </View>
                </View>

                {o.recado ? <Text style={e.recado}>“{o.recado}”</Text> : null}

                {/* ---- o que está esperando de mim ---- */}
                {o.estado === 'ACEITA' ? (
                  souComprador ? (
                    <Botao
                      titulo={`comprar por ${reais(o.valorAtual)}`}
                      aoTocar={() =>
                        navigation.navigate('Checkout', { anuncioId: o.anuncio.id, ofertaId: o.id })
                      }
                    />
                  ) : (
                    <Text style={e.aguardando}>
                      combinado por {reais(o.valorAtual)}. agora é esperar a pessoa pagar.
                    </Text>
                  )
                ) : o.minhaVez ? (
                  contrapropondo === o.id ? (
                    <View style={{ marginTop: espaco.md }}>
                      <Campo
                        rotulo="seu valor"
                        valor={valorContra}
                        aoMudar={setValorContra}
                        dica="R$ 0,00"
                        teclado="numeric"
                        ajuda={`tem que ficar entre ${reais(o.valorAtual)} e ${reais(o.precoAnunciado)}`}
                      />
                      <View style={{ flexDirection: 'row', gap: espaco.sm }}>
                        <View style={{ flex: 1 }}>
                          <Botao
                            titulo="cancelar"
                            variante="vazado"
                            aoTocar={() => {
                              setContrapropondo(null);
                              setValorContra('');
                            }}
                          />
                        </View>
                        <View style={{ flex: 1 }}>
                          <Botao titulo="enviar" aoTocar={() => enviarContraproposta(o)} />
                        </View>
                      </View>
                    </View>
                  ) : (
                    <View style={{ gap: espaco.sm, marginTop: espaco.md }}>
                      <Botao
                        titulo={`aceitar ${reais(o.valorAtual)}`}
                        aoTocar={() => agir(o.id, 'aceitar')}
                      />
                      <View style={{ flexDirection: 'row', gap: espaco.sm }}>
                        {!souComprador ? (
                          <View style={{ flex: 1 }}>
                            <Botao
                              titulo="outro valor"
                              variante="vazado"
                              aoTocar={() => setContrapropondo(o.id)}
                            />
                          </View>
                        ) : null}
                        <View style={{ flex: 1 }}>
                          <Botao titulo="recusar" variante="vazado" aoTocar={() => recusar(o)} />
                        </View>
                      </View>
                    </View>
                  )
                ) : (
                  <View style={{ marginTop: espaco.md }}>
                    <Text style={e.aguardando}>
                      aguardando {souComprador ? 'o vendedor' : 'quem comprou'} responder
                      {o.prazoAte ? ` · vence ${quando(o.prazoAte)}` : ''}
                    </Text>
                    <Pressable onPress={() => agir(o.id, 'cancelar')} hitSlop={8}>
                      <Text style={e.cancelar}>cancelar minha proposta</Text>
                    </Pressable>
                  </View>
                )}
              </View>
            );
          })
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const e = StyleSheet.create({
  topo: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: espaco.lg,
    paddingVertical: espaco.md,
  },
  abas: {
    flexDirection: 'row',
    gap: espaco.sm,
    paddingHorizontal: espaco.lg,
    paddingBottom: espaco.md,
  },
  aba: {
    paddingHorizontal: espaco.lg,
    paddingVertical: espaco.sm,
    borderRadius: raio.pilula,
    backgroundColor: cores.fundoCinza,
  },
  abaAtiva: { backgroundColor: cores.verdeEscuro },
  abaTexto: { fontSize: 13, fontWeight: '600', color: cores.textoSuave },
  abaTextoAtivo: { color: cores.branco },
  cartao: {
    borderWidth: 1,
    borderColor: cores.borda,
    borderRadius: raio.cartao,
    padding: espaco.lg,
    marginBottom: espaco.md,
  },
  valores: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: espaco.md,
    backgroundColor: cores.fundoCinza,
    borderRadius: raio.md,
    padding: espaco.md,
    marginTop: espaco.md,
  },
  rotuloValor: { fontSize: 11, color: cores.textoSuave },
  precoRiscado: {
    fontSize: 15,
    color: cores.textoSuave,
    textDecorationLine: 'line-through',
  },
  precoAtual: { fontSize: 18, fontWeight: '700', color: cores.verdeProfundo },
  recado: {
    marginTop: espaco.md,
    fontSize: 13,
    fontStyle: 'italic',
    color: cores.textoSuave,
  },
  aguardando: { fontSize: 12, color: cores.textoSuave, marginTop: espaco.sm },
  cancelar: {
    marginTop: espaco.sm,
    fontSize: 12,
    fontWeight: '600',
    color: cores.alerta,
  },
});
