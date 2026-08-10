/**
 * Criar anúncio.
 *
 * A tela avisa sobre os limites (20 kg e 60 cm) enquanto a pessoa digita, e
 * não deixa publicar fora deles — é melhor barrar aqui do que descobrir na
 * hora da coleta que o entregador não consegue levar.
 */

import React, { useMemo, useState } from 'react';
import {
  Image,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import * as ImagePicker from 'expo-image-picker';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import type { CondicaoProduto } from '../api/tipos';
import { Aviso, Botao, Campo, Selo } from '../componentes/base';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { DIMENSAO_MAXIMA_CM, PESO_MAXIMO_G, validarMedidas } from '../regras/limites';

type Props = NativeStackScreenProps<ParametrosApp, 'NovoAnuncio'>;

const condicoes: { chave: CondicaoProduto; rotulo: string }[] = [
  { chave: 'NOVO', rotulo: 'novo' },
  { chave: 'SEMINOVO', rotulo: 'seminovo' },
  { chave: 'USADO', rotulo: 'usado' },
];

/** Converte "R$ 129,90" ou "129,90" em 12990 centavos. */
function paraCentavos(texto: string): number {
  const digitos = texto.replace(/\D/g, '');
  return digitos ? Number(digitos) : 0;
}

function formatarPreco(centavos: number): string {
  if (!centavos) return '';
  return (centavos / 100).toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  });
}

