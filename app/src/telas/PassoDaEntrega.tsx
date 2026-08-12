/**
 * Passos da corrida que precisam de mais que um toque:
 *  - coleta: foto do pacote e quantidade de volumes;
 *  - entrega: código de confirmação e foto opcional;
 *  - recusa: motivo.
 *
 * Cada um é uma tela cheia, com um botão só, porque o entregador está na rua,
 * muitas vezes de capacete e com o celular na mão.
 */

import React, { useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { tirarFotoAgora } from '../util/fotos';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api } from '../api/cliente';
import { Aviso, Botao, Campo } from '../componentes/base';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';

type Props = NativeStackScreenProps<ParametrosApp, 'PassoDaEntrega'>;

const MOTIVOS_DE_RECUSA = [
  'estou longe demais',
  'o pacote é grande demais pra mim',
  'não consigo agora',
  'endereço fora da minha área',
  'outro motivo',
];

export function TelaPassoDaEntrega({ navigation, route }: Props) {
  const { entregaId, passo } = route.params;

  const [foto, setFoto] = useState<string | null>(null);
  const [volumes, setVolumes] = useState(1);
  const [codigo, setCodigo] = useState('');
  const [motivo, setMotivo] = useState<string | null>(null);
  const [observacao, setObservacao] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function tirarFoto() {
    setErro(null);
    const { uris, aviso } = await tirarFotoAgora();
    if (aviso) setErro(aviso);
    if (uris[0]) setFoto(uris[0]);
  }

  async function enviar() {
    setErro(null);

    setEnviando(true);
    try {
      if (passo === 'coleta') {
        // TODO na sua infra: subir a foto para o storage e mandar a URL.
        await api(`/entregas/${entregaId}/coletei`, {
          metodo: 'POST',
          corpo: { fotoPacote: foto, volumes },
        });
      } else if (passo === 'entrega') {
        await api(`/entregas/${entregaId}/entreguei`, {
          metodo: 'POST',
          corpo: {
            codigoConfirmacao: codigo.trim(),
            ...(foto ? { fotoEntrega: foto } : {}),
          },
        });
      } else {
        await api(`/entregas/${entregaId}/recusar`, {
          metodo: 'POST',
          corpo: {
            motivo: [motivo, observacao.trim()].filter(Boolean).join(' — '),
          },
        });
      }
      navigation.goBack();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  const titulo =
    passo === 'coleta'
      ? 'confirmar coleta'
      : passo === 'entrega'
        ? 'confirmar entrega'
        : 'recusar entrega';

  const podeEnviar =
    passo === 'coleta'
      ? !!foto && volumes >= 1
      : passo === 'entrega'
        ? codigo.trim().length >= 4
        : !!motivo;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="close" size={26} color={cores.texto} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>{titulo}</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: espaco.lg, paddingBottom: 120 }}>
        {erro ? <Aviso texto={erro} tom="alerta" /> : null}

        {passo === 'coleta' ? (
          <>
            <Text style={[fonte.corpo, { marginBottom: espaco.lg }]}>
              Tire uma foto do pacote antes de sair. Ela protege você e o vendedor se algo
              acontecer no caminho.
            </Text>

            <Pressable onPress={tirarFoto} style={e.areaFoto} accessibilityRole="button">
              {foto ? (
                <Image source={{ uri: foto }} style={e.foto} />
              ) : (
                <>
                  <Ionicons name="camera" size={40} color={cores.verdeEscuro} />
                  <Text style={[fonte.rotulo, { marginTop: espaco.sm }]}>tirar foto do pacote</Text>
                </>
              )}
            </Pressable>

            {foto ? (
              <Botao titulo="tirar outra" variante="texto" aoTocar={tirarFoto} />
            ) : null}

            <Text style={[fonte.rotulo, { marginTop: espaco.xl, marginBottom: espaco.sm }]}>
              quantos volumes você está levando?
            </Text>
            <View style={e.contador}>
              <Pressable
                onPress={() => setVolumes((v) => Math.max(1, v - 1))}
                style={e.botaoContador}
                accessibilityLabel="menos um volume"
              >
                <Ionicons name="remove" size={24} color={cores.verdeEscuro} />
              </Pressable>
              <Text style={e.numeroVolumes}>{volumes}</Text>
              <Pressable
                onPress={() => setVolumes((v) => Math.min(20, v + 1))}
                style={e.botaoContador}
                accessibilityLabel="mais um volume"
              >
                <Ionicons name="add" size={24} color={cores.verdeEscuro} />
              </Pressable>
            </View>
          </>
        ) : passo === 'entrega' ? (
          <>
            <Text style={[fonte.corpo, { marginBottom: espaco.lg }]}>
              Peça o código de 4 dígitos que aparece no app de quem está recebendo e digite
              aqui.
            </Text>

            <Campo
              rotulo="código de confirmação"
              valor={codigo}
              aoMudar={setCodigo}
              dica="0000"
              teclado="numeric"
              maxLength={6}
            />

            <Pressable onPress={tirarFoto} style={e.areaFotoPequena} accessibilityRole="button">
              {foto ? (
                <Image source={{ uri: foto }} style={e.foto} />
              ) : (
                <>
                  <Ionicons name="camera-outline" size={26} color={cores.verdeEscuro} />
                  <Text style={[fonte.pequeno, { marginTop: 4 }]}>
                    foto da entrega (opcional)
                  </Text>
                </>
              )}
            </Pressable>

            <View style={{ marginTop: espaco.lg }}>
              <Aviso
                texto="ao confirmar, começa a contar o prazo de 7 dias que o comprador tem para pedir devolução."
                tom="informacao"
              />
            </View>
          </>
        ) : (
          <>
            <Text style={[fonte.corpo, { marginBottom: espaco.lg }]}>
              Sem problema. Diga o motivo para a gente entender e chamar outra pessoa.
            </Text>

            {MOTIVOS_DE_RECUSA.map((item) => (
              <Pressable
                key={item}
                onPress={() => setMotivo(item)}
                accessibilityRole="radio"
                accessibilityState={{ selected: motivo === item }}
                style={[
                  e.opcao,
                  motivo === item && { borderColor: cores.verde, backgroundColor: cores.verdeClaro },
                ]}
              >
                <Ionicons
                  name={motivo === item ? 'radio-button-on' : 'radio-button-off'}
                  size={20}
                  color={motivo === item ? cores.verde : cores.textoFraco}
                />
                <Text style={[fonte.corpo, { marginLeft: espaco.md }]}>{item}</Text>
              </Pressable>
            ))}

            <View style={{ marginTop: espaco.lg }}>
              <Campo
                rotulo="quer explicar melhor? (opcional)"
                valor={observacao}
                aoMudar={setObservacao}
                dica="ajuda a gente a melhorar a distribuição"
                multilinha
                maxLength={300}
              />
            </View>
          </>
        )}
      </ScrollView>

      <View style={e.rodape}>
        <Botao
          titulo={
            passo === 'coleta'
              ? 'confirmar coleta'
              : passo === 'entrega'
                ? 'confirmar entrega'
                : 'enviar recusa'
          }
          aoTocar={enviar}
          carregando={enviando}
          desabilitado={!podeEnviar}
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
  areaFoto: {
    height: 200,
    borderRadius: raio.lg,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: cores.verde,
    backgroundColor: cores.verdeClaro,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  areaFotoPequena: {
    height: 110,
    borderRadius: raio.md,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: cores.borda,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  foto: { width: '100%', height: '100%' },
  contador: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: espaco.xl,
  },
  botaoContador: {
    width: 56,
    height: 56,
    borderRadius: 28,
    borderWidth: 1.5,
    borderColor: cores.verdeEscuro,
    alignItems: 'center',
    justifyContent: 'center',
  },
  numeroVolumes: {
    fontSize: 40,
    fontWeight: '700',
    color: cores.texto,
    minWidth: 60,
    textAlign: 'center',
  },
  opcao: {
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
