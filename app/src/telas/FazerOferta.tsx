/**
 * Fazer uma oferta pelo produto.
 *
 * Em brechó quase ninguém compra pelo preço do anúncio sem antes perguntar
 * "aceita menos?". Esta tela é essa pergunta, com valor e recado.
 *
 * A validação acontece enquanto a pessoa digita, não depois de enviar: se o
 * valor está fora do permitido, o botão fica desligado e o motivo aparece
 * embaixo do campo. O servidor valida de novo — é ele que manda.
 */

import React, { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api } from '../api/cliente';
import { Aviso, Botao, Campo } from '../componentes/base';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { reais } from '../util/formato';
import {
  DIAS_PARA_RESPONDER_OFERTA,
  validarProposta,
  valorMinimoDaOferta,
} from '../regras/limites';

type Props = NativeStackScreenProps<ParametrosApp, 'FazerOferta'>;

/** Converte "R$ 129,90" ou "129,90" em 12990 centavos. */
function paraCentavos(texto: string): number {
  const digitos = texto.replace(/\D/g, '');
  return digitos ? Number(digitos) : 0;
}

function formatar(centavos: number): string {
  if (!centavos) return '';
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

export function TelaFazerOferta({ navigation, route }: Props) {
  const { anuncioId, titulo, preco } = route.params;

  const [valor, setValor] = useState(0);
  const [recado, setRecado] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const minimo = useMemo(() => valorMinimoDaOferta(preco), [preco]);
  const validacao = useMemo(
    () => (valor > 0 ? validarProposta(valor, preco) : { valido: false, motivo: '' }),
    [valor, preco],
  );

  /** Atalhos: os descontos que as pessoas mais tentam. */
  const sugestoes = useMemo(
    () =>
      [0.9, 0.8, 0.7]
        .map((fracao) => Math.round(preco * fracao))
        .filter((v) => v >= minimo),
    [preco, minimo],
  );

  const desconto = valor > 0 ? Math.round((1 - valor / preco) * 100) : 0;

  async function enviar() {
    setErro(null);
    setEnviando(true);
    try {
      await api(`/anuncios/${anuncioId}/ofertas`, {
        metodo: 'POST',
        corpo: { valor, recado: recado.trim() || undefined },
      });
      navigation.replace('Ofertas');
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="fechar">
          <Ionicons name="close" size={26} color={cores.texto} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md, flex: 1 }]}>fazer uma oferta</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: espaco.lg, paddingBottom: espaco.xxl }}>
        <View style={e.produto}>
          <Text style={fonte.rotulo} numberOfLines={2}>
            {titulo.toLowerCase()}
          </Text>
          <Text style={[fonte.pequeno, { marginTop: 2 }]}>
            o vendedor está pedindo {reais(preco)}
          </Text>
        </View>

        <Text style={[fonte.rotulo, { marginTop: espaco.xl, marginBottom: espaco.sm }]}>
          quanto você paga?
        </Text>

        <Campo
          rotulo=""
          valor={formatar(valor)}
          aoMudar={(texto) => setValor(paraCentavos(texto))}
          dica="R$ 0,00"
          teclado="numeric"
        />

        {valor > 0 && validacao.valido ? (
          <View style={e.desconto}>
            <Ionicons name="pricetag" size={15} color={cores.ambarEscuro} />
            <Text style={e.descontoTexto}>
              {desconto}% abaixo do preço pedido — são {reais(preco - valor)} a menos
            </Text>
          </View>
        ) : null}

        {valor > 0 && !validacao.valido ? (
          <Text style={e.motivo}>{validacao.motivo}</Text>
        ) : (
          <Text style={[fonte.pequeno, { marginTop: espaco.sm }]}>
            a menor oferta possível aqui é {reais(minimo)}
          </Text>
        )}

        {sugestoes.length > 0 ? (
          <>
            <Text style={[fonte.pequeno, { marginTop: espaco.lg, marginBottom: espaco.sm }]}>
              ou toque em um valor
            </Text>
            <View style={{ flexDirection: 'row', gap: espaco.sm }}>
              {sugestoes.map((v) => (
                <Pressable
                  key={v}
                  onPress={() => setValor(v)}
                  style={[e.sugestao, valor === v && e.sugestaoAtiva]}
                  accessibilityRole="button"
                  accessibilityState={{ selected: valor === v }}
                >
                  <Text style={[e.sugestaoTexto, valor === v && { color: cores.branco }]}>
                    {reais(v)}
                  </Text>
                </Pressable>
              ))}
            </View>
          </>
        ) : null}

        <View style={{ marginTop: espaco.xl }}>
          <Campo
            rotulo="recado (opcional)"
            valor={recado}
            aoMudar={setRecado}
            dica="ex.: levo hoje mesmo se aceitar"
            multilinha
            ajuda="um recado educado aumenta muito a chance de o vendedor aceitar"
          />
        </View>

        <Aviso
          tom="informacao"
          texto={`O vendedor tem ${DIAS_PARA_RESPONDER_OFERTA} dias para responder. Ele pode aceitar, recusar ou devolver com outro valor. Enquanto isso, o produto continua à venda para outras pessoas.`}
        />

        {erro ? <Aviso tom="alerta" texto={erro} /> : null}
      </ScrollView>

      <View style={e.rodape}>
        <Botao
          titulo={valor > 0 ? `oferecer ${reais(valor)}` : 'oferecer'}
          aoTocar={enviar}
          carregando={enviando}
          desabilitado={!validacao.valido}
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
    paddingVertical: espaco.md,
  },
  produto: {
    backgroundColor: cores.fundoCinza,
    borderRadius: raio.cartao,
    padding: espaco.lg,
  },
  desconto: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: espaco.sm,
    backgroundColor: cores.ambarClaro,
    borderRadius: raio.md,
    paddingHorizontal: espaco.md,
    paddingVertical: espaco.sm,
    marginTop: espaco.sm,
  },
  descontoTexto: { flex: 1, fontSize: 12, color: cores.ambarEscuro, fontWeight: '600' },
  motivo: { marginTop: espaco.sm, fontSize: 12, color: cores.alerta },
  sugestao: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: espaco.md,
    borderRadius: raio.botao,
    borderWidth: 1,
    borderColor: cores.borda,
  },
  sugestaoAtiva: { backgroundColor: cores.verdeEscuro, borderColor: cores.verdeEscuro },
  sugestaoTexto: { fontSize: 14, fontWeight: '700', color: cores.texto },
  rodape: {
    padding: espaco.lg,
    borderTopWidth: 1,
    borderTopColor: cores.borda,
    backgroundColor: cores.fundo,
  },
});