export function TelaNovoAnuncio({ navigation }: Props) {
  const [fotos, setFotos] = useState<string[]>([]);
  const [titulo, setTitulo] = useState('');
  const [descricao, setDescricao] = useState('');
  const [preco, setPreco] = useState(0);
  const [condicao, setCondicao] = useState<CondicaoProduto>('USADO');
  const [marca, setMarca] = useState('');
  const [pesoKg, setPesoKg] = useState('');
  const [comprimento, setComprimento] = useState('');
  const [largura, setLargura] = useState('');
  const [altura, setAltura] = useState('');
  const [aceitaEntregador, setAceitaEntregador] = useState(true);
  const [aceitaCombinado, setAceitaCombinado] = useState(true);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const medidas = useMemo(
    () => ({
      pesoG: Math.round(Number(pesoKg.replace(',', '.')) * 1000) || 0,
      comprimentoCm: Number(comprimento) || 0,
      larguraCm: Number(largura) || 0,
      alturaCm: Number(altura) || 0,
    }),
    [pesoKg, comprimento, largura, altura],
  );

  // só reclama depois que a pessoa começou a preencher as medidas
  const preencheuMedidas =
    medidas.pesoG > 0 && medidas.comprimentoCm > 0 && medidas.larguraCm > 0 && medidas.alturaCm > 0;
  const validacao = preencheuMedidas ? validarMedidas(medidas) : { valido: true, erros: [] };

  async function escolherFotos() {
    const permissao = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permissao.granted) {
      setErro('Precisamos da sua permissão para acessar as fotos.');
      return;
    }

    const resultado = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsMultipleSelection: true,
      selectionLimit: 10 - fotos.length,
      quality: 0.7,
    });

    if (!resultado.canceled) {
      setFotos((atuais) => [...atuais, ...resultado.assets.map((a) => a.uri)].slice(0, 10));
    }
  }

  const podePublicar =
    fotos.length > 0 &&
    titulo.trim().length >= 4 &&
    descricao.trim().length >= 10 &&
    preco >= 100 &&
    preencheuMedidas &&
    validacao.valido &&
    (aceitaEntregador || aceitaCombinado);

  async function publicar() {
    setErro(null);

    if (MODO_DEMONSTRACAO) {
      setErro('Modo demonstração: configure o servidor para publicar de verdade.');
      return;
    }

    setEnviando(true);
    try {
      // TODO na sua infra: subir as fotos para o storage (S3/Cloudinary) e
      // mandar as URLs. Aqui vão as URIs locais como placeholder.
      await api('/anuncios', {
        metodo: 'POST',
        corpo: {
          titulo: titulo.trim(),
          descricao: descricao.trim(),
          preco,
          condicao,
          marca: marca.trim() || undefined,
          ...medidas,
          aceitaEntregador,
          aceitaCombinado,
          fotos,
        },
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
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>novo anúncio</Text>
      </View>

      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerStyle={{ padding: espaco.lg, paddingBottom: 120 }}>
          {erro ? <Aviso texto={erro} tom="alerta" /> : null}

          <Text style={[fonte.rotulo, { marginBottom: espaco.sm }]}>fotos</Text>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={{ gap: espaco.sm, marginBottom: espaco.lg }}
          >
            <Pressable onPress={escolherFotos} style={e.adicionarFoto} accessibilityRole="button">
              <Ionicons name="camera-outline" size={26} color={cores.verdeEscuro} />
              <Text style={[fonte.pequeno, { marginTop: 4 }]}>adicionar</Text>
            </Pressable>

            {fotos.map((uri, indice) => (
              <View key={uri} style={e.miniatura}>
                <Image source={{ uri }} style={{ width: '100%', height: '100%' }} />
                <Pressable
                  onPress={() => setFotos((atuais) => atuais.filter((_, i) => i !== indice))}
                  style={e.removerFoto}
                  hitSlop={8}
                  accessibilityLabel="remover foto"
                >
                  <Ionicons name="close" size={14} color={cores.branco} />
                </Pressable>
                {indice === 0 ? (
                  <View style={e.capaSelo}>
                    <Text style={{ color: cores.branco, fontSize: 10, fontWeight: '700' }}>capa</Text>
                  </View>
                ) : null}
              </View>
            ))}
          </ScrollView>

          <Campo
            rotulo="o que você está vendendo"
            valor={titulo}
            aoMudar={setTitulo}
            dica="ex.: bicicleta aro 26"
            maxLength={120}
          />

          <Campo
            rotulo="preço"
            valor={formatarPreco(preco)}
            aoMudar={(v) => setPreco(paraCentavos(v))}
            dica="R$ 0,00"
            teclado="numeric"
            ajuda={
              preco >= 100
                ? `você recebe ${formatarPreco(preco - Math.floor(preco * 0.18))} se a gente entregar, ou ${formatarPreco(
                    preco - Math.floor(preco * 0.16),
                  )} se você mesmo entregar`
                : 'preço mínimo: R$ 1,00'
            }
          />

          <Text style={[fonte.rotulo, { marginBottom: espaco.sm }]}>estado do produto</Text>
          <View style={{ flexDirection: 'row', gap: espaco.sm, marginBottom: espaco.lg }}>
            {condicoes.map((c) => (
              <Pressable
                key={c.chave}
                onPress={() => setCondicao(c.chave)}
                accessibilityRole="radio"
                accessibilityState={{ selected: condicao === c.chave }}
                style={[e.opcaoCondicao, condicao === c.chave && e.opcaoCondicaoAtiva]}
              >
                <Text
                  style={{
                    fontWeight: '700',
                    fontSize: 13,
                    color: condicao === c.chave ? cores.branco : cores.textoSuave,
                  }}
                >
                  {c.rotulo}
                </Text>
              </Pressable>
            ))}
          </View>

          <Campo
            rotulo="descrição"
            valor={descricao}
            aoMudar={setDescricao}
            dica="conte o estado, o tempo de uso, se tem marca de uso..."
            multilinha
            maxLength={4000}
          />

          <Campo rotulo="marca (opcional)" valor={marca} aoMudar={setMarca} dica="ex.: caloi" />

          <View style={e.blocoMedidas}>
            <Text style={[fonte.rotulo, { marginBottom: 4 }]}>tamanho e peso do pacote</Text>
            <Text style={[fonte.pequeno, { marginBottom: espaco.lg }]}>
              nossos entregadores levam até {PESO_MAXIMO_G / 1000} kg e {DIMENSAO_MAXIMA_CM} cm em
              cada lado
            </Text>

            <Campo
              rotulo="peso"
              valor={pesoKg}
              aoMudar={setPesoKg}
              dica="0"
              teclado="numeric"
              sufixo="kg"
            />

            <View style={{ flexDirection: 'row', gap: espaco.md }}>
              <View style={{ flex: 1 }}>
                <Campo
                  rotulo="comprimento"
                  valor={comprimento}
                  aoMudar={setComprimento}
                  dica="0"
                  teclado="numeric"
                  sufixo="cm"
                />
              </View>
              <View style={{ flex: 1 }}>
                <Campo
                  rotulo="largura"
                  valor={largura}
                  aoMudar={setLargura}
                  dica="0"
                  teclado="numeric"
                  sufixo="cm"
                />
              </View>
              <View style={{ flex: 1 }}>
                <Campo
                  rotulo="altura"
                  valor={altura}
                  aoMudar={setAltura}
                  dica="0"
                  teclado="numeric"
                  sufixo="cm"
                />
              </View>
            </View>

            {!validacao.valido ? (
              <Aviso texto={validacao.erros.join(' ')} tom="alerta" />
            ) : preencheuMedidas ? (
              <Aviso texto="dentro do limite. nossos entregadores conseguem levar." tom="sucesso" />
            ) : null}
          </View>

          <Text style={[fonte.rotulo, { marginTop: espaco.xl, marginBottom: espaco.sm }]}>
            como o comprador recebe
          </Text>
          <Pressable
            onPress={() => setAceitaEntregador((v) => !v)}
            style={e.escolha}
            accessibilityRole="checkbox"
            accessibilityState={{ checked: aceitaEntregador }}
          >
            <Ionicons
              name={aceitaEntregador ? 'checkbox' : 'square-outline'}
              size={22}
              color={aceitaEntregador ? cores.verde : cores.textoFraco}
            />
            <View style={{ flex: 1, marginLeft: espaco.md }}>
              <Text style={fonte.corpo}>entregador do vendas itinga</Text>
              <Text style={fonte.pequeno}>comissão de 18% · a gente busca e entrega</Text>
            </View>
          </Pressable>

          <Pressable
            onPress={() => setAceitaCombinado((v) => !v)}
            style={e.escolha}
            accessibilityRole="checkbox"
            accessibilityState={{ checked: aceitaCombinado }}
          >
            <Ionicons
              name={aceitaCombinado ? 'checkbox' : 'square-outline'}
              size={22}
              color={aceitaCombinado ? cores.verde : cores.textoFraco}
            />
            <View style={{ flex: 1, marginLeft: espaco.md }}>
              <Text style={fonte.corpo}>combinar com o comprador</Text>
              <Text style={fonte.pequeno}>comissão de 16% · vocês combinam a entrega</Text>
            </View>
          </Pressable>
        </ScrollView>
      </KeyboardAvoidingView>

      <View style={e.rodape}>
        <Botao
          titulo="publicar anúncio"
          aoTocar={publicar}
          carregando={enviando}
          desabilitado={!podePublicar}
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
  adicionarFoto: {
    width: 88,
    height: 88,
    borderRadius: raio.md,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: cores.verde,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: cores.verdeClaro,
  },
  miniatura: {
    width: 88,
    height: 88,
    borderRadius: raio.md,
    overflow: 'hidden',
    backgroundColor: cores.fundoCinza,
  },
  removerFoto: {
    position: 'absolute',
    top: 4,
    right: 4,
    backgroundColor: 'rgba(0,0,0,0.6)',
    borderRadius: 10,
    padding: 3,
  },
  capaSelo: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: cores.verde,
    alignItems: 'center',
    paddingVertical: 2,
  },
  opcaoCondicao: {
    flex: 1,
    paddingVertical: espaco.md,
    borderRadius: raio.md,
    backgroundColor: cores.fundoCinza,
    alignItems: 'center',
  },
  opcaoCondicaoAtiva: { backgroundColor: cores.verde },
  blocoMedidas: {
    backgroundColor: cores.fundoCinza,
    borderRadius: raio.lg,
    padding: espaco.lg,
    marginTop: espaco.sm,
  },
  escolha: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: espaco.md,
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
