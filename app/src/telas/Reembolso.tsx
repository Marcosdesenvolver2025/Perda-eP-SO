/**
 * Pedido de devolução dentro dos 4 dias.
 *
 * A tela mostra ANTES de confirmar quanto volta e quanto fica retido: quando a
 * entrega foi feita pelos nossos entregadores, a comissão e o frete são
 * cobrados do mesmo jeito, porque o serviço já foi prestado.
 */

import React, { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import { Aviso, Botao, Campo, Carregando, Cartao, Separador } from '../componentes/base';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { reais } from '../util/formato';

type Props = NativeStackScreenProps<ParametrosApp, 'Reembolso'>;

interface Previa {
  valorPago: number;
  valorReembolsado: number;
  valorRetido: number;
  explicacao: string;
  prazoTesteAte: string | null;
}

const motivos = [
  'não é o que estava no anúncio',
  'chegou com defeito',
  'chegou danificado',
  'não serviu / não coube',
  'mudei de ideia',
  'outro motivo',
];

export function TelaReembolso({ navigation, route }: Props) {
  const [previa, setPrevia] = useState<Previa | null>(null);
  const [motivo, setMotivo] = useState<string | null>(null);
  const [descricao, setDescricao] = useState('');
  const [carregando, setCarregando] = useState(true);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    async function carregar() {
      if (MODO_DEMONSTRACAO) {
        setPrevia({
          valorPago: 12_800,
          valorReembolsado: 8_200,
          valorRetido: 4_600,
          explicacao:
            'A comissão e o frete não são devolvidos porque a entrega já foi feita pelos nossos entregadores.',
          prazoTesteAte: null,
        });
        setCarregando(false);
        return;
      }
      try {
        setPrevia(await api<Previa>(`/pedidos/${route.params.pedidoId}/reembolso/previa`));
      } catch (e) {
        setErro((e as Error).message);
      } finally {
        setCarregando(false);
      }
    }
    void carregar();
  }, [route.params.pedidoId]);

  async function enviar() {
    if (!motivo) return;
    setErro(null);

    if (MODO_DEMONSTRACAO) {
      setErro('Modo demonstração: conecte o servidor para abrir a devolução.');
      return;
    }

    setEnviando(true);
    try {
      await api(`/pedidos/${route.params.pedidoId}/reembolso`, {
        metodo: 'POST',
        corpo: { motivo, descricao: descricao.trim() || undefined },
      });
      navigation.goBack();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  if (carregando) return <Carregando />;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>pedir devolução</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: espaco.lg, paddingBottom: 120 }}>
        {erro ? <Aviso texto={erro} tom="alerta" /> : null}

        {previa ? (
          <Cartao>
            <Text style={[fonte.rotulo, { marginBottom: espaco.md }]}>o que volta pra você</Text>
            <Linha rotulo="você pagou" valor={reais(previa.valorPago)} />
            {previa.valorRetido > 0 ? (
              <Linha rotulo="taxa retida" valor={`- ${reais(previa.valorRetido)}`} />
            ) : null}
            <View style={{ height: espaco.sm }} />
            <Separador />
            <View style={{ height: espaco.sm }} />
            <Linha rotulo="você recebe de volta" valor={reais(previa.valorReembolsado)} destaque />

            {previa.valorRetido > 0 ? (
              <View style={{ marginTop: espaco.md }}>
                <Aviso texto={previa.explicacao} tom="alerta" />
              </View>
            ) : null}
          </Cartao>
        ) : null}

        <Text style={[fonte.rotulo, { marginTop: espaco.xl, marginBottom: espaco.md }]}>
          por que você quer devolver?
        </Text>

        {motivos.map((item) => (
          <Pressable
            key={item}
            onPress={() => setMotivo(item)}
            accessibilityRole="radio"
            accessibilityState={{ selected: motivo === item }}
            style={[e.motivo, motivo === item && { borderColor: cores.verde, backgroundColor: cores.verdeClaro }]}
          >
            <Ionicons
              name={motivo === item ? 'radio-button-on' : 'radio-button-off'}
              size={20}
              color={motivo === item ? cores.verde : cores.textoFraco}
            />
            <Text style={[fonte.corpo, { marginLeft: espaco.md }]}>{item}</Text>
          </Pressable>
        ))}

        <View style={{ height: espaco.lg }} />
        <Campo
          rotulo="conte o que aconteceu"
          valor={descricao}
          aoMudar={setDescricao}
          dica="quanto mais detalhes, mais rápido a gente resolve"
          multilinha
          maxLength={2000}
        />

        <Text style={fonte.pequeno}>
          a gente analisa em até 2 dias úteis. se aprovado, o valor volta pela mesma forma de
          pagamento em até 10 dias, conforme o prazo do seu banco.
        </Text>
      </ScrollView>

      <View style={e.rodape}>
        <Botao
          titulo="enviar pedido de devolução"
          aoTocar={enviar}
          carregando={enviando}
          desabilitado={!motivo}
        />
      </View>
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
      <Text style={destaque ? [fonte.rotulo, { fontSize: 17 }] : fonte.corpo}>{valor}</Text>
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
  motivo: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: cores.borda,
    borderRadius: raio.md,
    padding: espaco.lg,
    marginBottom: espaco.sm,
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
